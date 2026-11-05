package com.livestream.playback.media3.watchdog

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 播放卡死看门狗（Watchdog）：
 * 1. 监测长周期 BUFFERING 状态，若缓冲卡死持续超过超时阈值（如 10 秒），主动触发回调
 * 2. 调度器接收到卡顿告警后，可平滑触发无感知切线或有限重试，杜绝黑屏死锁
 */
class PlaybackWatchdog(
    private val dispatchers: AppDispatchers,
    private val stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
    private val onStallTimeout: suspend () -> Unit
) {
    private val watchdogScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var monitorJob: Job? = null
    private var stallTimerJob: Job? = null

    companion object {
        const val DEFAULT_STALL_TIMEOUT_MS = 10000L
    }

    fun start(statusFlow: StateFlow<PlaybackStatus>) {
        stop()
        monitorJob = watchdogScope.launch {
            statusFlow.collect { status ->
                handleStatusChange(status)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        stallTimerJob?.cancel()
        stallTimerJob = null
    }

    private fun handleStatusChange(status: PlaybackStatus) {
        if (status == PlaybackStatus.BUFFERING) {
            if (stallTimerJob == null || stallTimerJob?.isCompleted == true) {
                stallTimerJob = watchdogScope.launch {
                    delay(stallTimeoutMs)
                    onStallTimeout()
                }
            }
        } else {
            stallTimerJob?.cancel()
            stallTimerJob = null
        }
    }
}
