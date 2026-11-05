package com.livestream.playback.media3

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackErrorType
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.SourceResolution
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.playback.api.OrchestratorState
import com.livestream.playback.api.PlaybackEngine
import com.livestream.playback.api.PlaybackOrchestrator
import com.livestream.playback.api.SwitchDirection
import com.livestream.playback.api.model.EngineState
import com.livestream.playback.api.model.PlaybackMediaItem
import com.livestream.source.api.SourceAdapterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlaybackOrchestrator @Inject constructor(
    private val engine: PlaybackEngine,
    private val channelRepository: ChannelRepository,
    private val sourceAdapterRegistry: SourceAdapterRegistry,
    private val dispatchers: AppDispatchers
) : PlaybackOrchestrator {

    private val orchestratorScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val _orchestratorState = MutableStateFlow(OrchestratorState())
    override val orchestratorState: StateFlow<OrchestratorState> = _orchestratorState.asStateFlow()

    // 活跃请求会话序列号，解决快速切台时的异步竞争
    private var activeSessionToken = 0L
    private var activePlayJob: Job? = null

    // 当前频道可用的源列表与索引
    private var availableSources: List<LiveSource> = emptyList()
    private var currentSourceIndex: Int = 0
    private var currentRetryCount: Int = 0

    companion object {
        const val MAX_RETRY_COUNT = 3
    }

    init {
        // 监听底层引擎状态并映射到调度器聚合状态
        orchestratorScope.launch {
            engine.engineState.collect { engineState ->
                handleEngineState(engineState)
            }
        }

        // 监听实时性能指标
        orchestratorScope.launch {
            engine.playbackMetrics.collect { metrics ->
                _orchestratorState.value = _orchestratorState.value.copy(metrics = metrics)
            }
        }
    }

    override fun playChannel(channel: Channel, preferredSourceId: String?) {
        val currentToken = ++activeSessionToken

        // 1. 协同取消旧播放与解析任务，杜绝旧任务回调污染新状态
        activePlayJob?.cancel()
        currentRetryCount = 0

        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.SWITCHING,
            currentChannel = channel,
            currentSource = null,
            errorInfo = null
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

        // 通过 SourceAdapter 插件解析真实媒体流
        val adapter = sourceAdapterRegistry.getAdapter(source.adapterProviderId)
        val resolution = adapter?.resolve(channel, source)
            ?: SourceResolution.Unavailable("未找到对应直播源适配器 (${source.adapterProviderId})")

        if (sessionToken != activeSessionToken) return

        when (resolution) {
            is SourceResolution.NativeMedia -> {
                _orchestratorState.value = _orchestratorState.value.copy(
                    status = PlaybackStatus.PREPARING,
                    currentSource = source,
                    errorInfo = null
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
                // 官方网页兼容播放将在阶段七扩展
                val error = PlaybackErrorInfo(
                    errorType = PlaybackErrorType.SOURCE_UNAVAILABLE,
                    message = "当前源需官方网页播放，原生播放器不支持",
                    canRetry = false
                )
                handlePlaybackFailure(error)
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
    }

    private fun handleEngineState(engineState: EngineState) {
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
                _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.PLAYING)
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
                delay(300) // 平滑延时避免突发震荡
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

        _orchestratorState.value = _orchestratorState.value.copy(
            status = PlaybackStatus.ERROR,
            errorInfo = errorInfo.copy(retryCount = currentRetryCount)
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

    override fun switchNextSource() {
        if (availableSources.size <= 1) return
        val currentToken = activeSessionToken
        currentSourceIndex = (currentSourceIndex + 1) % availableSources.size
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
        engine.stop()
        _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.IDLE)
    }

    override fun release() {
        activeSessionToken++
        activePlayJob?.cancel()
        engine.release()
        _orchestratorState.value = _orchestratorState.value.copy(status = PlaybackStatus.RELEASED)
    }

    override fun attachOutput(output: Any?) {
        engine.attachSurface(output)
    }

    override fun detachOutput() {
        engine.detachSurface()
    }
}
