package com.livestream.domain.channel

import app.cash.turbine.test
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.EpgRepository
import com.livestream.domain.channel.usecase.GetChannelsUseCase
import com.livestream.domain.channel.usecase.GetCurrentProgramUseCase
import com.livestream.domain.channel.usecase.SearchChannelsUseCase
import com.livestream.domain.channel.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UseCaseTest {

    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val epgRepository = mockk<EpgRepository>(relaxed = true)

    private val sampleChannel = Channel(
        id = "cctv1",
        name = "CCTV-1 综合",
        channelNumber = 1,
        categoryId = "cctv",
        isFavorite = false
    )

    @Test
    fun `test GetChannelsUseCase fetches all channels when category is null`() = runTest {
        every { channelRepository.observeAllVisibleChannels() } returns flowOf(listOf(sampleChannel))

        val useCase = GetChannelsUseCase(channelRepository)
        useCase().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("cctv1", list.first().id)
            awaitComplete()
        }
    }

    @Test
    fun `test GetChannelsUseCase filters by category`() = runTest {
        every { channelRepository.observeChannelsByCategory("cctv") } returns flowOf(listOf(sampleChannel))

        val useCase = GetChannelsUseCase(channelRepository)
        useCase("cctv").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("cctv1", list.first().id)
            awaitComplete()
        }
    }

    @Test
    fun `test SearchChannelsUseCase returns empty when query is blank`() = runTest {
        val useCase = SearchChannelsUseCase(channelRepository)
        useCase("   ").test {
            val list = awaitItem()
            assertEquals(0, list.size)
            awaitComplete()
        }
    }

    @Test
    fun `test ToggleFavoriteUseCase calls repository`() = runTest {
        coEvery { channelRepository.toggleFavorite("cctv1", true) } returns Unit

        val useCase = ToggleFavoriteUseCase(channelRepository)
        useCase("cctv1", true)

        coVerify(exactly = 1) { channelRepository.toggleFavorite("cctv1", true) }
    }

    @Test
    fun `test GetCurrentProgramUseCase delegates to epgRepository`() = runTest {
        val program = EpgProgram("epg_1", "cctv1", "新闻联播", startTimestamp = 100L, endTimestamp = 200L)
        every { epgRepository.observeCurrentProgram("cctv1", 150L) } returns flowOf(program)

        val useCase = GetCurrentProgramUseCase(epgRepository)
        useCase("cctv1", 150L).test {
            val item = awaitItem()
            assertEquals("新闻联播", item?.title)
            awaitComplete()
        }
    }
}
