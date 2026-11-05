package com.livestream.feature.channel

import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup

data class ChannelUiState(
    val categories: List<ChannelCategory> = emptyList(),
    val selectedCategory: ChannelCategory? = null,
    val channels: List<Channel> = emptyList(),
    val activeChannelId: String? = null,
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val isLoading: Boolean = false
) {
    companion object {
        val ALL_CATEGORY = ChannelCategory(
            id = "virtual_all",
            name = "全部频道",
            group = ChannelCategoryGroup.CUSTOM,
            orderIndex = -2
        )
        val FAVORITE_CATEGORY = ChannelCategory(
            id = "virtual_favorite",
            name = "我的收藏",
            group = ChannelCategoryGroup.CUSTOM,
            orderIndex = -1
        )
    }
}
