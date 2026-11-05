package com.livestream.source.api

/**
 * 直播源可用性检测结果
 */
sealed interface AvailabilityResult {
    data class Available(val latencyMs: Long) : AvailabilityResult
    data class Degraded(val latencyMs: Long, val reason: String) : AvailabilityResult
    data class Unavailable(val reason: String) : AvailabilityResult
}
