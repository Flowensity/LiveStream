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

    // 时延与性能统计分段打点
    private var switchStartTimeMs = 0L
    private var networkResolutionMs = 0L
    private var enginePrepareStartTimeMs = 0L

    // 当前频道可用的源列表与索引
    private var availableSources: List<LiveSource> = emptyList()
    private var currentSourceIndex: Int = 0
    private var currentRetryCount: Int = 0

    // 缓冲卡死看门狗（超时自动换线或自愈）
    internal val watchdog = PlaybackWatchdog(dispatchers) {
        val currentMetrics = _orchestratorState.value.metrics
        _orchestratorState.value = _orchestratorState.value.copy(
            metrics = currentMetrics.copy(stallCount = currentMetrics.stallCount + 1)
        )
        switchNextSource()
    }

    companion object {
        const val MAX_RETRY_COUNT = 3
    }

    private val statusFlow = _orchestratorState
        .map { it.status }
        .stateIn(orchestratorScope, SharingStarted.Eagerly, PlaybackStatus.IDLE)

    init {
        // 启动卡顿看门狗监听
        watchdog.start(statusFlow)

        // 监听并动态同步用户设置的换台/缓冲卡死超时时间
        userPreferencesRepository?.let { repo ->
            orchestratorScope.launch {
                repo.userPreferences
                    .map { it.channelSwitchTimeoutMs }
                    .distinctUntilChanged()
                    .collect { timeoutMs ->
                        watchdog.updateStallTimeout(timeoutMs)
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
                                message = "官方网页渲染进程异常崩溃，正在恢复",
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
                _orchestratorState.value = _orchestratorState.value.copy(
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
        currentRetryCount = 0
        watchdog.start(statusFlow)

        // 记录切台起始时间点
        switchStartTimeMs = System.currentTimeMillis()
        networkResolutionMs = 0L
        enginePrepareStartTimeMs = 0L

        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.SWITCHING,
            currentChannel = channel,
            currentSource = null,
            errorInfo = null,
            isOfficialWeb = false,
            officialWebUrl = null
        )

        activePlayJob = orchestratorScope.launch {
            _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.RESOLVING)

            // 2. 加载当前频道的有效直播源
            val sources = channelRepository.getSourcesForChannelSync(channel.id)
            if (currentToken != activeSessionToken) return@launch // 过期请求立即退出

            if (sources.isEmpty()) {
                val error = PlaybackErrorInfo(
                    errorType = PlaybackErrorType.SOURCE_UNAVAILABLE,
                    message = "当前频道暂无可用直播源",
                    canRetry = false
                )
                _orchestratorState.value = _orchestratorState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorInfo = error
                )
                return@launch
            }

            availableSources = sources.sortedByDescending { it.priority }
            currentSourceIndex = if (preferredSourceId != null) {
                availableSources.indexOfFirst { it.id == preferredSourceId }.coerceAtLeast(0)
            } else {
                0
            }

            executeSourcePlayback(currentToken)
        }
    }

    private suspend fun executeSourcePlayback(sessionToken: Long) {
        if (sessionToken != activeSessionToken) return

        val source = availableSources.getOrNull(currentSourceIndex)
        if (source == null) {
            val error = PlaybackErrorInfo(
                errorType = PlaybackErrorType.SOURCE_UNAVAILABLE,
                message = "所有后备直播源均不可用",
                canRetry = false
            )
            _orchestratorState.value = _orchestratorState.value.copy(
                status = PlaybackStatus.ERROR,
                errorInfo = error
            )
            return
        }

        val channel = _orchestratorState.value.currentChannel ?: return

        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.RESOLVING,
            currentSource = source,
            currentSourceIndex = currentSourceIndex,
            totalSources = availableSources.size,
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

                    val mediaItem = PlaybackMediaItem(
                        uri = resolution.uri,
                        protocol = source.protocol,
                        title = "${channel.name} - ${source.name}",
                        channelId = channel.id,
                        sourceId = source.id,
                        headersProfileId = resolution.headersProfileId ?: source.headersProfileId,
                        drmProfileId = resolution.drmProfileId ?: source.drmProfileId
                    )

                    engine.prepare(mediaItem, playWhenReady = true)
                }
                is SourceResolution.OfficialWeb -> {
                    // 原生播放引擎停止，转入官方合规网页模式
                    engine.stop()

                    _orchestratorState.value = _orchestratorState.value.copy(
                        status = PlaybackStatus.PREPARING,
                        currentSource = source,
                        errorInfo = null,
                        isOfficialWeb = true,
                        officialWebUrl = resolution.uri
                    )
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
                message = "播放源启动异常: ${e.message}",
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

        // 尝试源回退策略：切换同频道的备选源
        if (currentSourceIndex + 1 < availableSources.size) {
            currentSourceIndex++
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
    }

    override fun previewChannel(channel: Channel) {
        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.SWITCHING,
            currentChannel = channel,
            currentSource = null,
            errorInfo = null
        )
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
        if (availableSources.size <= 1) return
        val currentToken = activeSessionToken
        val nextIndex = currentSourceIndex + 1
        if (nextIndex >= availableSources.size && _orchestratorState.value.status != PlaybackStatus.PLAYING) {
            // 所有备用源均已轮询尝试完毕且未成功起播，判定为网络超时，转入 ERROR 状态以激活网络自愈监听
            handlePlaybackFailure(
                PlaybackErrorInfo(
                    errorType = PlaybackErrorType.NETWORK_TIMEOUT,
                    message = "所有直播线路均连接超时，请检查网络",
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

    override fun retry() {
        val currentToken = activeSessionToken
        currentRetryCount = 0
        activePlayJob?.cancel()
        activePlayJob = orchestratorScope.launch {
            executeSourcePlayback(currentToken)
        }
    }

    override fun pause() {
        engine.pause()
    }

    override fun resume() {
        engine.play()
    }

    override fun stop() {
        activeSessionToken++
        activePlayJob?.cancel()
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
