package com.livestream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livestream.core.database.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY last_played_timestamp DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int = 30): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlayback(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE channel_id = :channelId")
    suspend fun deleteHistoryByChannelId(channelId: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
