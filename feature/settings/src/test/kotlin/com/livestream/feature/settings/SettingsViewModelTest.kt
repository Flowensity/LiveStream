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
        val otherSource = com.livestream.core.model.CustomPlaylistSource(
            id = "test_src_2",
            name = "Other Playlist",
            url = "http://example.com/other.m3u",
            channelCount = 1,
            channelIds = listOf("ch_3")
        )
        customSourcesFlow.value = listOf(source, otherSource)
        advanceUntilIdle()

        viewModel.deleteCustomPlaylistSource(source)
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteChannelsByIds(listOf("ch_1", "ch_2")) }
        coVerify(exactly = 1) { channelRepository.deleteSourcesByIds(listOf("matched_src_1", "matched_src_2")) }
        coVerify(exactly = 1) { userPreferencesRepository.removeCustomPlaylistSource("test_src_1") }
    }

    @Test
    fun `isPipEnabled defaults to false and setPipEnabled updates repository`() = runTest(testDispatcher) {
        advanceUntilIdle()
        org.junit.Assert.assertFalse(viewModel.uiState.value.isPipEnabled)

        viewModel.setPipEnabled(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.setPipEnabled(true) }
    }

    @Test
    fun `re-importing M3U when custom channels already exist in DB does not match custom channels as presets`() = runTest(testDispatcher) {
        val existingCustomChannel = com.livestream.core.model.Channel(
            id = "user_ch_existing",
            name = "凤凰中文",
            channelNumber = 801,
            categoryId = "custom_123"
        )
        coEvery { channelRepository.getAllChannelsSync() } returns listOf(existingCustomChannel)

        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="港澳台",凤凰中文
            https://example.com/fhzw.m3u8
        """.trimIndent()

        val savedPlaylistSlot = mutableListOf<com.livestream.core.model.CustomPlaylistSource>()
        coEvery { userPreferencesRepository.saveCustomPlaylistSource(capture(savedPlaylistSlot)) } returns Unit

        val res = viewModel.importM3uInternal(m3u)
        assertTrue(res.isSuccess)
        assertTrue(savedPlaylistSlot.isNotEmpty())
        assertEquals(1, savedPlaylistSlot.last().channelIds.size)
        assertTrue(savedPlaylistSlot.last().matchedSourceIds.isEmpty())
    }

    @Test
    fun `deleteCustomPlaylistSource wipes all custom channels and sources when deleting the last playlist`() = runTest(testDispatcher) {
        val onlySource = com.livestream.core.model.CustomPlaylistSource(
            id = "only_src",
            name = "Only Playlist",
            url = "https://example.com/only.m3u",
            channelCount = 2,
            channelIds = listOf("user_ch_1", "user_ch_2")
        )
        customSourcesFlow.value = listOf(onlySource)
        advanceUntilIdle()

        viewModel.deleteCustomPlaylistSource(onlySource)
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteAllCustomChannelsAndSources() }
        coVerify(exactly = 1) { userPreferencesRepository.clearCustomPlaylistSources() }
    }

    @Test
    fun `deleteCustomPlaylistSource retains shared channels owned by another remaining playlist`() = runTest(testDispatcher) {
        val source1 = com.livestream.core.model.CustomPlaylistSource(
            id = "src_1",
            name = "Playlist 1",
            url = "https://example.com/1.m3u",
            channelCount = 2,
            channelIds = listOf("user_ch_shared", "user_ch_only1"),
            matchedSourceIds = listOf("matched_shared", "matched_only1")
        )
        val source2 = com.livestream.core.model.CustomPlaylistSource(
            id = "src_2",
            name = "Playlist 2",
            url = "https://example.com/2.m3u",
            channelCount = 1,
            channelIds = listOf("user_ch_shared"),
            matchedSourceIds = listOf("matched_shared")
        )
        customSourcesFlow.value = listOf(source1, source2)
        advanceUntilIdle()

        viewModel.deleteCustomPlaylistSource(source1)
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteChannelsByIds(listOf("user_ch_only1")) }
        coVerify(exactly = 1) { channelRepository.deleteSourcesByIds(listOf("matched_only1")) }
        coVerify(exactly = 1) { userPreferencesRepository.removeCustomPlaylistSource("src_1") }
    }

    @Test
    fun `deleting custom playlist while one of its custom channels is playing switches playback to first preset channel`() = runTest(testDispatcher) {
        val activeCustomCh = com.livestream.core.model.Channel(
            id = "user_ch_playing",
            name = "自定义台",
            channelNumber = 801,
            categoryId = "custom_1"
        )
        val presetCh = com.livestream.core.model.Channel(
            id = "cctv_600001859",
            name = "CCTV-1 综合",
            channelNumber = 1,
            categoryId = "cctv"
        )
        val orchState = MutableStateFlow(com.livestream.playback.api.OrchestratorState(currentChannel = activeCustomCh))
        val mockOrch = mockk<com.livestream.playback.api.PlaybackOrchestrator>(relaxed = true) {
            io.mockk.every { orchestratorState } returns orchState
        }
        coEvery { channelRepository.getChannelById("user_ch_playing") } returns null
        coEvery { channelRepository.getAllChannelsSync() } returns listOf(presetCh)

        val onlySource = com.livestream.core.model.CustomPlaylistSource(
            id = "src_active",
            name = "Active List",
            url = "https://example.com/active.m3u",
            channelCount = 1,
            channelIds = listOf("user_ch_playing")
        )
        customSourcesFlow.value = listOf(onlySource)

        val vmWithOrch = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            localImportServer = localImportServer,
            okHttpClient = okHttpClient,
            dispatchers = dispatchers,
            epgRepository = null,
            playbackOrchestrator = mockOrch,
            networkProbe = null,
            isTvDevice = false
        )
        advanceUntilIdle()

        vmWithOrch.deleteCustomPlaylistSource(onlySource)
        advanceUntilIdle()

        io.mockk.verify(exactly = 1) { mockOrch.playChannel(presetCh) }
        coVerify(exactly = 1) { userPreferencesRepository.setLastPlayedChannelId("cctv_600001859") }
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

    @Test
    fun `probeCustomSources detects unavailable channels and removeUnavailableCustomChannels cleans them up`() = runTest(testDispatcher) {
        val chOk = com.livestream.core.model.Channel(id = "u_ok", name = "正常台", channelNumber = 801, categoryId = "custom_1")
        val chDead = com.livestream.core.model.Channel(id = "u_dead", name = "失效台", channelNumber = 802, categoryId = "custom_1")
        io.mockk.every { channelRepository.observeCustomChannels() } returns kotlinx.coroutines.flow.flowOf(listOf(chOk, chDead))

        val srcOk = com.livestream.core.model.LiveSource(
            id = "s_ok", channelId = "u_ok", name = "线路1",
            protocol = com.livestream.core.model.StreamProtocol.HLS,
            adapterProviderId = "custom_user_source",
            url = "https://live.example.com/ok.m3u8"
        )
        val srcDead = com.livestream.core.model.LiveSource(
            id = "s_dead", channelId = "u_dead", name = "线路1",
            protocol = com.livestream.core.model.StreamProtocol.HLS,
            adapterProviderId = "custom_user_source",
            url = "https://live.example.com/dead.m3u8"
        )
        coEvery { channelRepository.getSourcesForChannelSync("u_ok") } returns listOf(srcOk)
        coEvery { channelRepository.getSourcesForChannelSync("u_dead") } returns listOf(srcDead)

        val ownerSource = com.livestream.core.model.CustomPlaylistSource(
            id = "list_1",
            name = "Test List",
            url = "https://example.com/list.m3u",
            channelCount = 2,
            channelIds = listOf("u_ok", "u_dead")
        )
        customSourcesFlow.value = listOf(ownerSource)

        val mockProbe = mockk<com.livestream.core.network.NetworkProbe>()
        coEvery { mockProbe.probeUrl("https://live.example.com/ok.m3u8", any(), any()) } returns
            com.livestream.core.network.ProbeResult.Success(35L, 200)
        coEvery { mockProbe.probeUrl("https://live.example.com/dead.m3u8", any(), any()) } returns
            com.livestream.core.network.ProbeResult.Failure("HTTP 404")

        val probeVm = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            channelRepository = channelRepository,
            categoryRepository = categoryRepository,
            localImportServer = localImportServer,
            okHttpClient = okHttpClient,
            dispatchers = dispatchers,
            epgRepository = null,
            playbackOrchestrator = null,
            networkProbe = mockProbe,
            isTvDevice = false
        )
        advanceUntilIdle()

        probeVm.probeCustomSources()
        advanceUntilIdle()

        org.junit.Assert.assertFalse(probeVm.uiState.value.isProbingSources)
        assertEquals(setOf("u_dead"), probeVm.uiState.value.unavailableCustomChannelIds)
        coVerify(exactly = 1) {
            channelRepository.updateSourceHealth("s_ok", com.livestream.core.model.SourceHealthStatus.HEALTHY, any())
        }
        coVerify(exactly = 1) {
            channelRepository.updateSourceHealth("s_dead", com.livestream.core.model.SourceHealthStatus.UNAVAILABLE, any())
        }

        probeVm.removeUnavailableCustomChannels()
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.deleteChannelsByIds(listOf("u_dead")) }
        coVerify(exactly = 1) {
            userPreferencesRepository.saveCustomPlaylistSource(
                match { it.id == "list_1" && it.channelIds == listOf("u_ok") && it.channelCount == 1 }
            )
        }
        assertEquals(emptySet<String>(), probeVm.uiState.value.unavailableCustomChannelIds)
    }
}
