package com.livestream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livestream.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE is_hidden = 0 ORDER BY order_index ASC, channel_number ASC")
    fun observeAllVisibleChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE category_id = :categoryId AND is_hidden = 0 ORDER BY order_index ASC, channel_number ASC")
    fun observeChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE is_favorite = 1 AND is_hidden = 0 ORDER BY order_index ASC, channel_number ASC")
    fun observeFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE is_hidden = 0 AND (name LIKE '%' || :query || '%' OR CAST(channel_number AS TEXT) LIKE '%' || :query || '%') ORDER BY channel_number ASC")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE channel_number = :number LIMIT 1")
    suspend fun getChannelByNumber(number: Int): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Query("UPDATE channels SET is_favorite = :isFavorite, updated_at = :timestamp WHERE id = :channelId")
    suspend fun updateFavoriteStatus(channelId: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE channels SET is_hidden = :isHidden, updated_at = :timestamp WHERE id = :channelId")
    suspend fun updateHiddenStatus(channelId: String, isHidden: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE channels SET order_index = :orderIndex, updated_at = :timestamp WHERE id = :channelId")
    suspend fun updateOrderIndex(channelId: String, orderIndex: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM channels WHERE id = :channelId")
    suspend fun deleteChannelById(channelId: String)
}
