package com.livestream.feature.settings

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.UserPreferences
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.feature.settings.server.LocalImportServer
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
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }
    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true) {
        coEvery { userPreferences } returns prefsFlow
    }
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val categoryRepository = mockk<com.livestream.domain.channel.repository.CategoryRepository>(relaxed = true)
    private val localImportServer = mockk<LocalImportServer>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SettingsViewModel(
            userPreferencesRepository,
            channelRepository,
            categoryRepository,
            localImportServer,
            okHttpClient,
            dispatchers
        )
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

    @Test
    fun `openImportDialog starts local server and updates state`() = runTest(testDispatcher) {
        io.mockk.every { localImportServer.start(any()) } returns "http://192.168.1.10:52026/"

        viewModel.openImportDialog()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isImportDialogOpen)
        assertEquals("http://192.168.1.10:52026/", viewModel.uiState.value.localServerUrl)
        coVerify(exactly = 1) { localImportServer.start(any()) }
    }

    @Test
    fun `importM3uContent matches preset channels and saves merged sources`() = runTest(testDispatcher) {
        val presetChannels = listOf(
            com.livestream.core.model.Channel(
                id = "cctv_preset_1",
                name = "CCTV-1 综合",
                channelNumber = 1,
                categoryId = "cctv"
            )
        )
        coEvery { channelRepository.getAllChannelsSync() } returns presetChannels

        val validM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1 高清
            http://example.com/live/cctv1_custom.m3u8
        """.trimIndent()

        val capturedSources = mutableListOf<List<com.livestream.core.model.LiveSource>>()
        coEvery { channelRepository.saveSources(capture(capturedSources)) } returns Unit

        viewModel.onImportTextChanged(validM3u)
        viewModel.importM3uContent()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.importSuccessCount)
        assertTrue(capturedSources.isNotEmpty())
        val saved = capturedSources.first()
        // There should be the parsed source and the matched source attached to cctv_preset_1
        assertTrue(saved.any { it.channelId == "cctv_preset_1" && it.name == "网络线路 1" })
    }

    @Test
    fun `closeImportDialog stops local server and clears url`() = runTest(testDispatcher) {
        viewModel.closeImportDialog()
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.uiState.value.isImportDialogOpen)
        org.junit.Assert.assertNull(viewModel.uiState.value.localServerUrl)
        coVerify(atLeast = 1) { localImportServer.stop() }
    }
}
