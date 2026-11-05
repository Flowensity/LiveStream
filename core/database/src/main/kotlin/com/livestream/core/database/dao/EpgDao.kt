package com.livestream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livestream.core.database.entity.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    @Query("SELECT * FROM epg_programs WHERE channel_id = :channelId AND end_timestamp > :fromTime AND start_timestamp < :toTime ORDER BY start_timestamp ASC")
    fun observeProgramsForChannel(channelId: String, fromTime: Long, toTime: Long): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE channel_id = :channelId AND start_timestamp <= :currentTime AND end_timestamp > :currentTime LIMIT 1")
    fun observeCurrentProgram(channelId: String, currentTime: Long): Flow<EpgProgramEntity?>

    @Query("SELECT * FROM epg_programs WHERE channel_id = :channelId AND start_timestamp >= :currentTime ORDER BY start_timestamp ASC LIMIT 1")
    fun observeNextProgram(channelId: String, currentTime: Long): Flow<EpgProgramEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE end_timestamp < :beforeTimestamp")
    suspend fun deleteExpiredPrograms(beforeTimestamp: Long)
}
