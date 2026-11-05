package com.livestream.feature.player

import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.model.PlaybackStatus
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.usecase.GetCurrentProgramUseCase
import com.livestream.domain.channel.usecase.GetNextProgramUseCase
import com.livestream.domain.channel.usecase.GetProgramsForChannelUseCase
import com.livestream.domain.channel.usecase.RefreshEpgUseCase
import com.livestream.playback.api.OrchestratorState
import com.livestream.playback.api.PlaybackOrchestrator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val orchestratorStateFlow = MutableStateFlow(OrchestratorState())
    private val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true) {
        every { orchestratorState } returns orchestratorStateFlow
    }
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val getCurrentProgramUseCase = mockk<GetCurrentProgramUseCase>(relaxed = true)
    private val getNextProgramUseCase = mockk<GetNextProgramUseCase>(relaxed = true)
    private val getProgramsForChannelUseCase = mockk<GetProgramsForChannelUseCase>(relaxed = true)
    private val refreshEpgUseCase = mockk<RefreshEpgUseCase>(relaxed = true)

    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getCurrentProgramUseCase(any(), any()) } returns flowOf(null)
        every { getNextProgramUseCase(any(), any()) } returns flowOf(null)
        every { getProgramsForChannelUseCase(any(), any(), any()) } returns flowOf(emptyList())
        viewModel = PlayerViewModel(
            playbackOrchestrator,
            channelRepository,
            getCurrentProgramUseCase,
            getNextProgramUseCase,
            getProgramsForChannelUseCase,
            refreshEpgUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects orchestrator idle state and shows osd`() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertTrue(state.isOsdVisible)
    }

    @Test
    fun `playChannel triggers orchestrator and shows osd`() = runTest(testDispatcher) {
        val channel = Channel(
            id = "cctv1",
            name = "CCTV-1 综合",
            channelNumber = 1,
            categoryId = "cctv"
        )

        viewModel.playChannel(channel)
        testScheduler.runCurrent()

        verify(exactly = 1) { playbackOrchestrator.playChannel(channel) }
        assertTrue(viewModel.uiState.value.isOsdVisible)
    }

    @Test
    fun `osd auto dismisses after delay`() = runTest(testDispatcher) {
        viewModel.showOsd(2000L)
        assertTrue(viewModel.uiState.value.isOsdVisible)

        advanceTimeBy(1000L)
        assertTrue(viewModel.uiState.value.isOsdVisible)

        advanceTimeBy(1500L)
        assertFalse(viewModel.uiState.value.isOsdVisible)
    }

    @Test
    fun `number key input accumulates and debounces channel jump`() = runTest(testDispatcher) {
        val channel1 = Channel(id = "cctv1", name = "CCTV-1", channelNumber = 1, categoryId = "cctv")
        val channel12 = Channel(id = "cctv12", name = "CCTV-12", channelNumber = 12, categoryId = "cctv")
        coEvery { channelRepository.observeAllVisibleChannels() } returns flowOf(listOf(channel1, channel12))

        viewModel.onNumberKeyInput(1)
        assertEquals("1", viewModel.uiState.value.quickChannelNumber)

        advanceTimeBy(500L)
        viewModel.onNumberKeyInput(2)
        assertEquals("12", viewModel.uiState.value.quickChannelNumber)

        advanceTimeBy(1500L)
        assertEquals("", viewModel.uiState.value.quickChannelNumber)
        coVerify(exactly = 1) { playbackOrchestrator.playChannel(channel12) }
    }

    @Test
    fun `switchNextSource triggers orchestrator switchNextSource`() = runTest(testDispatcher) {
        viewModel.switchNextSource()
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackOrchestrator.switchNextSource() }
    }

    @Test
    fun `active channel changes trigger epg programs subscription`() = runTest(testDispatcher) {
        val channel = Channel(id = "cctv1", name = "CCTV-1", channelNumber = 1, categoryId = "cctv")
        val sampleProgram = EpgProgram(
            id = "cctv1_1",
            channelId = "cctv1",
            title = "新闻联播",
            startTimestamp = 1000L,
            endTimestamp = 2000L
        )
        every { getCurrentProgramUseCase("cctv1", any()) } returns flowOf(sampleProgram)

        orchestratorStateFlow.value = OrchestratorState(
            currentChannel = channel,
            status = PlaybackStatus.PLAYING
        )
        testScheduler.runCurrent()

        assertEquals(sampleProgram, viewModel.uiState.value.currentProgram)
    }

    @Test
    fun `orchestrator official web state propagates to uiState`() = runTest(testDispatcher) {
        orchestratorStateFlow.value = OrchestratorState(
            status = PlaybackStatus.PREPARING,
            isOfficialWeb = true,
            officialWebUrl = "https://tv.cctv.com/live"
        )
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isOfficialWeb)
        assertEquals("https://tv.cctv.com/live", viewModel.uiState.value.officialWebUrl)
    }
}
