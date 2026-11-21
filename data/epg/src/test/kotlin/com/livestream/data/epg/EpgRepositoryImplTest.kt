package com.livestream.data.epg

import app.cash.turbine.test
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.EpgDao
import com.livestream.core.database.entity.EpgProgramEntity
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.data.epg.mapper.toDomain
import com.livestream.data.epg.mapper.toEntity
import com.livestream.data.epg.repository.EpgRepositoryImpl
import com.livestream.data.epg.service.CntvEpgService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgRepositoryImplTest {

    private val epgDao = mockk<EpgDao>(relaxed = true)
    private val cntvEpgService = mockk<CntvEpgService>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    @Test
    fun `test epg mapper converts accurately`() {
        val entity = EpgProgramEntity(
            id = "epg_1",
            channelId = "cctv1",
            title = "新闻30分",
            description = "中午新闻播报",
            startTimestamp = 1000L,
            endTimestamp = 1800L
        )
        val domain = entity.toDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)

        val backToEntity = domain.toEntity()
        assertEquals(entity.id, backToEntity.id)
        assertEquals(entity.title, backToEntity.title)
    }

    @Test
    fun `test observeCurrentProgram maps entity to domain`() = runTest(testDispatcher) {
        val entity = EpgProgramEntity(
            id = "epg_1",
            channelId = "cctv1",
            title = "新闻联播",
            startTimestamp = 1900L,
            endTimestamp = 1930L
        )
        every { epgDao.observeCurrentProgram("cctv1", 1910L) } returns flowOf(entity)

        val repo = EpgRepositoryImpl(epgDao, cntvEpgService, testDispatchers)

        repo.observeCurrentProgram("cctv1", 1910L).test {
            val item = awaitItem()
            assertEquals("新闻联播", item?.title)
            awaitComplete()
        }
    }

    @Test
    fun `test savePrograms calls dao insert`() = runTest(testDispatcher) {
        val programs = listOf(
            EpgProgram("epg_1", "cctv1", "早间新闻", startTimestamp = 10L, endTimestamp = 20L)
        )
        coEvery { epgDao.insertPrograms(any()) } returns Unit

        val repo = EpgRepositoryImpl(epgDao, cntvEpgService, testDispatchers)
        repo.savePrograms(programs)

        coVerify(exactly = 1) { epgDao.insertPrograms(any()) }
    }

    @Test
    fun `refreshEpgForChannel caches result and avoids duplicate CNTV fetch within TTL`() = runTest(testDispatcher) {
        val samplePrograms = listOf(
            EpgProgram("cctv1_100", "cctv1", "朝闻天下", startTimestamp = 100_000L, endTimestamp = 200_000L)
        )
        coEvery { cntvEpgService.fetchDayPrograms("cctv1", "CCTV-1 综合", any()) } returns samplePrograms

        val repo = EpgRepositoryImpl(epgDao, cntvEpgService, testDispatchers)
        repo.refreshEpgForChannel("cctv1", "CCTV-1 综合")
        repo.refreshEpgForChannel("cctv1", "CCTV-1 综合")

        coVerify(exactly = 1) { cntvEpgService.fetchDayPrograms("cctv1", "CCTV-1 综合", any()) }
    }

    @Test
    fun `importXmltvFromUrl streams batches directly to DAO without buffering all batches`() = runTest(testDispatcher) {
        val xmlBuilder = StringBuilder("<tv>")
        for (i in 0 until 600) {
            val startHour = (i % 24).toString().padStart(2, '0')
            xmlBuilder.append(
                """<programme start="20261121${startHour}0000 +0800" stop="20261121${startHour}3000 +0800" channel="cctv1"><title>节目$i</title></programme>"""
            )
        }
        xmlBuilder.append("</tv>")

        val okHttpClient = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url("https://epg.example.com/guide.xml").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(xmlBuilder.toString().toResponseBody())
            .build()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response

        val repo = EpgRepositoryImpl(epgDao, cntvEpgService, testDispatchers, okHttpClient)
        val result = repo.importXmltvFromUrl(
            "https://epg.example.com/guide.xml",
            listOf(Channel(id = "cctv1", name = "CCTV-1", channelNumber = 1, categoryId = "cctv"))
        )

        assertTrue(result.isSuccess)
        assertEquals(600, result.getOrNull())
        // 600 programmes with batchSize=500 -> 2 synchronous batch inserts directly during parsing
        verify(exactly = 2) { epgDao.insertProgramsSync(any()) }
    }
}
