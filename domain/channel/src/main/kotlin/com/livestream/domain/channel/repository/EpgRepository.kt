package com.livestream.domain.channel.repository

import com.livestream.core.model.EpgProgram
import kotlinx.coroutines.flow.Flow

/**
 * EPG 节目单领域仓库契约
 */
interface EpgRepository {

    fun observeProgramsForChannel(channelId: String, fromTime: Long, toTime: Long): Flow<List<EpgProgram>>

    fun observeCurrentProgram(channelId: String, currentTime: Long): Flow<EpgProgram?>

    fun observeNextProgram(channelId: String, currentTime: Long): Flow<EpgProgram?>

    suspend fun savePrograms(programs: List<EpgProgram>)

    suspend fun deleteExpiredPrograms(beforeTimestamp: Long)

    suspend fun refreshEpgForChannel(channelId: String, channelName: String)
}
