package com.livestream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livestream.core.database.entity.LiveSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveSourceDao {

    @Query("SELECT * FROM live_sources WHERE channel_id = :channelId ORDER BY priority DESC")
    fun observeSourcesByChannelId(channelId: String): Flow<List<LiveSourceEntity>>

    @Query("SELECT * FROM live_sources WHERE channel_id = :channelId ORDER BY priority DESC")
    suspend fun getSourcesByChannelIdSync(channelId: String): List<LiveSourceEntity>

    @Query("SELECT * FROM live_sources WHERE id = :sourceId LIMIT 1")
    suspend fun getSourceById(sourceId: String): LiveSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<LiveSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: LiveSourceEntity)

    @Query("UPDATE live_sources SET health_status = :status, last_checked_timestamp = :timestamp WHERE id = :sourceId")
    suspend fun updateSourceHealth(sourceId: String, status: String, timestamp: Long)

    @Query("DELETE FROM live_sources WHERE id = :sourceId")
    suspend fun deleteSourceById(sourceId: String)

    @Query("DELETE FROM live_sources WHERE channel_id = :channelId")
    suspend fun deleteSourcesByChannelId(channelId: String)

    @Query("DELETE FROM live_sources WHERE channel_id IN (:channelIds)")
    suspend fun deleteSourcesByChannelIds(channelIds: List<String>): Int

    @Query("DELETE FROM live_sources WHERE id IN (:sourceIds)")
    suspend fun deleteSourcesByIds(sourceIds: List<String>): Int

    @Query("DELETE FROM live_sources WHERE adapter_provider_id = 'custom_user_source' OR id LIKE 'matched_%' OR id LIKE 'user_src_%'")
    suspend fun deleteAllCustomSources(): Int
}
