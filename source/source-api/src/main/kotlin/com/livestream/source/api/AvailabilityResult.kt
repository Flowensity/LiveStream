package com.livestream.source.api

/**
 * 直播源可用性检测结果
 */
sealed interface AvailabilityResult {
    data class Available(
        val latencyMs: Long,
        val videoWidth: Int? = null,
        val videoHeight: Int? = null
    ) : AvailabilityResult

    data class Degraded(
        val latencyMs: Long,
        val reason: String,
        val videoWidth: Int? = null,
        val videoHeight: Int? = null
    ) : AvailabilityResult

    data class Unavailable(val reason: String) : AvailabilityResult
}
