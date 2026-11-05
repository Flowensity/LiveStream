package com.livestream.feature.settings

import com.livestream.core.model.UserPreferences
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true) {
        coEvery { userPreferences } returns prefsFlow
    }
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SettingsViewModel(userPreferencesRepository, channelRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects preferences`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isHardwareDecodePreferred)
        assertTrue(viewModel.uiState.value.isSurfaceViewPreferred)
    }

    @Test
    fun `setHardwareDecodePreferred calls repo`() = runTest(testDispatcher) {
        viewModel.setHardwareDecodePreferred(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.setHardwareDecodePreferred(false) }
    }

    @Test
    fun `importM3uContent with valid content saves to channel repository`() = runTest(testDispatcher) {
        val validM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1 综合
            http://example.com/live/cctv1.m3u8
        """.trimIndent()

        viewModel.onImportTextChanged(validM3u)
        viewModel.importM3uContent()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.importSuccessCount)
        coVerify(exactly = 1) { channelRepository.saveChannels(any()) }
        coVerify(exactly = 1) { channelRepository.saveSources(any()) }
    }

    @Test
    fun `importM3uContent with empty content sets error`() = runTest(testDispatcher) {
        viewModel.onImportTextChanged("   ")
        viewModel.importM3uContent()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.importError)
    }
}
