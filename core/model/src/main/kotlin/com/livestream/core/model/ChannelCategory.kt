package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 频道分类实体
 */
@Serializable
enum class ChannelCategoryGroup {
    CCTV,         // 央视频道
    SATELLITE,    // 卫视频道
    LOCAL,        // 地方台
    CUSTOM        // 用户自定义
}

@Serializable
data class ChannelCategory(
    val id: String,
    val name: String,
    val group: ChannelCategoryGroup,
    val orderIndex: Int = 0
)
