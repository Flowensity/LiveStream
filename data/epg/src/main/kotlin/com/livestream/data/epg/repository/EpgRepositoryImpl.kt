package com.livestream.data.epg.repository

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.EpgDao
import com.livestream.core.model.EpgProgram
import com.livestream.data.epg.mapper.toDomain
import com.livestream.data.epg.mapper.toEntity
import com.livestream.data.epg.service.CntvEpgService
import com.livestream.domain.channel.repository.EpgRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val epgDao: EpgDao,
    private val cntvEpgService: CntvEpgService,
    private val dispatchers: AppDispatchers
) : EpgRepository {

    override fun observeProgramsForChannel(
        channelId: String,
        fromTime: Long,
        toTime: Long
    ): Flow<List<EpgProgram>> {
        return epgDao.observeProgramsForChannel(channelId, fromTime, toTime)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeCurrentProgram(
        channelId: String,
        currentTime: Long
    ): Flow<EpgProgram?> {
        return epgDao.observeCurrentProgram(channelId, currentTime)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override fun observeNextProgram(
        channelId: String,
        currentTime: Long
    ): Flow<EpgProgram?> {
        return epgDao.observeNextProgram(channelId, currentTime)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override suspend fun savePrograms(programs: List<EpgProgram>) {
        withContext(dispatchers.io) {
            epgDao.insertPrograms(programs.map { it.toEntity() })
        }
    }

    override suspend fun deleteExpiredPrograms(beforeTimestamp: Long) {
        withContext(dispatchers.io) {
            epgDao.deleteExpiredPrograms(beforeTimestamp)
        }
    }

    override suspend fun refreshEpgForChannel(channelId: String, channelName: String) {
        withContext(dispatchers.io) {
            val programs = cntvEpgService.fetchDayPrograms(channelId, channelName)
            if (programs.isNotEmpty()) {
                epgDao.insertPrograms(programs.map { it.toEntity() })
            }
        }
    }
}
