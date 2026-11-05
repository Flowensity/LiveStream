package com.livestream.playback.media3.watchdog

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.PlaybackStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackWatchdogTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    private val statusFlow = MutableStateFlow(PlaybackStatus.IDLE)
    private var stallTriggerCount = 0

    private lateinit var watchdog: PlaybackWatchdog

    @Before
    fun setUp() {
        stallTriggerCount = 0
        watchdog = PlaybackWatchdog(
            dispatchers = testDispatchers,
            stallTimeoutMs = 1000L,
            onStallTimeout = {
                stallTriggerCount++
            }
        )
    }

    @Test
    fun `buffering state persisting past timeout triggers stall callback`() = runTest(testDispatcher) {
        watchdog.start(statusFlow)

        statusFlow.value = PlaybackStatus.BUFFERING
        testScheduler.runCurrent()
        assertEquals(0, stallTriggerCount)

        // 推进 500ms（尚未超时）
        advanceTimeBy(500L)
        assertEquals(0, stallTriggerCount)

        // 推进至 1050ms（已超时）
        advanceTimeBy(550L)
        assertEquals(1, stallTriggerCount)

        watchdog.stop()
    }

    @Test
    fun `recovering to playing state cancels stall timer and prevents trigger`() = runTest(testDispatcher) {
        watchdog.start(statusFlow)

        statusFlow.value = PlaybackStatus.BUFFERING
        testScheduler.runCurrent()

        // 推进 600ms
        advanceTimeBy(600L)
        assertEquals(0, stallTriggerCount)

        // 恢复至 PLAYING
        statusFlow.value = PlaybackStatus.PLAYING
        testScheduler.runCurrent()

        // 继续推进 1000ms
        advanceTimeBy(1000L)
        assertEquals(0, stallTriggerCount)

        watchdog.stop()
    }

    @Test
    fun `stop cancels active stall timer`() = runTest(testDispatcher) {
        watchdog.start(statusFlow)

        statusFlow.value = PlaybackStatus.BUFFERING
        testScheduler.runCurrent()

        watchdog.stop()

        advanceTimeBy(2000L)
        assertEquals(0, stallTriggerCount)
    }

    @Test
    fun `default stall timeout constant is 4500ms`() {
        assertEquals(4500L, PlaybackWatchdog.DEFAULT_STALL_TIMEOUT_MS)
    }

    @Test
    fun `updateStallTimeout changes timeout for subsequent buffering events`() = runTest(testDispatcher) {
        val dynamicWatchdog = PlaybackWatchdog(
            dispatchers = testDispatchers,
            stallTimeoutMs = 5000L,
            onStallTimeout = { stallTriggerCount++ }
        )
        dynamicWatchdog.start(statusFlow)
        dynamicWatchdog.updateStallTimeout(1500L)
        statusFlow.value = PlaybackStatus.BUFFERING
        testScheduler.runCurrent()

        advanceTimeBy(1000L)
        assertEquals(0, stallTriggerCount)

        advanceTimeBy(600L)
        assertEquals(1, stallTriggerCount)
        dynamicWatchdog.stop()
    }

    @Test
    fun `updateStallTimeout while actively buffering reschedules with new timeout`() = runTest(testDispatcher) {
        val dynamicWatchdog = PlaybackWatchdog(
            dispatchers = testDispatchers,
            stallTimeoutMs = 5000L,
            onStallTimeout = { stallTriggerCount++ }
        )
        dynamicWatchdog.start(statusFlow)
        statusFlow.value = PlaybackStatus.BUFFERING
        testScheduler.runCurrent()

        // 已经过去 1000ms（原 5000ms 未到）
        advanceTimeBy(1000L)
        assertEquals(0, stallTriggerCount)

        // 动态调为 1500ms（应重置并重新延时 1500ms）
        dynamicWatchdog.updateStallTimeout(1500L)
        testScheduler.runCurrent()

        advanceTimeBy(1000L)
        assertEquals(0, stallTriggerCount)

        advanceTimeBy(600L)
        assertEquals(1, stallTriggerCount)
        dynamicWatchdog.stop()
    }

    @Test
    fun `updateStallTimeout while in PLAYING state does not launch spurious timer`() = runTest(testDispatcher) {
        val dynamicWatchdog = PlaybackWatchdog(
            dispatchers = testDispatchers,
            stallTimeoutMs = 5000L,
            onStallTimeout = { stallTriggerCount++ }
        )
        dynamicWatchdog.start(statusFlow)
        statusFlow.value = PlaybackStatus.PLAYING
        testScheduler.runCurrent()

        // 在播放中动态更新超时，不应误启动计时器
        dynamicWatchdog.updateStallTimeout(1000L)
        testScheduler.runCurrent()

        advanceTimeBy(3000L)
        assertEquals(0, stallTriggerCount)
        dynamicWatchdog.stop()
    }

    @Test
    fun `updateStallTimeout enforces minimum threshold of 1000ms`() {
        val dynamicWatchdog = PlaybackWatchdog(
            dispatchers = testDispatchers,
            stallTimeoutMs = 5000L,
            onStallTimeout = {}
        )
        dynamicWatchdog.updateStallTimeout(200L)
        assertEquals(1000L, dynamicWatchdog.stallTimeoutMs)
    }
}
