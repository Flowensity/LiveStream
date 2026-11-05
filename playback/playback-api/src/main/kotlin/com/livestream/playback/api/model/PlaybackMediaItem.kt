package com.livestream.playback.api.model

import com.livestream.core.model.StreamProtocol

/**
 * 播放器引擎媒体项统一描述（纯 Kotlin 领域数据模型，不直接依赖 Media3 MediaItem）
 */
data class PlaybackMediaItem(
    val uri: String,
    val protocol: StreamProtocol,
    val title: String,
    val channelId: String,
    val sourceId: String,
    val headersProfileId: String? = null,
    val drmProfileId: String? = null,
    val customHeaders: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)
