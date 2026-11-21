package com.livestream.data.channel.repository

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.CategoryDao
import com.livestream.core.database.dao.ChannelDao
import com.livestream.core.database.dao.LiveSourceDao
import com.livestream.core.datastore.UserPreferencesDataStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val liveSourceDao: LiveSourceDao,
    private val categoryDao: CategoryDao,
    private val dispatchers: AppDispatchers,
    private val userPreferencesDataStore: UserPreferencesDataStore? = null
) : ChannelRepository {

    constructor(
        channelDao: ChannelDao,
        liveSourceDao: LiveSourceDao,
        categoryDao: CategoryDao,
        dispatchers: AppDispatchers
    ) : this(channelDao, liveSourceDao, categoryDao, dispatchers, null)

    private var hasSeeded = false
    private val seedMutex = kotlinx.coroutines.sync.Mutex()

    companion object {
        const val DB_BATCH_CHUNK_SIZE = 500
    }

    override fun observeAllVisibleChannels(): Flow<List<Channel>> {
        return channelDao.observeAllVisibleChannels()
            .onStart { checkAndSeedDefaultChannels() }
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channelDao.observeChannelsByCategory(categoryId)
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
        if (channels.isEmpty()) return
        withContext(dispatchers.io) {
            val referencedCategoryEntities = channels.asSequence()
                .map { it.categoryId }
                .distinct()
                .map { catId ->
                    com.livestream.core.database.entity.CategoryEntity(
                        id = catId,
                        name = "自定义源",
                        group = com.livestream.core.model.ChannelCategoryGroup.CUSTOM.name,
                        orderIndex = 999
                    )
                }
                .toList()
            if (referencedCategoryEntities.isNotEmpty()) {
                categoryDao.insertCategoriesIgnore(referencedCategoryEntities)
            }
            for (chunk in channels.chunked(DB_BATCH_CHUNK_SIZE)) {
                channelDao.insertChannels(chunk.map { it.toEntity() })
            }
        }
    }

    override suspend fun saveSources(sources: List<LiveSource>) {
        if (sources.isEmpty()) return
        withContext(dispatchers.io) {
            for (chunk in sources.chunked(DB_BATCH_CHUNK_SIZE)) {
                liveSourceDao.insertSources(chunk.map { it.toEntity() })
            }
        }
    }

    override suspend fun updateSourceHealth(
        sourceId: String,
        status: com.livestream.core.model.SourceHealthStatus,
        timestamp: Long
    ) {
        withContext(dispatchers.io) {
            liveSourceDao.updateSourceHealth(sourceId, status.name, timestamp)
        }
    }

    override fun observeCustomChannels(): Flow<List<Channel>> {
        return channelDao.observeCustomChannels()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun deleteChannelById(channelId: String) {
        withContext(dispatchers.io) {
            channelDao.deleteChannelById(channelId)
            liveSourceDao.deleteSourcesByChannelId(channelId)
        }
    }

    override suspend fun deleteChannelsByIds(channelIds: List<String>) {
        if (channelIds.isEmpty()) return
        withContext(dispatchers.io) {
            for (chunk in channelIds.chunked(DB_BATCH_CHUNK_SIZE)) {
                channelDao.deleteChannelsByIds(chunk)
                liveSourceDao.deleteSourcesByChannelIds(chunk)
            }
        }
    }

    override suspend fun deleteSourcesByIds(sourceIds: List<String>) {
        if (sourceIds.isEmpty()) return
        withContext(dispatchers.io) {
            for (chunk in sourceIds.chunked(DB_BATCH_CHUNK_SIZE)) {
                liveSourceDao.deleteSourcesByIds(chunk)
            }
        }
    }

    override suspend fun deleteAllCustomChannelsAndSources() {
        withContext(dispatchers.io) {
            channelDao.deleteAllCustomChannels()
            liveSourceDao.deleteAllCustomSources()
            categoryDao.deleteAllCustomCategories()
        }
    }

    override suspend fun checkAndSeedDefaultChannels() {
        if (hasSeeded) return
        withContext(dispatchers.io) {
            seedMutex.withLock {
                if (hasSeeded) return@withLock

                val seededVersion = userPreferencesDataStore?.getPresetSeedVersion() ?: 0
                if (seededVersion >= PresetChannelDataProvider.PRESET_DATA_VERSION && channelDao.getChannelCount() > 0) {
                    hasSeeded = true
                    return@withLock
                }

                // Read existing channels before upserting categories/channels so user state is always preserved
                val existingChannels = channelDao.getAllChannelsSync().associateBy { it.id }

                // Upsert predefined categories without cascading deletes
                categoryDao.insertCategories(PresetChannelDataProvider.categories.map { it.toEntity() })

                val defaultChannelIds = PresetChannelDataProvider.allDefaultChannels.mapTo(HashSet()) { it.id }
                val obsoletePresetIds = existingChannels.keys.filter { id ->
                    (id.startsWith("cctv_") || id.startsWith("weishi_")) && id !in defaultChannelIds
                }
                if (obsoletePresetIds.isNotEmpty()) {
                    channelDao.deleteChannelsByIds(obsoletePresetIds)
                    liveSourceDao.deleteSourcesByChannelIds(obsoletePresetIds)
                }

                val channelsToUpsert = PresetChannelDataProvider.allDefaultChannels.map { defaultChannel ->
                    val existing = existingChannels[defaultChannel.id]
                    if (existing != null) {
                        defaultChannel.copy(
                            isFavorite = existing.isFavorite,
                            isHidden = existing.isHidden,
                            orderIndex = existing.orderIndex
                        ).toEntity()
                    } else {
                        defaultChannel.toEntity()
                    }
                }
                channelDao.insertChannels(channelsToUpsert)

                // Upsert all preset official sources so satellite and CCTV sources are always populated
                liveSourceDao.insertSources(PresetChannelDataProvider.allDefaultSourceEntities)

                userPreferencesDataStore?.setPresetSeedVersion(PresetChannelDataProvider.PRESET_DATA_VERSION)
                hasSeeded = true
            }
        }
    }
}
