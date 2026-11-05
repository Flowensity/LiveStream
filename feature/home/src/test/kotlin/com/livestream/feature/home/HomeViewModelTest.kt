package com.livestream.feature.home

import com.livestream.core.model.Channel
import com.livestream.core.model.UserPreferences
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.playback.api.OrchestratorState
import com.livestream.playback.api.PlaybackOrchestrator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val orchestratorStateFlow = MutableStateFlow(OrchestratorState())
    private val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true) {
        every { orchestratorState } returns orchestratorStateFlow
    }

    private lateinit var viewModel: HomeViewModel

    private val channel1 = Channel(id = "cctv1", name = "CCTV-1", channelNumber = 1, categoryId = "cctv")
    private val channel2 = Channel(id = "cctv2", name = "CCTV-2", channelNumber = 2, categoryId = "cctv")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { channelRepository.observeAllVisibleChannels() } returns flowOf(listOf(channel1, channel2))
        coEvery { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())

        viewModel = HomeViewModel(
            channelRepository = channelRepository,
            userPreferencesRepository = userPreferencesRepository,
            playbackOrchestrator = playbackOrchestrator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initStartupPlayback plays first channel when no last played channel exists`() = runTest(testDispatcher) {
        viewModel.initStartupPlayback()
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackOrchestrator.playChannel(channel1) }
        coVerify(exactly = 1) { userPreferencesRepository.setLastPlayedChannelId(channel1.id) }
    }

    @Test
    fun `initStartupPlayback plays last played channel when preference exists`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.userPreferences } returns flowOf(
            UserPreferences(lastPlayedChannelId = "cctv2")
        )

        viewModel.initStartupPlayback()
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackOrchestrator.playChannel(channel2) }
    }

    @Test
    fun `switchNextChannel advances to next channel in list`() = runTest(testDispatcher) {
        orchestratorStateFlow.value = OrchestratorState(currentChannel = channel1)

        viewModel.switchNextChannel()
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackOrchestrator.playChannel(channel2) }
    }

    @Test
    fun `switchPreviousChannel wraps to last channel from first`() = runTest(testDispatcher) {
        orchestratorStateFlow.value = OrchestratorState(currentChannel = channel1)

        viewModel.switchPreviousChannel()
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackOrchestrator.playChannel(channel2) }
    }
}
