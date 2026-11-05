package com.livestream.data.channel.mapper

import com.livestream.core.database.entity.LiveSourceEntity
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceHealthStatus
import com.livestream.core.model.StreamProtocol

fun LiveSourceEntity.toDomain(): LiveSource {
    val protocolEnum = when (protocol) {
        "HLS" -> StreamProtocol.HLS
        "DASH" -> StreamProtocol.DASH
        "PROGRESSIVE_HTTP" -> StreamProtocol.PROGRESSIVE_HTTP
        "OFFICIAL_WEB" -> StreamProtocol.OFFICIAL_WEB
        else -> StreamProtocol.HLS
    }
    val healthEnum = when (healthStatus) {
        "HEALTHY" -> SourceHealthStatus.HEALTHY
        "DEGRADED" -> SourceHealthStatus.DEGRADED
        "UNAVAILABLE" -> SourceHealthStatus.UNAVAILABLE
        else -> SourceHealthStatus.UNKNOWN
    }
    return LiveSource(
        id = id,
        channelId = channelId,
        name = name,
        protocol = protocolEnum,
        adapterProviderId = adapterProviderId,
        priority = priority,
        healthStatus = healthEnum,
        lastCheckedTimestamp = lastCheckedTimestamp,
        headersProfileId = headersProfileId,
        drmProfileId = drmProfileId
    )
}

fun LiveSource.toEntity(rawUrl: String = "", userAgent: String? = null, referer: String? = null): LiveSourceEntity {
    return LiveSourceEntity(
        id = id,
        channelId = channelId,
        name = name,
        url = rawUrl,
        protocol = protocol.name,
        adapterProviderId = adapterProviderId,
        priority = priority,
        healthStatus = healthStatus.name,
        lastCheckedTimestamp = lastCheckedTimestamp,
        headersProfileId = headersProfileId,
        drmProfileId = drmProfileId,
        userAgent = userAgent,
        referer = referer
    )
}
