package com.livestream.playback.api

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackStatus
import com.livestream.playback.api.model.PlaybackMetrics
import com.livestream.playback.api.monitor.LatencyTracker
import com.livestream.playback.api.web.WebPlaybackController
import kotlinx.coroutines.flow.StateFlow

/**
 * 切台方向枚举
 */
enum class SwitchDirection {
    PREVIOUS,
    NEXT
}

/**
 * 调度器整体会话聚合状态
 */
data class OrchestratorState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentChannel: Channel? = null,
    val currentSource: LiveSource? = null,
    val currentSourceIndex: Int = 0,
    val totalSources: Int = 1,
    val errorInfo: PlaybackErrorInfo? = null,
    val metrics: PlaybackMetrics = PlaybackMetrics(),
    val isOfficialWeb: Boolean = false,
    val officialWebUrl: String? = null
)

/**
 * 统一播放调度层契约
 * 核心职责：
 * 1. 接收切台/播放请求并维护单一活跃会话
 * 2. 调度播放源与回退策略
 * 3. 协同取消过期异步任务与解决切台竞争
 * 4. 驱动状态机流转 (IDLE -> RESOLVING -> PREPARING -> BUFFERING -> READY -> PLAYING)
 */
interface PlaybackOrchestrator {

    val orchestratorState: StateFlow<OrchestratorState>

    val webPlaybackController: WebPlaybackController? get() = null

    val latencyTracker: LatencyTracker? get() = null

    fun playChannel(channel: Channel, preferredSourceId: String? = null)

    fun switchChannel(direction: SwitchDirection)

    fun switchNextSource()

    fun retry()

    fun pause()

    fun resume()

    fun stop()

    fun release()

    fun attachOutput(output: Any?)

    fun detachOutput()
}
