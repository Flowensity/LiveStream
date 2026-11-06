package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 传输协议类型
 */
@Serializable
enum class StreamProtocol {
    HLS,
    DASH,
    PROGRESSIVE_HTTP,
    OFFICIAL_WEB
}

/**
 * 播放源可用状态
 */
@Serializable
enum class SourceHealthStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
}

/**
 * 直播播放源实体（与频道解耦）
 */
@Serializable
data class LiveSource(
    val id: String,
    val channelId: String,
    val name: String,
    val protocol: StreamProtocol,
    val adapterProviderId: String,
    val priority: Int = 0,
    val healthStatus: SourceHealthStatus = SourceHealthStatus.UNKNOWN,
    val lastCheckedTimestamp: Long = 0L,
    val headersProfileId: String? = null,
    val drmProfileId: String? = null,
    val url: String = ""
)
