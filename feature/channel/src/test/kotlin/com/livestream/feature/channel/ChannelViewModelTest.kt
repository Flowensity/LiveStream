package com.livestream.feature.channel

import app.cash.turbine.test
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup
import com.livestream.domain.channel.repository.CategoryRepository
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.usecase.SearchChannelsUseCase
import com.livestream.domain.channel.usecase.ToggleFavoriteUseCase
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
class ChannelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val toggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>(relaxed = true)
    private val searchChannelsUseCase = mockk<SearchChannelsUseCase>(relaxed = true)
    private val orchestratorStateFlow = MutableStateFlow(OrchestratorState())
    private val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true) {
        every { orchestratorState } returns orchestratorStateFlow
    }

    private lateinit var viewModel: ChannelViewModel

    private val testCategory = ChannelCategory(
        id = "cctv",
        name = "央视频道",
        group = ChannelCategoryGroup.CCTV,
        orderIndex = 1
    )
    private val testChannel = Channel(id = "cctv1", name = "CCTV-1", channelNumber = 1, categoryId = "cctv", isFavorite = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { categoryRepository.observeCategories() } returns flowOf(listOf(testCategory))
        coEvery { channelRepository.observeAllVisibleChannels() } returns flowOf(listOf(testChannel))
        coEvery { channelRepository.observeChannelsByCategory("cctv") } returns flowOf(listOf(testChannel))
        coEvery { channelRepository.observeFavoriteChannels() } returns flowOf(emptyList())

        viewModel = ChannelViewModel(
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            searchChannelsUseCase = searchChannelsUseCase,
            playbackOrchestrator = playbackOrchestrator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial categories contains virtual categories plus repo categories`() = runTest(testDispatcher) {
        viewModel.categories.test {
            val initial = awaitItem()
            val finalCats = if (initial.size == 3) awaitItem() else initial
            assertEquals(4, finalCats.size)
            assertEquals(ChannelUiState.ALL_CATEGORY.id, finalCats[0].id)
            assertEquals(ChannelUiState.RECENT_CATEGORY.id, finalCats[1].id)
            assertEquals(ChannelUiState.FAVORITE_CATEGORY.id, finalCats[2].id)
            assertEquals("cctv", finalCats[3].id)
        }
    }

    @Test
    fun `selecting RECENT_CATEGORY observes recent history channels`() = runTest(testDispatcher) {
        val recentCh = Channel(id = "cctv5", name = "CCTV-5", channelNumber = 5, categoryId = "cctv")
        coEvery { channelRepository.observeRecentHistoryChannels(any()) } returns flowOf(listOf(recentCh))

        viewModel.selectCategory(ChannelUiState.RECENT_CATEGORY)
        advanceUntilIdle()

        assertEquals(listOf(recentCh), viewModel.uiState.value.channels)
    }

    @Test
    fun `drawer open close toggle functions correctly`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isDrawerOpen)
        viewModel.openDrawer()
        assertTrue(viewModel.uiState.value.isDrawerOpen)

        viewModel.closeDrawer()
        assertFalse(viewModel.uiState.value.isDrawerOpen)

        viewModel.toggleDrawer()
        assertTrue(viewModel.uiState.value.isDrawerOpen)
    }

    @Test
    fun `selectChannel calls orchestrator and closes drawer`() = runTest(testDispatcher) {
        viewModel.openDrawer()
        assertTrue(viewModel.uiState.value.isDrawerOpen)

        viewModel.selectChannel(testChannel)
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isDrawerOpen)
        verify(exactly = 1) { playbackOrchestrator.playChannel(testChannel) }
    }

    @Test
    fun `toggleFavorite calls toggleFavoriteUseCase`() = runTest(testDispatcher) {
        viewModel.toggleFavorite(testChannel)
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleFavoriteUseCase(testChannel.id, true) }
    }

    @Test
    fun `rapid search query changes are debounced to avoid redundant database queries`() = runTest(testDispatcher) {
        every { searchChannelsUseCase(any()) } returns flowOf(listOf(testChannel))
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("c")
        testScheduler.advanceTimeBy(50)
        viewModel.onSearchQueryChanged("cc")
        testScheduler.advanceTimeBy(50)
        viewModel.onSearchQueryChanged("cctv")

        // Before 200ms debounce window elapses, search use case should not be invoked yet
        verify(exactly = 0) { searchChannelsUseCase(any()) }

        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()

        // Only the final query "cctv" should trigger a database search
        verify(exactly = 0) { searchChannelsUseCase("c") }
        verify(exactly = 0) { searchChannelsUseCase("cc") }
        verify(exactly = 1) { searchChannelsUseCase("cctv") }
    }

    @Test
    fun `when selected custom category is removed from categories flow, selectedCategory resets to ALL_CATEGORY`() = runTest(testDispatcher) {
        val customCat = ChannelCategory(id = "custom_999", name = "测试分组", group = ChannelCategoryGroup.CUSTOM, orderIndex = 1000)
        val categoriesFlow = MutableStateFlow(listOf(testCategory, customCat))
        coEvery { categoryRepository.observeCategories() } returns categoriesFlow
        coEvery { channelRepository.observeChannelsByCategory("custom_999") } returns flowOf(emptyList())

        val vm = ChannelViewModel(
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            searchChannelsUseCase = searchChannelsUseCase,
            playbackOrchestrator = playbackOrchestrator
        )
        advanceUntilIdle()

        vm.selectCategory(customCat)
        advanceUntilIdle()
        assertEquals("custom_999", vm.uiState.value.selectedCategory?.id)

        // Remove customCat
        categoriesFlow.value = listOf(testCategory)
        advanceUntilIdle()

        assertEquals(ChannelUiState.ALL_CATEGORY.id, vm.uiState.value.selectedCategory?.id)
    }
}
