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
    private val customSourcesFlow = MutableStateFlow<List<com.livestream.core.model.CustomPlaylistSource>>(emptyList())
    private val customChannelsFlow = MutableStateFlow<List<com.livestream.core.model.Channel>>(emptyList())
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true) {
        coEvery { userPreferences } returns prefsFlow
        coEvery { customPlaylistSources } returns customSourcesFlow
    }
    private val channelRepository = mockk<ChannelRepository>(relaxed = true) {
        coEvery { observeCustomChannels() } returns customChannelsFlow
    }
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
        coVerify(atLeast = 1) { localImportServer.start(any()) }
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
        val capturedChannels = mutableListOf<List<com.livestream.core.model.Channel>>()
        coEvery { channelRepository.saveSources(capture(capturedSources)) } returns Unit
        coEvery { channelRepository.saveChannels(capture(capturedChannels)) } returns Unit

        viewModel.onImportTextChanged(validM3u)
        viewModel.importM3uContent()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.importSuccessCount)
        assertTrue(capturedSources.isNotEmpty())
        val saved = capturedSources.first()
        // There should be the matched source attached to cctv_preset_1
        assertTrue(saved.any { it.channelId == "cctv_preset_1" && it.name.startsWith("网络线路") })
        // Scheme A: Since all channels matched preset, no duplicate custom channel should be saved
        assertTrue(capturedChannels.isEmpty() || capturedChannels.all { it.isEmpty() })
    }

    @Test
    fun `importM3uContent with mixed channels saves unmatched as custom and matched as sources only`() = runTest(testDispatcher) {
        val presetChannels = listOf(
            com.livestream.core.model.Channel(
                id = "cctv_preset_1",
                name = "CCTV-1 综合",
                channelNumber = 1,
                categoryId = "cctv"
            )
        )
        coEvery { channelRepository.getAllChannelsSync() } returns presetChannels

        val mixedM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1 综合 [4K]
            http://example.com/live/cctv1_4k.m3u8
            #EXTINF:-1 group-title="国际频道",HBO HD
            http://example.com/live/hbo.m3u8
        """.trimIndent()

        val capturedSources = mutableListOf<List<com.livestream.core.model.LiveSource>>()
        val capturedChannels = mutableListOf<List<com.livestream.core.model.Channel>>()
        coEvery { channelRepository.saveSources(capture(capturedSources)) } returns Unit
        coEvery { channelRepository.saveChannels(capture(capturedChannels)) } returns Unit

        viewModel.onImportTextChanged(mixedM3u)
        viewModel.importM3uContent()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.importSuccessCount)
        assertTrue(capturedChannels.isNotEmpty())
        val savedChannels = capturedChannels.flatten()
        // Only HBO should be saved as a new custom channel
        assertEquals(1, savedChannels.size)
        assertEquals("HBO", savedChannels[0].name)

        val savedSources = capturedSources.first()
        // CCTV-1 line should be attached to cctv_preset_1 with clean base priority 50
        val matchedCctvSource = savedSources.first { it.channelId == "cctv_preset_1" }
        assertEquals(50, matchedCctvSource.priority)

        // HBO source should be attached to HBO custom channel
        val hboSource = savedSources.first { it.channelId == savedChannels[0].id }
        assertEquals("http://example.com/live/hbo.m3u8", hboSource.url)
    }

    @Test
    fun `closeImportDialog keeps local server running for persistent phone remote`() = runTest(testDispatcher) {
        io.mockk.every { localImportServer.start(any()) } returns "http://192.168.1.10:52026/"
        viewModel.openImportDialog()
        viewModel.closeImportDialog()
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.uiState.value.isImportDialogOpen)
        assertEquals("http://192.168.1.10:52026/", viewModel.uiState.value.localServerUrl)
        coVerify(exactly = 0) { localImportServer.stop() }
    }

    @Test
    fun `when isTvDevice is false, local server is not started on init or openImportDialog`() = runTest(testDispatcher) {
        val nonTvServer = mockk<LocalImportServer>(relaxed = true)
        val nonTvVm = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            localImportServer = nonTvServer,
            okHttpClient = okHttpClient,
            dispatchers = dispatchers,
            isTv = false
        )

        nonTvVm.ensureLocalServerStarted()
        nonTvVm.openImportDialog()
        nonTvVm.openSettings()
        nonTvVm.toggleSettings()
        advanceUntilIdle()

        org.junit.Assert.assertNull(nonTvVm.uiState.value.localServerUrl)
        org.junit.Assert.assertNull(nonTvVm.uiState.value.qrBitmap)
        coVerify(exactly = 0) { nonTvServer.start(any()) }
    }

    @Test
    fun `openSourceManagement and closeSourceManagement update state`() = runTest(testDispatcher) {
        viewModel.openSourceManagement()
        assertTrue(viewModel.uiState.value.isSourceManagementOpen)
        viewModel.closeSourceManagement()
        org.junit.Assert.assertFalse(viewModel.uiState.value.isSourceManagementOpen)
    }

    @Test
    fun `clearAllCustomSources calls channelRepository and userPreferencesRepository`() = runTest(testDispatcher) {
        viewModel.clearAllCustomSources()
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteAllCustomChannelsAndSources() }
        coVerify(exactly = 1) { userPreferencesRepository.clearCustomPlaylistSources() }
    }

    @Test
    fun `deleteSingleCustomChannel calls channelRepository and updates owning custom playlist source`() = runTest(testDispatcher) {
        val ownerSource = com.livestream.core.model.CustomPlaylistSource(
            id = "src_owner",
            name = "My List",
            url = "http://example.com/list.m3u",
            channelCount = 2,
            channelIds = listOf("user_ch_123", "user_ch_456")
        )
        customSourcesFlow.value = listOf(ownerSource)
        advanceUntilIdle()

        viewModel.deleteSingleCustomChannel("user_ch_123")
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteChannelById("user_ch_123") }
        coVerify(exactly = 1) {
            userPreferencesRepository.saveCustomPlaylistSource(
                match {
                    it.id == "src_owner" &&
                        it.channelIds == listOf("user_ch_456") &&
                        it.channelCount == 1
                }
            )
        }
    }

    @Test
    fun `deleteCustomPlaylistSource deletes both channelIds and matchedSourceIds`() = runTest(testDispatcher) {
        val source = com.livestream.core.model.CustomPlaylistSource(
            id = "test_src_1",
            name = "Test Playlist",
            url = "http://example.com/playlist.m3u",
            channelCount = 2,
            channelIds = listOf("ch_1", "ch_2"),
            matchedSourceIds = listOf("matched_src_1", "matched_src_2")
        )

        viewModel.deleteCustomPlaylistSource(source)
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteChannelsByIds(listOf("ch_1", "ch_2")) }
        coVerify(exactly = 1) { channelRepository.deleteSourcesByIds(listOf("matched_src_1", "matched_src_2")) }
        coVerify(exactly = 1) { userPreferencesRepository.removeCustomPlaylistSource("test_src_1") }
    }

    @Test
    fun `ChannelMatchIndex matches exact, CCTV numbered, and prefix channels in O(1) lookup`() {
        val presetChannels = listOf(
            com.livestream.core.model.Channel(id = "cctv1", name = "CCTV-1 综合", channelNumber = 1, categoryId = "cctv"),
            com.livestream.core.model.Channel(id = "cctv5p", name = "CCTV-5+ 体育赛事", channelNumber = 6, categoryId = "cctv"),
            com.livestream.core.model.Channel(id = "hunan", name = "湖南卫视", channelNumber = 21, categoryId = "satellite")
        )
        val index = SettingsViewModel.ChannelMatchIndex(presetChannels)

        assertEquals("cctv1", index.match("CCTV1")?.id)
        assertEquals("cctv1", index.match("CCTV-1 高清")?.id)
        assertEquals("cctv5p", index.match("CCTV5+ FHD")?.id)
        assertEquals("hunan", index.match("湖南卫视-超高清 4K")?.id)
    }

    @Test
    fun `ensureLocalServerStarted defers server startup off main thread construction`() = runTest(testDispatcher) {
        val deferredServer = mockk<LocalImportServer>(relaxed = true)
        io.mockk.every { deferredServer.start(any()) } returns "http://192.168.1.20:52026/"

        val vm = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            localImportServer = deferredServer,
            okHttpClient = okHttpClient,
            dispatchers = dispatchers,
            isTv = true
        )

        // Must not synchronously block constructor/main thread before coroutine runs
        coVerify(exactly = 0) { deferredServer.start(any()) }

        advanceUntilIdle()
        coVerify(atLeast = 1) { deferredServer.start(any()) }
        assertEquals("http://192.168.1.20:52026/", vm.uiState.value.localServerUrl)
    }

    @Test
    fun `importM3uInternal rejects loopback and unsafe URLs before making network call`() = runTest(testDispatcher) {
        val strictOkHttp = mockk<OkHttpClient>(relaxed = true)
        val vm = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            localImportServer = localImportServer,
            okHttpClient = strictOkHttp,
            dispatchers = dispatchers,
            isTv = false
        )

        val result = vm.importM3uInternal("http://127.0.0.1:52026/secret.m3u")
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { strictOkHttp.newCall(any()) }
    }

    @Test
    fun `importM3uInternal assigns non-colliding channel numbers above existing custom channels`() = runTest(testDispatcher) {
        val existing = listOf(
            com.livestream.core.model.Channel(id = "ch_existing_1", name = "已有自定义A", channelNumber = 805, categoryId = "custom_1")
        )
        coEvery { channelRepository.getAllChannelsSync() } returns existing

        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="新组",新导入频道A
            https://example.com/new_a.m3u8
            #EXTINF:-1 group-title="新组",新导入频道B
            https://example.com/new_b.m3u8
        """.trimIndent()

        val capturedChannels = mutableListOf<List<com.livestream.core.model.Channel>>()
        coEvery { channelRepository.saveChannels(capture(capturedChannels)) } returns Unit

        val result = viewModel.importM3uInternal(m3u)
        assertTrue(result.isSuccess)
        val saved = capturedChannels.flatten()
        assertEquals(2, saved.size)
        assertEquals(806, saved[0].channelNumber)
        assertEquals(807, saved[1].channelNumber)
    }
}
