package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 直播源解析结果（纯 Kotlin 数据模型，绝不直接引用 Media3 MediaItem）
 */
@Serializable
sealed interface SourceResolution {

    @Serializable
    data class NativeMedia(
        val uri: String,
        val protocol: String,
        val headersProfileId: String? = null,
        val drmProfileId: String? = null
    ) : SourceResolution

    @Serializable
    data class OfficialWeb(
        val uri: String
    ) : SourceResolution

    @Serializable
    data class Unavailable(
        val reason: String
    ) : SourceResolution
}
