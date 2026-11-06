package com.livestream.data.epg.repository

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.EpgDao
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.data.epg.mapper.toDomain
import com.livestream.data.epg.mapper.toEntity
import com.livestream.data.epg.parser.XmltvParser
import com.livestream.data.epg.service.CntvEpgService
import com.livestream.domain.channel.repository.EpgRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val epgDao: EpgDao,
    private val cntvEpgService: CntvEpgService,
    private val dispatchers: AppDispatchers,
    private val okHttpClient: OkHttpClient?
) : EpgRepository {

    constructor(
        epgDao: EpgDao,
        cntvEpgService: CntvEpgService,
        dispatchers: AppDispatchers
    ) : this(epgDao, cntvEpgService, dispatchers, null)

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
            runCatching {
                epgDao.deleteExpiredPrograms(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            }
            val programs = cntvEpgService.fetchDayPrograms(channelId, channelName)
            if (programs.isNotEmpty()) {
                epgDao.insertPrograms(programs.map { it.toEntity() })
            }
        }
    }

    override suspend fun importXmltvFromUrl(
        epgUrl: String,
        channels: List<Channel>
    ): Result<Int> = withContext(dispatchers.io) {
        try {
            val client = okHttpClient ?: OkHttpClient()
            val request = Request.Builder().url(epgUrl).build()
            val rawPrograms = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载 XMLTV 节目单失败: HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.success(0)
                val isGzip = epgUrl.lowercase(Locale.ROOT).endsWith(".gz") ||
                    response.header("Content-Encoding")?.contains("gzip", ignoreCase = true) == true
                val stream = if (isGzip) GZIPInputStream(body.byteStream()) else body.byteStream()
                stream.use { XmltvParser.parse(it) }
            }

            if (rawPrograms.isEmpty()) {
                return@withContext Result.success(0)
            }

            // 建立 XMLTV channel 标识与本地 Channel.id 的双向索引映射（支持 tvg-id、频道 ID 与标准化名称匹配）
            val channelLookup = mutableMapOf<String, MutableList<String>>()
            for (ch in channels) {
                fun registerKey(rawKey: String?) {
                    val norm = rawKey?.trim()?.uppercase(Locale.ROOT)?.replace(" ", "")?.replace("-", "")
                    if (!norm.isNullOrEmpty()) {
                        channelLookup.getOrPut(norm) { mutableListOf() }.add(ch.id)
                    }
                }
                registerKey(ch.id)
                registerKey(ch.epgSourceId)
                registerKey(ch.name)
            }

            val mappedEntities = mutableListOf<com.livestream.core.database.entity.EpgProgramEntity>()
            for (prog in rawPrograms) {
                val normKey = prog.channelId.trim().uppercase(Locale.ROOT).replace(" ", "").replace("-", "")
                val matchedChannelIds = channelLookup[normKey]
                if (!matchedChannelIds.isNullOrEmpty()) {
                    for (localChId in matchedChannelIds) {
                        mappedEntities.add(
                            prog.copy(
                                id = "${localChId}_${prog.startTimestamp}",
                                channelId = localChId
                            ).toEntity()
                        )
                    }
                } else {
                    mappedEntities.add(prog.toEntity())
                }
            }

            if (mappedEntities.isNotEmpty()) {
                epgDao.insertPrograms(mappedEntities)
            }
            Result.success(mappedEntities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
