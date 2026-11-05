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
 * 1. 监测长周期 BUFFERING 状态，若缓冲卡死持续超过超时阈值（优化为 4.5 秒，高于 3s HTTP 连接握手超时，保证异常优先由网络层捕获），主动触发回调
 * 2. 调度器接收到卡顿告警后，可平滑触发无感知切线或有限重试，杜绝黑屏死锁
 * 3. 支持运行时线程安全地动态更新超时阈值（如联动用户设置中的切台超时）
 */
class PlaybackWatchdog(
    private val dispatchers: AppDispatchers,
    stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
    private val onStallTimeout: suspend () -> Unit
) {
    private val watchdogScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var monitorJob: Job? = null
    private var stallTimerJob: Job? = null

    @Volatile
    private var currentStatus: PlaybackStatus = PlaybackStatus.IDLE

    @Volatile
    private var _stallTimeoutMs: Long = stallTimeoutMs.coerceAtLeast(MIN_STALL_TIMEOUT_MS)
    val stallTimeoutMs: Long get() = _stallTimeoutMs

    companion object {
        const val DEFAULT_STALL_TIMEOUT_MS = 4500L
        const val MIN_STALL_TIMEOUT_MS = 1000L
    }

    /**
     * 动态更新超时时间。仅在当前明确处于 BUFFERING 状态时才重置正在运行的计时器，
     * 严防在 PLAYING/READY 播放中因并发更新误触发换线。
     */
    fun updateStallTimeout(timeoutMs: Long) {
        val coerced = timeoutMs.coerceAtLeast(MIN_STALL_TIMEOUT_MS)
        _stallTimeoutMs = coerced
        if (currentStatus == PlaybackStatus.BUFFERING && stallTimerJob?.isActive == true) {
            stallTimerJob?.cancel()
            stallTimerJob = watchdogScope.launch {
                delay(coerced)
                onStallTimeout()
            }
        }
    }

    fun start(statusFlow: StateFlow<PlaybackStatus>) {
        if (monitorJob?.isActive != true) {
            monitorJob?.cancel()
            monitorJob = watchdogScope.launch {
                statusFlow.collect { status ->
                    handleStatusChange(status)
                }
            }
        }
    }

    fun stop() {
        stallTimerJob?.cancel()
        stallTimerJob = null
    }

    fun release() {
        monitorJob?.cancel()
        monitorJob = null
        stallTimerJob?.cancel()
        stallTimerJob = null
    }

    private fun handleStatusChange(status: PlaybackStatus) {
        currentStatus = status
        if (status == PlaybackStatus.BUFFERING) {
            if (stallTimerJob == null || stallTimerJob?.isCompleted == true) {
                stallTimerJob = watchdogScope.launch {
                    delay(_stallTimeoutMs)
                    onStallTimeout()
                }
            }
        } else {
            stallTimerJob?.cancel()
            stallTimerJob = null
        }
    }
}
