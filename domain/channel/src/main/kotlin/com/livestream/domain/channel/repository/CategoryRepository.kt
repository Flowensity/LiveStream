package com.livestream.domain.channel.repository

import com.livestream.core.model.ChannelCategory
import kotlinx.coroutines.flow.Flow

/**
 * 分类领域仓库契约
 */
interface CategoryRepository {

    fun observeCategories(): Flow<List<ChannelCategory>>

    suspend fun getCategoryById(categoryId: String): ChannelCategory?

    suspend fun saveCategories(categories: List<ChannelCategory>)
}
