package com.livestream.playback.api.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

/**
 * 单次切台时延诊断明细记录
 */
data class SwitchRecord(
    val channelId: String,
    val sourceId: String,
    val isSuccess: Boolean,
    val totalDurationMs: Long,
    val networkResolutionMs: Long = 0L,
    val enginePrepareMs: Long = 0L,
    val decodeToFirstFrameMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * 时延与成功率统计报告（分位数基准）
 */
data class LatencyReport(
    val totalSwitches: Int = 0,
    val successfulSwitches: Int = 0,
    val successRatePercent: Double = 100.0,
    val p50SwitchLatencyMs: Long = 0L,
    val p90SwitchLatencyMs: Long = 0L,
    val p95SwitchLatencyMs: Long = 0L,
    val averageSwitchLatencyMs: Double = 0.0,
    val averageNetworkResolutionMs: Double = 0.0,
    val averageDecodeMs: Double = 0.0,
    val meetsP95Target: Boolean = true,
    val meetsSuccessRateTarget: Boolean = true
)

interface LatencyTracker {
    val latencyReport: StateFlow<LatencyReport>

    fun recordSwitch(record: SwitchRecord)

    fun reset()
}

/**
 * 默认切台时延度量追踪器
 * 采用 Nearest-Rank 分位数算法计算 P50 / P90 / P95
 */
class DefaultLatencyTracker(
    private val maxWindowSize: Int = 200
) : LatencyTracker {

    private val records = mutableListOf<SwitchRecord>()
    private val _latencyReport = MutableStateFlow(LatencyReport())
    override val latencyReport: StateFlow<LatencyReport> = _latencyReport.asStateFlow()

    companion object {
        const val TARGET_P95_SWITCH_MS = 2000L
        const val TARGET_SUCCESS_RATE_PERCENT = 99.0
    }

    @Synchronized
    override fun recordSwitch(record: SwitchRecord) {
        if (records.size >= maxWindowSize) {
            records.removeAt(0)
        }
        records.add(record)
        calculateReport()
    }

    @Synchronized
    override fun reset() {
        records.clear()
        _latencyReport.value = LatencyReport()
    }

    private fun calculateReport() {
        if (records.isEmpty()) {
            _latencyReport.value = LatencyReport()
            return
        }

        val total = records.size
        val successes = records.filter { it.isSuccess }
        val successCount = successes.size
        val successRate = (successCount.toDouble() / total.toDouble()) * 100.0

        if (successes.isEmpty()) {
            _latencyReport.value = LatencyReport(
                totalSwitches = total,
                successfulSwitches = 0,
                successRatePercent = 0.0,
                meetsP95Target = false,
                meetsSuccessRateTarget = false
            )
            return
        }

        val sortedDurations = successes.map { it.totalDurationMs }.sorted()
        val p50 = calculatePercentile(sortedDurations, 0.50)
        val p90 = calculatePercentile(sortedDurations, 0.90)
        val p95 = calculatePercentile(sortedDurations, 0.95)

        val avgTotal = successes.map { it.totalDurationMs }.average()
        val avgNet = successes.map { it.networkResolutionMs }.average()
        val avgDecode = successes.map { it.decodeToFirstFrameMs }.average()

        _latencyReport.update {
            LatencyReport(
                totalSwitches = total,
                successfulSwitches = successCount,
                successRatePercent = successRate,
                p50SwitchLatencyMs = p50,
                p90SwitchLatencyMs = p90,
                p95SwitchLatencyMs = p95,
                averageSwitchLatencyMs = avgTotal,
                averageNetworkResolutionMs = avgNet,
                averageDecodeMs = avgDecode,
                meetsP95Target = p95 <= TARGET_P95_SWITCH_MS,
                meetsSuccessRateTarget = successRate >= TARGET_SUCCESS_RATE_PERCENT
            )
        }
    }

    private fun calculatePercentile(sortedValues: List<Long>, percentile: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = (ceil(percentile * sortedValues.size).toInt() - 1).coerceIn(0, sortedValues.size - 1)
        return sortedValues[index]
    }
}
