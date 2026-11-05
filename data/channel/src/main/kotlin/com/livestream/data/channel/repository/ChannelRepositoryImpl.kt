package com.livestream.data.channel.repository

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.CategoryDao
import com.livestream.core.database.dao.ChannelDao
import com.livestream.core.database.dao.LiveSourceDao
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.data.channel.mapper.toDomain
import com.livestream.data.channel.mapper.toEntity
import com.livestream.data.channel.preset.PresetChannelDataProvider
import com.livestream.domain.channel.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val liveSourceDao: LiveSourceDao,
    private val categoryDao: CategoryDao,
    private val dispatchers: AppDispatchers
) : ChannelRepository {

    override fun observeAllVisibleChannels(): Flow<List<Channel>> {
        return channelDao.observeAllVisibleChannels()
            .onStart { checkAndSeedDefaultChannels() }
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channelDao.observeChannelsByCategory(categoryId)
            .onStart { checkAndSeedDefaultChannels() }
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeFavoriteChannels(): Flow<List<Channel>> {
        return channelDao.observeFavoriteChannels()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channelDao.searchChannels(query)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getChannelById(channelId: String): Channel? {
        return withContext(dispatchers.io) {
            channelDao.getChannelById(channelId)?.toDomain()
        }
    }

    override suspend fun getChannelByNumber(number: Int): Channel? {
        return withContext(dispatchers.io) {
            channelDao.getChannelByNumber(number)?.toDomain()
        }
    }

    override suspend fun getAllChannelsSync(): List<Channel> {
        return withContext(dispatchers.io) {
            channelDao.getAllChannelsSync().map { it.toDomain() }
        }
    }

    override suspend fun toggleFavorite(channelId: String, isFavorite: Boolean) {
        withContext(dispatchers.io) {
            channelDao.updateFavoriteStatus(channelId, isFavorite)
        }
    }

    override suspend fun setChannelHidden(channelId: String, isHidden: Boolean) {
        withContext(dispatchers.io) {
            channelDao.updateHiddenStatus(channelId, isHidden)
        }
    }

    override suspend fun updateChannelOrder(channelId: String, orderIndex: Int) {
        withContext(dispatchers.io) {
            channelDao.updateOrderIndex(channelId, orderIndex)
        }
    }

    override fun observeSourcesForChannel(channelId: String): Flow<List<LiveSource>> {
        return liveSourceDao.observeSourcesByChannelId(channelId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getSourcesForChannelSync(channelId: String): List<LiveSource> {
        return withContext(dispatchers.io) {
            liveSourceDao.getSourcesByChannelIdSync(channelId).map { it.toDomain() }
        }
    }

    override suspend fun getStreamUrl(sourceId: String): String? {
        return withContext(dispatchers.io) {
            liveSourceDao.getSourceById(sourceId)?.url
        }
    }

    override suspend fun saveChannels(channels: List<Channel>) {
        withContext(dispatchers.io) {
            channelDao.insertChannels(channels.map { it.toEntity() })
        }
    }

    override suspend fun saveSources(sources: List<LiveSource>) {
        withContext(dispatchers.io) {
            liveSourceDao.insertSources(sources.map { it.toEntity() })
        }
    }

    override suspend fun checkAndSeedDefaultChannels() {
        withContext(dispatchers.io) {
            // Always ensure predefined categories are up-to-date (e.g. 地方卫视)
            categoryDao.insertCategories(PresetChannelDataProvider.categories.map { it.toEntity() })

            // Preserve user-favorite and user-hidden settings for existing channels
            val existingChannels = channelDao.getAllChannelsSync().associateBy { it.id }
            val channelsToUpsert = PresetChannelDataProvider.allDefaultChannels.map { defaultChannel ->
                val existing = existingChannels[defaultChannel.id]
                if (existing != null) {
                    defaultChannel.copy(
                        isFavorite = existing.isFavorite,
                        isHidden = existing.isHidden
                    ).toEntity()
                } else {
                    defaultChannel.toEntity()
                }
            }
            channelDao.insertChannels(channelsToUpsert)

            // Upsert all preset official sources so satellite and CCTV sources are always populated
            liveSourceDao.insertSources(PresetChannelDataProvider.allDefaultSourceEntities)
        }
    }
}
