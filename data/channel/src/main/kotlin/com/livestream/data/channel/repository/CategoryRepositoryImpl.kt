package com.livestream.data.channel.repository

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.CategoryDao
import com.livestream.core.model.ChannelCategory
import com.livestream.data.channel.mapper.toDomain
import com.livestream.data.channel.mapper.toEntity
import com.livestream.domain.channel.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val dispatchers: AppDispatchers
) : CategoryRepository {

    override fun observeCategories(): Flow<List<ChannelCategory>> {
        return categoryDao.observeAllCategories()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getCategoryById(categoryId: String): ChannelCategory? {
        return withContext(dispatchers.io) {
            categoryDao.getCategoryById(categoryId)?.toDomain()
        }
    }

    override suspend fun saveCategories(categories: List<ChannelCategory>) {
        withContext(dispatchers.io) {
            categoryDao.insertCategories(categories.map { it.toEntity() })
        }
    }
}
