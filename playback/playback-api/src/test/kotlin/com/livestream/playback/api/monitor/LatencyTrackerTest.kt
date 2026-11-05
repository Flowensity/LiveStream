package com.livestream.playback.api.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LatencyTrackerTest {

    private lateinit var tracker: DefaultLatencyTracker

    @Before
    fun setUp() {
        tracker = DefaultLatencyTracker(maxWindowSize = 100)
    }

    @Test
    fun `initial report is empty and meets defaults`() {
        val report = tracker.latencyReport.value
        assertEquals(0, report.totalSwitches)
        assertEquals(0, report.successfulSwitches)
        assertEquals(100.0, report.successRatePercent, 0.01)
        assertEquals(0L, report.p50SwitchLatencyMs)
        assertEquals(0L, report.p95SwitchLatencyMs)
    }

    @Test
    fun `percentile calculation correctly computes P50, P90, and P95 for 100 items`() {
        // 构造 1..100ms 的成功切台样本
        for (i in 1..100) {
            tracker.recordSwitch(
                SwitchRecord(
                    channelId = "ch_$i",
                    sourceId = "src_$i",
                    isSuccess = true,
                    totalDurationMs = (i * 20).toLong(), // 20ms .. 2000ms
                    networkResolutionMs = (i * 5).toLong(),
                    enginePrepareMs = (i * 5).toLong(),
                    decodeToFirstFrameMs = (i * 10).toLong()
                )
            )
        }

        val report = tracker.latencyReport.value
        assertEquals(100, report.totalSwitches)
        assertEquals(100, report.successfulSwitches)
        assertEquals(100.0, report.successRatePercent, 0.01)

        // P50: index = ceil(0.5 * 100) - 1 = 49 -> 50 * 20 = 1000ms
        assertEquals(1000L, report.p50SwitchLatencyMs)
        // P90: index = ceil(0.9 * 100) - 1 = 89 -> 90 * 20 = 1800ms
        assertEquals(1800L, report.p90SwitchLatencyMs)
        // P95: index = ceil(0.95 * 100) - 1 = 94 -> 95 * 20 = 1900ms
        assertEquals(1900L, report.p95SwitchLatencyMs)

        // 1900ms <= 2000ms (P95 验收目标)
        assertTrue(report.meetsP95Target)
        assertTrue(report.meetsSuccessRateTarget)
    }

    @Test
    fun `meetsP95Target is false when P95 exceeds 2000ms threshold`() {
        // 添加 10 个均为 2500ms 的耗时样本
        for (i in 1..10) {
            tracker.recordSwitch(
                SwitchRecord(
                    channelId = "ch_$i",
                    sourceId = "src_$i",
                    isSuccess = true,
                    totalDurationMs = 2500L
                )
            )
        }

        val report = tracker.latencyReport.value
        assertEquals(2500L, report.p95SwitchLatencyMs)
        assertFalse(report.meetsP95Target)
    }

    @Test
    fun `failed switches lower success rate and trigger meetsSuccessRateTarget flag`() {
        // 98 个成功，2 个失败
        for (i in 1..98) {
            tracker.recordSwitch(
                SwitchRecord(
                    channelId = "ch_$i",
                    sourceId = "src_$i",
                    isSuccess = true,
                    totalDurationMs = 800L
                )
            )
        }
        for (i in 99..100) {
            tracker.recordSwitch(
                SwitchRecord(
                    channelId = "ch_$i",
                    sourceId = "src_$i",
                    isSuccess = false,
                    totalDurationMs = 5000L
                )
            )
        }

        val report = tracker.latencyReport.value
        assertEquals(100, report.totalSwitches)
        assertEquals(98, report.successfulSwitches)
        assertEquals(98.0, report.successRatePercent, 0.01)
        assertFalse(report.meetsSuccessRateTarget) // < 99%
    }

    @Test
    fun `window size limits records and evicts oldest items`() {
        val smallTracker = DefaultLatencyTracker(maxWindowSize = 5)
        for (i in 1..10) {
            smallTracker.recordSwitch(
                SwitchRecord(
                    channelId = "ch_$i",
                    sourceId = "src_$i",
                    isSuccess = true,
                    totalDurationMs = (i * 100).toLong()
                )
            )
        }

        val report = smallTracker.latencyReport.value
        assertEquals(5, report.totalSwitches)
        // 剩下 600, 700, 800, 900, 1000
        assertEquals(1000L, report.p95SwitchLatencyMs)
    }

    @Test
    fun `reset clears all recorded samples`() {
        tracker.recordSwitch(
            SwitchRecord(
                channelId = "ch1",
                sourceId = "s1",
                isSuccess = true,
                totalDurationMs = 1200L
            )
        )
        assertEquals(1, tracker.latencyReport.value.totalSwitches)

        tracker.reset()
        assertEquals(0, tracker.latencyReport.value.totalSwitches)
    }
}
