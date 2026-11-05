package com.livestream.data.channel

import app.cash.turbine.test
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.ChannelDao
import com.livestream.core.database.dao.LiveSourceDao
import com.livestream.core.database.entity.ChannelEntity
import com.livestream.core.database.entity.LiveSourceEntity
import com.livestream.data.channel.repository.ChannelRepositoryImpl
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

class ChannelRepositoryImplTest {

    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val liveSourceDao = mockk<LiveSourceDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    private val sampleChannelEntity = ChannelEntity(
        id = "cctv1",
        name = "CCTV-1",
        channelNumber = 1,
        categoryId = "cctv"
    )

    @Test
    fun `test observeAllVisibleChannels maps entities properly`() = runTest(testDispatcher) {
        every { channelDao.observeAllVisibleChannels() } returns flowOf(listOf(sampleChannelEntity))

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, testDispatchers)

        repo.observeAllVisibleChannels().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("cctv1", list.first().id)
            awaitComplete()
        }
    }

    @Test
    fun `test toggleFavorite updates channelDao`() = runTest(testDispatcher) {
        coEvery { channelDao.updateFavoriteStatus("cctv1", true, any()) } returns Unit

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, testDispatchers)
        repo.toggleFavorite("cctv1", true)

        coVerify(exactly = 1) { channelDao.updateFavoriteStatus("cctv1", true, any()) }
    }

    @Test
    fun `test observeSourcesForChannel maps source entities`() = runTest(testDispatcher) {
        val sourceEntity = LiveSourceEntity(
            id = "s1",
            channelId = "cctv1",
            name = "线路1",
            url = "https://example.com/stream.m3u8",
            protocol = "HLS",
            adapterProviderId = "test_adapter"
        )
        every { liveSourceDao.observeSourcesByChannelId("cctv1") } returns flowOf(listOf(sourceEntity))

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, testDispatchers)

        repo.observeSourcesForChannel("cctv1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("s1", list.first().id)
            awaitComplete()
        }
    }
}
