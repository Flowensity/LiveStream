package com.livestream.data.epg

import app.cash.turbine.test
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.EpgDao
import com.livestream.core.database.entity.EpgProgramEntity
import com.livestream.core.model.EpgProgram
import com.livestream.data.epg.mapper.toDomain
import com.livestream.data.epg.mapper.toEntity
import com.livestream.data.epg.repository.EpgRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgRepositoryImplTest {

    private val epgDao = mockk<EpgDao>(relaxed = true)
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

        val repo = EpgRepositoryImpl(epgDao, testDispatchers)

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

        val repo = EpgRepositoryImpl(epgDao, testDispatchers)
        repo.savePrograms(programs)

        coVerify(exactly = 1) { epgDao.insertPrograms(any()) }
    }
}
