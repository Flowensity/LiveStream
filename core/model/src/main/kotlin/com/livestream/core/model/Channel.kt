package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 频道领域实体
 * 注意：频道数据不直接耦合播放 URL，播放 URL 由绑定的 LiveSource 独立管理
 */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val channelNumber: Int,
    val categoryId: String,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val orderIndex: Int = 0,
    val epgSourceId: String? = null
)
