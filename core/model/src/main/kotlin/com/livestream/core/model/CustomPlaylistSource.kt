package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 用户导入的自定义播放列表/M3U源元数据
 */
@Serializable
data class CustomPlaylistSource(
    val id: String,
    val name: String,
    val url: String,
    val channelCount: Int,
    val channelIds: List<String> = emptyList(),
    val matchedSourceIds: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val epgUrl: String = ""
)
