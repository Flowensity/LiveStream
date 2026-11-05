package com.livestream.domain.channel.repository

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import kotlinx.coroutines.flow.Flow

/**
 * 频道领域仓库契约
 */
interface ChannelRepository {

    fun observeAllVisibleChannels(): Flow<List<Channel>>

    fun observeChannelsByCategory(categoryId: String): Flow<List<Channel>>

    fun observeFavoriteChannels(): Flow<List<Channel>>

    fun searchChannels(query: String): Flow<List<Channel>>

    suspend fun getChannelById(channelId: String): Channel?

    suspend fun getChannelByNumber(number: Int): Channel?

    suspend fun getAllChannelsSync(): List<Channel>

    suspend fun toggleFavorite(channelId: String, isFavorite: Boolean)

    suspend fun setChannelHidden(channelId: String, isHidden: Boolean)

    suspend fun updateChannelOrder(channelId: String, orderIndex: Int)

    fun observeSourcesForChannel(channelId: String): Flow<List<LiveSource>>

    suspend fun getSourcesForChannelSync(channelId: String): List<LiveSource>

    suspend fun getStreamUrl(sourceId: String): String?

    suspend fun saveChannels(channels: List<Channel>)

    suspend fun saveSources(sources: List<LiveSource>)

    suspend fun checkAndSeedDefaultChannels()
}
