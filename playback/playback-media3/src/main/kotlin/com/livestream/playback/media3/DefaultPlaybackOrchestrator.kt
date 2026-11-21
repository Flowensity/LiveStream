package com.livestream.playback.media3

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.common.network.NetworkMonitor
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackErrorType
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.SourceResolution
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.playback.api.OrchestratorState
import com.livestream.playback.api.PlaybackEngine
import com.livestream.playback.api.PlaybackOrchestrator
import com.livestream.playback.api.SwitchDirection
import com.livestream.playback.api.model.EngineState
import com.livestream.playback.api.model.PlaybackMediaItem
import com.livestream.playback.api.monitor.LatencyTracker
import com.livestream.playback.api.monitor.SwitchRecord
import com.livestream.playback.api.web.WebPlaybackController
import com.livestream.playback.api.web.WebPlaybackState
import com.livestream.playback.media3.watchdog.PlaybackWatchdog
import com.livestream.source.api.SourceAdapterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlaybackOrchestrator @Inject constructor(
    private val engine: PlaybackEngine,
    private val channelRepository: ChannelRepository,
    private val sourceAdapterRegistry: SourceAdapterRegistry,
    override val webPlaybackController: WebPlaybackController,
    private val networkMonitor: NetworkMonitor,
    override val latencyTracker: LatencyTracker,
    private val dispatchers: AppDispatchers,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : PlaybackOrchestrator {

    private val orchestratorScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val _orchestratorState = MutableStateFlow(OrchestratorState())
    override val orchestratorState: StateFlow<OrchestratorState> = _orchestratorState.asStateFlow()

    // 活跃请求会话序列号，解决快速切台时的异步竞争
    private var activeSessionToken = 0L
    private var activePlayJob: Job? = null
    private var activeProbeJob: Job? = null
    private var activePrewarmJob: Job? = null

    // 时延与性能统计分段打点
    private var switchStartTimeMs = 0L
    private var networkResolutionMs = 0L
    private var enginePrepareStartTimeMs = 0L

    // 当前频道可用的源列表与索引
    private var availableSources: List<LiveSource> = emptyList()
    private var currentSourceIndex: Int = 0
    private var currentRetryCount: Int = 0
    private val attemptedSourceIdsInSession = mutableSetOf<String>()
    private val sourceLatencyCache = createBoundedLruMap<String, Long>(MAX_SOURCE_METRIC_CACHE_SIZE)
    private val sourceRealResolutionCache = createBoundedLruMap<String, Pair<Int, Int>>(MAX_SOURCE_METRIC_CACHE_SIZE)
    private val prewarmedSourcesCache = createBoundedLruMap<String, List<LiveSource>>(MAX_PREWARMED_CACHE_SIZE)

    private fun <K, V> createBoundedLruMap(maxSize: Int): MutableMap<K, V> {
        return java.util.Collections.synchronizedMap(
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                    return size > maxSize
                }
            }
        )
    }

    private fun <K, V> Map<K, V>.snapshot(): Map<K, V> = synchronized(this) { HashMap(this) }

    internal fun prewarmedCacheSizeForTest(): Int = synchronized(prewarmedSourcesCache) { prewarmedSourcesCache.size }

    // 缓冲卡死看门狗（多源时超时自动换线，单源时不打断正在进行的缓冲/网页加载）
    internal val watchdog = PlaybackWatchdog(dispatchers) {
        val currentMetrics = _orchestratorState.value.metrics
        _orchestratorState.value = _orchestratorState.value.copy(
            metrics = currentMetrics.copy(stallCount = currentMetrics.stallCount + 1)
        )
        if (availableSources.size > 1) {
            switchNextSource()
        }
    }

    companion object {
        const val MAX_RETRY_COUNT = 3
        const val MAX_CONCURRENT_PROBES = 3
        const val PREWARM_STABILIZE_DELAY_MS = 1200L
        const val MAX_PREWARMED_CACHE_SIZE = 16
        const val MAX_SOURCE_METRIC_CACHE_SIZE = 256

        internal fun calculateQualityScore(
            source: LiveSource,
            latencyMs: Long?,
            realResolution: Pair<Int, Int>?
        ): Int {
            // 真实物理分辨率评分：严禁轻信字符串命名，依据探针技术协议或解码器真实物理像素
            val resBonus = when {
                realResolution == null -> 0
                realResolution.first >= 3840 || realResolution.second >= 2160 -> 400
                realResolution.first >= 1920 || realResolution.second >= 1080 -> 300
                realResolution.first >= 1280 || realResolution.second >= 720 -> 200
                realResolution.first > 0 || realResolution.second > 0 -> 100
                else -> 0
            }
            val baseScore = 200 + resBonus + source.priority

            // 测速时延惩罚：极速加载优先，过大延迟（>1500ms）降权避免卡顿
            val latencyPenalty = when {
                latencyMs == null -> 0
                latencyMs <= 500L -> 0
                latencyMs <= 1500L -> 40
                latencyMs <= 3000L -> 160
                else -> 400
            }
            return baseScore - latencyPenalty
        }
    }

    private val statusFlow = _orchestratorState
        .map { it.status }
        .stateIn(orchestratorScope, SharingStarted.Eagerly, PlaybackStatus.IDLE)

    init {
        // 启动卡顿看门狗监听
        watchdog.start(statusFlow)

        // 监听并动态同步用户设置的换台/缓冲卡死超时时间与软硬解偏好
        userPreferencesRepository?.let { repo ->
            orchestratorScope.launch {
                repo.userPreferences
                    .map { it.channelSwitchTimeoutMs }
                    .distinctUntilChanged()
                    .collect { timeoutMs ->
                        watchdog.updateStallTimeout(timeoutMs)
                    }
            }
            orchestratorScope.launch {
                repo.userPreferences
                    .map { it.isHardwareDecodePreferred }
                    .distinctUntilChanged()
                    .collect { hwPreferred ->
                        engine.setHardwareDecodePreferred(hwPreferred)
                    }
            }
        }

        // 监听网络连通性自愈事件：从断网恢复连网时，自动尝试重连恢复播放
        orchestratorScope.launch {
            var prevConnected = networkMonitor.isConnected.value
            networkMonitor.isConnected.collect { connected ->
                if (!prevConnected && connected) {
                    val curState = _orchestratorState.value
                    if (curState.status == PlaybackStatus.ERROR &&
                        (curState.errorInfo?.errorType == PlaybackErrorType.NETWORK_TIMEOUT ||
                         curState.errorInfo?.errorType == PlaybackErrorType.NETWORK_DISCONNECTED)) {
                        retry()
                    }
                }
                prevConnected = connected
            }
        }

        // 监听原生引擎状态并映射到调度器聚合状态
        orchestratorScope.launch {
            engine.engineState.collect { engineState ->
                handleEngineState(engineState)
            }
        }

        // 监听官方网页播放状态并映射到调度器聚合状态
        orchestratorScope.launch {
            webPlaybackController.webState.collect { webState ->
                if (!_orchestratorState.value.isOfficialWeb) return@collect
                when (webState) {
                    is WebPlaybackState.Idle -> { }
                    is WebPlaybackState.Loading -> {
                        _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.BUFFERING)
                    }
                    is WebPlaybackState.Ready, is WebPlaybackState.Playing -> {
                        recordPlayingSuccess()
                    }
                    is WebPlaybackState.Error -> {
                        handlePlaybackFailure(
                            PlaybackErrorInfo(
                                errorType = PlaybackErrorType.NETWORK_TIMEOUT,
                                message = webState.description,
                                canRetry = true
                            )
                        )
                    }
                    is WebPlaybackState.RendererCrashed -> {
                        handlePlaybackFailure(
                            PlaybackErrorInfo(
                                errorType = PlaybackErrorType.RENDERER_CRASHED,
                                message = "画面渲染进程异常，正在自动恢复",
                                canRetry = true
                            )
                        )
                    }
                    is WebPlaybackState.Destroyed -> { }
                }
            }
        }

        // 监听实时性能指标
        orchestratorScope.launch {
            engine.playbackMetrics.collect { metrics ->
                val currentSrc = _orchestratorState.value.currentSource
                if (currentSrc != null && metrics.videoWidth > 0 && metrics.videoHeight > 0) {
                    val prev = sourceRealResolutionCache[currentSrc.id]
                    if (prev == null || prev.first != metrics.videoWidth || prev.second != metrics.videoHeight) {
                        sourceRealResolutionCache[currentSrc.id] = metrics.videoWidth to metrics.videoHeight
                        availableSources = sortSourcesByHealthAndLatency(availableSources)
                        val activeIdx = availableSources.indexOfFirst { it.id == currentSrc.id }
                        if (activeIdx >= 0) currentSourceIndex = activeIdx
                    }
                }

                _orchestratorState.value = _orchestratorState.value.copy(
                    availableSources = availableSources,
                    currentSourceIndex = currentSourceIndex,
                    sourceResolutions = sourceRealResolutionCache.snapshot(),
                    metrics = _orchestratorState.value.metrics.copy(
                        bitrateBps = metrics.bitrateBps,
                        videoWidth = metrics.videoWidth,
                        videoHeight = metrics.videoHeight,
                        frameRate = metrics.frameRate,
                        droppedFrames = metrics.droppedFrames,
                        liveLatencyMs = metrics.liveLatencyMs,
                        bufferedDurationMs = metrics.bufferedDurationMs,
                        decoderName = metrics.decoderName,
                        isHardwareAccelerated = metrics.isHardwareAccelerated
                    )
                )
            }
        }
    }

    override fun playChannel(channel: Channel, preferredSourceId: String?) {
        val currentToken = ++activeSessionToken

        // 1. 协同取消旧播放与解析任务，杜绝旧任务回调污染新状态
        activePlayJob?.cancel()
        activeProbeJob?.cancel()
        activePrewarmJob?.cancel()
        currentRetryCount = 0
        attemptedSourceIdsInSession.clear()
        watchdog.start(statusFlow)

        // 记录切台起始时间点
        switchStartTimeMs = System.currentTimeMillis()
        networkResolutionMs = 0L
        enginePrepareStartTimeMs = 0L

        // 保持上一频道的 isOfficialWeb 容器形态直至新源解析完毕，避免切台瞬间 SurfaceView 与 WebView 反复拆装
        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.SWITCHING,
            currentChannel = channel,
            currentSource = null,
            errorInfo = null
        )

        activePlayJob = orchestratorScope.launch {
            _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.RESOLVING)

            // 2. 优先命中邻台预热或预览缓存，免除数据库查询等待
            val cachedSources = prewarmedSourcesCache.remove(channel.id)
            val sources = if (!cachedSources.isNullOrEmpty()) {
                cachedSources
            } else {
                channelRepository.getSourcesForChannelSync(channel.id)
            }
            if (currentToken != activeSessionToken) return@launch // 过期请求立即退出

            if (sources.isEmpty()) {
                val error = PlaybackErrorInfo(
                    errorType = PlaybackErrorType.SOURCE_UNAVAILABLE,
                    message = "当前频道暂无可用线路",
                    canRetry = false
                )
                _orchestratorState.value = _orchestratorState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorInfo = error,
                    isOfficialWeb = false,
                    officialWebUrl = null
                )
                return@launch
            }

            availableSources = sortSourcesByHealthAndLatency(sources)
            currentSourceIndex = if (preferredSourceId != null) {
                availableSources.indexOfFirst { it.id == preferredSourceId }.coerceAtLeast(0)
            } else {
                0
            }

            if (availableSources.size > 1) {
                probeCandidateSources(currentToken, channel, availableSources)
            }

            executeSourcePlayback(currentToken)
        }
    }

    private fun sortSourcesByHealthAndLatency(sources: List<LiveSource>): List<LiveSource> {
        return sources.sortedWith(
            compareByDescending<LiveSource> { src ->
                // 第一梯队：健康或未知可用状态 (2)，降级状态次之 (1)，不可用垫底 (0)
                when (src.healthStatus) {
                    com.livestream.core.model.SourceHealthStatus.HEALTHY,
                    com.livestream.core.model.SourceHealthStatus.UNKNOWN -> 2
                    com.livestream.core.model.SourceHealthStatus.DEGRADED -> 1
                    com.livestream.core.model.SourceHealthStatus.UNAVAILABLE -> 0
                }
            }.thenByDescending { src ->
                // 在可用梯队内，优先使用 ExoPlayer 原生硬解流 (HLS/DASH/HTTP)，将网页容器作为保底
                if (src.protocol != com.livestream.core.model.StreamProtocol.OFFICIAL_WEB) 1 else 0
            }.thenByDescending { src ->
                when (src.healthStatus) {
                    com.livestream.core.model.SourceHealthStatus.HEALTHY -> 3
                    com.livestream.core.model.SourceHealthStatus.UNKNOWN -> 2
                    com.livestream.core.model.SourceHealthStatus.DEGRADED -> 1
                    com.livestream.core.model.SourceHealthStatus.UNAVAILABLE -> 0
                }
            }.thenByDescending { src ->
                // 综合真实物理分辨率（杜绝假画质文本虚标）与测速加载速度（网络时延）动态优选
                val latency = sourceLatencyCache[src.id]
                val realRes = sourceRealResolutionCache[src.id]
                calculateQualityScore(src, latency, realRes)
            }.thenBy { src ->
                sourceLatencyCache[src.id] ?: Long.MAX_VALUE
            }.thenByDescending { src ->
                src.priority
            }
        )
    }

    private fun probeCandidateSources(
        sessionToken: Long,
        channel: Channel,
        sources: List<LiveSource>
    ) {
        activeProbeJob?.cancel()
        activeProbeJob = orchestratorScope.launch(dispatchers.io) {
            // 错峰 50ms 启动后台探测，让主播放源优先独占带宽与连接池完成首帧握手
            delay(50L)
            if (sessionToken != activeSessionToken) return@launch
            val probeSemaphore = Semaphore(MAX_CONCURRENT_PROBES)
            val healthUpdates = java.util.concurrent.ConcurrentHashMap<String, com.livestream.core.model.SourceHealthStatus>()

            coroutineScope {
                sources.map { src ->
                    async {
                        probeSemaphore.withPermit {
                            if (sessionToken != activeSessionToken) return@withPermit
                            val adapter = sourceAdapterRegistry.getAdapter(src.adapterProviderId) ?: return@withPermit
                            runCatching {
                                when (val result = adapter.checkAvailability(channel, src)) {
                                    is com.livestream.source.api.AvailabilityResult.Available -> {
                                        sourceLatencyCache[src.id] = result.latencyMs
                                        val vw = result.videoWidth
                                        val vh = result.videoHeight
                                        if (vw != null && vh != null && vw > 0 && vh > 0) {
                                            sourceRealResolutionCache[src.id] = vw to vh
                                        }
                                        healthUpdates[src.id] = com.livestream.core.model.SourceHealthStatus.HEALTHY
                                        channelRepository.updateSourceHealth(
                                            src.id,
                                            com.livestream.core.model.SourceHealthStatus.HEALTHY
                                        )
                                    }
                                    is com.livestream.source.api.AvailabilityResult.Degraded -> {
                                        sourceLatencyCache[src.id] = result.latencyMs
                                        val vw = result.videoWidth
                                        val vh = result.videoHeight
                                        if (vw != null && vh != null && vw > 0 && vh > 0) {
                                            sourceRealResolutionCache[src.id] = vw to vh
                                        }
                                        healthUpdates[src.id] = com.livestream.core.model.SourceHealthStatus.DEGRADED
                                        channelRepository.updateSourceHealth(
                                            src.id,
                                            com.livestream.core.model.SourceHealthStatus.DEGRADED
                                        )
                                    }
                                    is com.livestream.source.api.AvailabilityResult.Unavailable -> {
                                        healthUpdates[src.id] = com.livestream.core.model.SourceHealthStatus.UNAVAILABLE
                                        channelRepository.updateSourceHealth(
                                            src.id,
                                            com.livestream.core.model.SourceHealthStatus.UNAVAILABLE
                                        )
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            withContext(dispatchers.main) {
                if (sessionToken == activeSessionToken) {
                    val updatedList = availableSources.map { src ->
                        val newHealth = healthUpdates[src.id]
                        if (newHealth != null) src.copy(healthStatus = newHealth) else src
                    }
                    availableSources = sortSourcesByHealthAndLatency(updatedList)
                    val activeSrcId = _orchestratorState.value.currentSource?.id
                    if (activeSrcId != null) {
                        val idx = availableSources.indexOfFirst { it.id == activeSrcId }
                        if (idx >= 0) currentSourceIndex = idx
                    }
                    _orchestratorState.value = _orchestratorState.value.copy(
                        availableSources = availableSources,
                        currentSourceIndex = currentSourceIndex,
                        sourceLatenciesMs = sourceLatencyCache.snapshot(),
                        sourceResolutions = sourceRealResolutionCache.snapshot()
                    )
                }
            }
        }
    }

    private suspend fun executeSourcePlayback(sessionToken: Long) {
        if (sessionToken != activeSessionToken) return

        val source = availableSources.getOrNull(currentSourceIndex)
        if (source == null) {
            val error = PlaybackErrorInfo(
                errorType = PlaybackErrorType.SOURCE_UNAVAILABLE,
                message = "当前频道所有线路均无法连接，请稍后重试",
                canRetry = false
            )
            _orchestratorState.value = _orchestratorState.value.copy(
                status = PlaybackStatus.ERROR,
                errorInfo = error
            )
            return
        }

        val channel = _orchestratorState.value.currentChannel ?: return
        attemptedSourceIdsInSession.add(source.id)

        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.RESOLVING,
            currentSource = source,
            currentSourceIndex = currentSourceIndex,
            totalSources = availableSources.size,
            availableSources = availableSources,
            sourceLatenciesMs = sourceLatencyCache.snapshot(),
            errorInfo = null
        )

        try {
            // 通过 SourceAdapter 插件解析真实媒体流
            val adapter = sourceAdapterRegistry.getAdapter(source.adapterProviderId)
            val resolution = adapter?.resolve(channel, source)
                ?: SourceResolution.Unavailable("未找到对应直播源适配器 (${source.adapterProviderId})")

            if (sessionToken != activeSessionToken) return

            val now = System.currentTimeMillis()
            networkResolutionMs = if (switchStartTimeMs > 0L) (now - switchStartTimeMs).coerceAtLeast(0L) else 0L
            enginePrepareStartTimeMs = now

            when (resolution) {
                is SourceResolution.NativeMedia -> {
                    // 停止并清理任何残留的 Web 容器
                    webPlaybackController.stopAndDestroy()

                    _orchestratorState.value = _orchestratorState.value.copy(
                        status = PlaybackStatus.PREPARING,
                        currentSource = source,
                        errorInfo = null,
                        isOfficialWeb = false,
                        officialWebUrl = null
                    )

                    val resolvedUserAgent = resolution.userAgent ?: source.userAgent
                    val resolvedReferer = resolution.referer ?: source.referer
                    val customHeaders = buildMap {
                        if (!resolvedReferer.isNullOrBlank()) put("Referer", resolvedReferer)
                        if (!resolvedUserAgent.isNullOrBlank()) put("User-Agent", resolvedUserAgent)
                    }
                    val mediaItem = PlaybackMediaItem(
                        uri = resolution.uri,
                        protocol = source.protocol,
                        title = "${channel.name} - ${source.name}",
                        channelId = channel.id,
                        sourceId = source.id,
                        headersProfileId = resolution.headersProfileId ?: source.headersProfileId,
                        drmProfileId = resolution.drmProfileId ?: source.drmProfileId,
                        customHeaders = customHeaders,
                        userAgent = resolvedUserAgent
                    )

                    engine.prepare(mediaItem, playWhenReady = true)
                }
                is SourceResolution.OfficialWeb -> {
                    // 原生播放引擎停止，转入官方合规网页模式
                    engine.stop()

                    val isSameWebTarget = _orchestratorState.value.isOfficialWeb &&
                        _orchestratorState.value.officialWebUrl == resolution.uri

                    _orchestratorState.value = _orchestratorState.value.copy(
                        status = PlaybackStatus.PREPARING,
                        currentSource = source,
                        errorInfo = null,
                        isOfficialWeb = true,
                        officialWebUrl = resolution.uri
                    )

                    if (isSameWebTarget) {
                        webPlaybackController.reload()
                    } else {
                        webPlaybackController.loadOfficialUrl(resolution.uri, null)
                    }
                }
                is SourceResolution.Unavailable -> {
                    val error = PlaybackErrorInfo(
                        errorType = PlaybackErrorType.SOURCE_UNAVAILABLE,
                        message = resolution.reason,
                        canRetry = true
                    )
                    handlePlaybackFailure(error)
                }
            }
        } catch (e: Throwable) {
            if (sessionToken != activeSessionToken) return
            val error = PlaybackErrorInfo(
                errorType = PlaybackErrorType.UNKNOWN,
                message = "线路启动失败：${e.message}",
                canRetry = true
            )
            handlePlaybackFailure(error)
        }
    }

    private fun handleEngineState(engineState: EngineState) {
        // 若当前处于官方网页播放模式，原生引擎回调不覆盖状态
        if (_orchestratorState.value.isOfficialWeb) return

        // 当前为 SWITCHING 或 RESOLVING 时不被旧引擎状态提前覆盖
        val currentStatus = _orchestratorState.value.status
        if (currentStatus == PlaybackStatus.SWITCHING || currentStatus == PlaybackStatus.RESOLVING) {
            return
        }

        when (engineState) {
            is EngineState.Idle -> {
                if (currentStatus != PlaybackStatus.ERROR && currentStatus != PlaybackStatus.RELEASED) {
                    _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.IDLE)
                }
            }
            is EngineState.Preparing -> {
                _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.PREPARING)
            }
            is EngineState.Buffering -> {
                _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.BUFFERING)
            }
            is EngineState.Ready -> {
                _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.READY)
            }
            is EngineState.Playing -> {
                recordPlayingSuccess()
            }
            is EngineState.Paused -> {
                _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.PAUSED)
            }
            is EngineState.Ended -> {
                _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.IDLE)
            }
            is EngineState.Error -> {
                handlePlaybackFailure(engineState.errorInfo)
            }
        }
    }

    private fun handlePlaybackFailure(errorInfo: PlaybackErrorInfo) {
        val currentToken = activeSessionToken
        val failedSource = availableSources.getOrNull(currentSourceIndex)
        if (failedSource != null) {
            orchestratorScope.launch(dispatchers.io) {
                runCatching {
                    channelRepository.updateSourceHealth(
                        failedSource.id,
                        com.livestream.core.model.SourceHealthStatus.DEGRADED
                    )
                }
            }
        }

        // 尝试源回退策略：从当前索引之后循环查找本轮尚未尝试过的备选源
        val total = availableSources.size
        val nextFallbackIndex = if (total > 1) {
            (1 until total)
                .map { offset -> (currentSourceIndex + offset) % total }
                .firstOrNull { idx -> availableSources[idx].id !in attemptedSourceIdsInSession }
        } else {
            null
        }
        if (nextFallbackIndex != null) {
            currentSourceIndex = nextFallbackIndex
            activePlayJob?.cancel()
            activePlayJob = orchestratorScope.launch {
                delay(100) // 调优：由 300ms 降至 100ms 快速换线切换备用源
                executeSourcePlayback(currentToken)
            }
            return
        }

        // 有限次数重试（最多3次），禁止无限死循环重试
        if (currentRetryCount < MAX_RETRY_COUNT && errorInfo.canRetry) {
            currentRetryCount++
            activePlayJob?.cancel()
            activePlayJob = orchestratorScope.launch {
                delay((currentRetryCount * 500L).coerceAtMost(2000L))
                executeSourcePlayback(currentToken)
            }
            return
        }

        // 记录切台失败指标
        if (switchStartTimeMs > 0L) {
            val now = System.currentTimeMillis()
            val channel = _orchestratorState.value.currentChannel
            val source = _orchestratorState.value.currentSource
            if (channel != null) {
                latencyTracker.recordSwitch(
                    SwitchRecord(
                        channelId = channel.id,
                        sourceId = source?.id ?: "",
                        isSuccess = false,
                        totalDurationMs = (now - switchStartTimeMs).coerceAtLeast(0L),
                        networkResolutionMs = networkResolutionMs,
                        timestampMs = now
                    )
                )
            }
            switchStartTimeMs = 0L
        }

        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.ERROR,
            errorInfo = errorInfo.copy(retryCount = currentRetryCount)
        )
    }

    private fun recordPlayingSuccess() {
        val now = System.currentTimeMillis()
        if (switchStartTimeMs > 0L) {
            val totalDuration = (now - switchStartTimeMs).coerceAtLeast(0L)
            val decodeDuration = if (enginePrepareStartTimeMs > 0L) (now - enginePrepareStartTimeMs).coerceAtLeast(0L) else 0L

            val updatedMetrics = _orchestratorState.value.metrics.copy(
                switchDurationMs = totalDuration,
                networkResolutionMs = networkResolutionMs,
                enginePrepareMs = if (enginePrepareStartTimeMs > 0L) (enginePrepareStartTimeMs - switchStartTimeMs).coerceAtLeast(0L) else 0L,
                decodeToFirstFrameMs = decodeDuration,
                firstFrameRenderedTimestampMs = now
            )
            _orchestratorState.value = _orchestratorState.value.copy(
                status = PlaybackStatus.PLAYING,
                metrics = updatedMetrics
            )

            val channel = _orchestratorState.value.currentChannel
            val source = _orchestratorState.value.currentSource
            if (channel != null && source != null) {
                sourceLatencyCache[source.id] = totalDuration
                orchestratorScope.launch(dispatchers.io) {
                    runCatching {
                        channelRepository.updateSourceHealth(
                            source.id,
                            com.livestream.core.model.SourceHealthStatus.HEALTHY
                        )
                    }
                    runCatching {
                        channelRepository.recordPlaybackHistory(channel.id, source.id)
                    }
                }
                _orchestratorState.value = _orchestratorState.value.copy(
                    sourceLatenciesMs = sourceLatencyCache.snapshot()
                )
                latencyTracker.recordSwitch(
                    SwitchRecord(
                        channelId = channel.id,
                        sourceId = source.id,
                        isSuccess = true,
                        totalDurationMs = totalDuration,
                        networkResolutionMs = networkResolutionMs,
                        enginePrepareMs = if (enginePrepareStartTimeMs > 0L) (enginePrepareStartTimeMs - switchStartTimeMs).coerceAtLeast(0L) else 0L,
                        decodeToFirstFrameMs = decodeDuration,
                        timestampMs = now
                    )
                )
            }
            switchStartTimeMs = 0L
        } else {
            _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.PLAYING)
        }
        scheduleAdjacentChannelPrewarm(
            currentChannel = _orchestratorState.value.currentChannel,
            sessionToken = activeSessionToken
        )
    }

    internal fun isChannelPrewarmed(channelId: String): Boolean {
        return prewarmedSourcesCache.containsKey(channelId)
    }

    private fun scheduleAdjacentChannelPrewarm(currentChannel: Channel?, sessionToken: Long) {
        if (currentChannel == null) return
        activePrewarmJob?.cancel()
        activePrewarmJob = orchestratorScope.launch(dispatchers.io) {
            delay(PREWARM_STABILIZE_DELAY_MS)
            if (sessionToken != activeSessionToken || _orchestratorState.value.status != PlaybackStatus.PLAYING) {
                return@launch
            }
            val visibleChannels = channelRepository.observeAllVisibleChannels().firstOrNull().orEmpty()
            if (visibleChannels.size <= 1) return@launch

            val idx = visibleChannels.indexOfFirst { it.id == currentChannel.id }
            if (idx == -1) return@launch

            val nextIdx = if (idx == visibleChannels.size - 1) 0 else idx + 1
            val prevIdx = if (idx == 0) visibleChannels.size - 1 else idx - 1
            val adjacentChannels = listOf(visibleChannels[nextIdx], visibleChannels[prevIdx])
                .distinctBy { it.id }
                .filter { it.id != currentChannel.id }

            for (adjChannel in adjacentChannels) {
                if (sessionToken != activeSessionToken) return@launch
                prewarmSingleChannel(adjChannel, sessionToken)
            }
        }
    }

    private suspend fun prewarmSingleChannel(targetChannel: Channel, sessionToken: Long) {
        runCatching {
            val sources = channelRepository.getSourcesForChannelSync(targetChannel.id)
            if (sources.isEmpty() || sessionToken != activeSessionToken) return
            val sorted = sortSourcesByHealthAndLatency(sources)
            prewarmedSourcesCache[targetChannel.id] = sorted

            // 预热首选源的 DNS 缓存与 TCP/TLS 连接池，并提前淘汰死源（OFFICIAL_WEB 走 Chromium 栈与页内 SPA 复用，无需 OkHttp 重复探测）
            val primarySource = sorted.firstOrNull() ?: return
            if (primarySource.protocol == com.livestream.core.model.StreamProtocol.OFFICIAL_WEB) return
            val adapter = sourceAdapterRegistry.getAdapter(primarySource.adapterProviderId) ?: return
            val newHealth = when (val result = adapter.checkAvailability(targetChannel, primarySource)) {
                is com.livestream.source.api.AvailabilityResult.Available -> {
                    sourceLatencyCache[primarySource.id] = result.latencyMs
                    channelRepository.updateSourceHealth(
                        primarySource.id,
                        com.livestream.core.model.SourceHealthStatus.HEALTHY
                    )
                    com.livestream.core.model.SourceHealthStatus.HEALTHY
                }
                is com.livestream.source.api.AvailabilityResult.Degraded -> {
                    sourceLatencyCache[primarySource.id] = result.latencyMs
                    channelRepository.updateSourceHealth(
                        primarySource.id,
                        com.livestream.core.model.SourceHealthStatus.DEGRADED
                    )
                    com.livestream.core.model.SourceHealthStatus.DEGRADED
                }
                is com.livestream.source.api.AvailabilityResult.Unavailable -> {
                    channelRepository.updateSourceHealth(
                        primarySource.id,
                        com.livestream.core.model.SourceHealthStatus.UNAVAILABLE
                    )
                    com.livestream.core.model.SourceHealthStatus.UNAVAILABLE
                }
            }
            val updated = sorted.map { src ->
                if (src.id == primarySource.id) src.copy(healthStatus = newHealth) else src
            }
            prewarmedSourcesCache[targetChannel.id] = sortSourcesByHealthAndLatency(updated)
        }
    }

    override fun previewChannel(channel: Channel) {
        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.SWITCHING,
            currentChannel = channel,
            currentSource = null,
            errorInfo = null
        )
        val token = activeSessionToken
        activePrewarmJob?.cancel()
        activePrewarmJob = orchestratorScope.launch(dispatchers.io) {
            prewarmSingleChannel(channel, token)
        }
    }

    override fun switchChannel(direction: SwitchDirection) {
        orchestratorScope.launch {
            val channels = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
            if (channels.isEmpty()) return@launch

            val currentId = _orchestratorState.value.currentChannel?.id
            val currentIndex = if (currentId != null) {
                channels.indexOfFirst { it.id == currentId }
            } else {
                -1
            }

            val targetIndex = when (direction) {
                SwitchDirection.NEXT -> {
                    if (currentIndex == -1 || currentIndex == channels.size - 1) 0 else currentIndex + 1
                }
                SwitchDirection.PREVIOUS -> {
                    if (currentIndex <= 0) channels.size - 1 else currentIndex - 1
                }
            }

            playChannel(channels[targetIndex])
        }
    }

    override val watchdogStallTimeoutMs: Long get() = watchdog.stallTimeoutMs

    override fun switchNextSource() {
        if (availableSources.isEmpty()) return
        if (availableSources.size == 1) {
            retry()
            return
        }
        val currentToken = activeSessionToken
        val nextIndex = currentSourceIndex + 1
        if (nextIndex >= availableSources.size && _orchestratorState.value.status != PlaybackStatus.PLAYING) {
            // 所有备用源均已轮询尝试完毕且未成功起播，判定为网络超时，转入 ERROR 状态以激活网络自愈监听
            currentRetryCount = MAX_RETRY_COUNT
            handlePlaybackFailure(
                PlaybackErrorInfo(
                    errorType = PlaybackErrorType.NETWORK_TIMEOUT,
                    message = "当前频道所有线路均连接超时，请检查网络设置",
                    canRetry = true
                )
            )
            return
        }
        currentSourceIndex = nextIndex % availableSources.size
        activePlayJob?.cancel()
        activePlayJob = orchestratorScope.launch {
            executeSourcePlayback(currentToken)
        }
    }

    override fun selectSource(sourceIndex: Int) {
        if (sourceIndex !in availableSources.indices) return
        if (sourceIndex == currentSourceIndex) {
            retry()
            return
        }
        val currentToken = activeSessionToken
        currentSourceIndex = sourceIndex
        currentRetryCount = 0
        attemptedSourceIdsInSession.clear()
        activePlayJob?.cancel()
        activePlayJob = orchestratorScope.launch {
            executeSourcePlayback(currentToken)
        }
    }

    override fun retry() {
        val currentToken = activeSessionToken
        currentRetryCount = 0
        attemptedSourceIdsInSession.clear()
        activePlayJob?.cancel()
        activePlayJob = orchestratorScope.launch {
            executeSourcePlayback(currentToken)
        }
    }

    override fun pause() {
        watchdog.stop()
        if (_orchestratorState.value.isOfficialWeb) {
            webPlaybackController.pause()
        } else {
            engine.pause()
        }
    }

    override fun resume() {
        if (_orchestratorState.value.isOfficialWeb) {
            webPlaybackController.resume()
        } else {
            engine.play()
        }
        watchdog.start(statusFlow)
    }

    override fun stop() {
        activeSessionToken++
        activePlayJob?.cancel()
        activeProbeJob?.cancel()
        activePrewarmJob?.cancel()
        attemptedSourceIdsInSession.clear()
        switchStartTimeMs = 0L
        watchdog.stop()
        engine.stop()
        webPlaybackController.stopAndDestroy()
        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.IDLE,
            isOfficialWeb = false,
            officialWebUrl = null
        )
    }

    override fun release() {
        activeSessionToken++
        activePlayJob?.cancel()
        activeProbeJob?.cancel()
        activePrewarmJob?.cancel()
        attemptedSourceIdsInSession.clear()
        switchStartTimeMs = 0L
        watchdog.release()
        engine.release()
        webPlaybackController.stopAndDestroy()
        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.RELEASED,
            isOfficialWeb = false,
            officialWebUrl = null
        )
    }

    override fun attachOutput(output: Any?) {
        engine.attachSurface(output)
    }

    override fun detachOutput() {
        engine.detachSurface()
    }

    override fun updateStallTimeout(timeoutMs: Long) {
        watchdog.updateStallTimeout(timeoutMs)
    }
}
