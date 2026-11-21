package com.livestream.playback.media3

import app.cash.turbine.test
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.common.network.NetworkMonitor
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackErrorType
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.SourceHealthStatus
import com.livestream.core.model.SourceResolution
import com.livestream.core.model.StreamProtocol
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.playback.api.PlaybackEngine
import com.livestream.playback.api.model.EngineState
import com.livestream.playback.api.model.PlaybackMetrics
import com.livestream.playback.api.monitor.DefaultLatencyTracker
import com.livestream.playback.api.web.WebPlaybackController
import com.livestream.playback.api.web.WebPlaybackState
import com.livestream.source.api.AvailabilityResult
import com.livestream.source.api.DefaultSourceAdapterRegistry
import com.livestream.source.api.SourceAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackOrchestratorTest {

    private val engine = mockk<PlaybackEngine>(relaxed = true)
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val webPlaybackController = mockk<WebPlaybackController>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val sourceAdapterRegistry = DefaultSourceAdapterRegistry()

    private val engineStateFlow = MutableStateFlow<EngineState>(EngineState.Idle)
    private val webStateFlow = MutableStateFlow<WebPlaybackState>(WebPlaybackState.Idle)
    private val isConnectedFlow = MutableStateFlow(true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true) {
        every { isConnected } returns isConnectedFlow
    }
    private val latencyTracker = DefaultLatencyTracker()
    private val playbackMetricsFlow = MutableStateFlow(PlaybackMetrics())

    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    private val sampleChannel1 = Channel(
        id = "cctv1",
        name = "CCTV-1 综合",
        channelNumber = 1,
        categoryId = "cctv"
    )

    private val sampleChannel2 = Channel(
        id = "cctv2",
        name = "CCTV-2 财经",
        channelNumber = 2,
        categoryId = "cctv"
    )

    private val sampleSource1 = LiveSource(
        id = "src_1",
        channelId = "cctv1",
        name = "线路1",
        protocol = StreamProtocol.HLS,
        adapterProviderId = "cctv",
        priority = 10
    )

    private val sampleSource2 = LiveSource(
        id = "src_2",
        channelId = "cctv1",
        name = "线路2(备用)",
        protocol = StreamProtocol.HLS,
        adapterProviderId = "cctv",
        priority = 5
    )

    @Before
    fun setUp() {
        coEvery { engine.engineState } returns engineStateFlow
        coEvery { engine.playbackMetrics } returns playbackMetricsFlow
        coEvery { webPlaybackController.webState } returns webStateFlow

        // 注册测试适配器
        sourceAdapterRegistry.register(object : SourceAdapter {
            override val providerId: String = "cctv"

            override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                return if (source.id == "src_web") {
                    SourceResolution.OfficialWeb(uri = "https://tv.cctv.com/live/cctv1")
                } else {
                    SourceResolution.NativeMedia(
                        uri = "https://live.example.com/${source.id}.m3u8",
                        protocol = source.protocol.name
                    )
                }
            }

            override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                return AvailabilityResult.Available(50L)
            }
        })
    }

    @Test
    fun `test playChannel successfully transitions to preparing and invokes engine`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        coVerify(exactly = 1) { engine.prepare(match { it.sourceId == "src_1" && it.uri.contains("src_1") }, true) }
        assertEquals(PlaybackStatus.PREPARING, orchestrator.orchestratorState.value.status)
        assertEquals("cctv1", orchestrator.orchestratorState.value.currentChannel?.id)
        assertEquals("src_1", orchestrator.orchestratorState.value.currentSource?.id)
    }

    @Test
    fun `test channel switching cancels previous job and plays new channel`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)
        coEvery { channelRepository.getSourcesForChannelSync("cctv2") } returns listOf(
            sampleSource1.copy(id = "src_ch2", channelId = "cctv2")
        )

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)

        // 快速连续切台
        orchestrator.playChannel(sampleChannel1)
        orchestrator.playChannel(sampleChannel2)
        advanceUntilIdle()

        // 最终活跃频道必须为 cctv2
        assertEquals("cctv2", orchestrator.orchestratorState.value.currentChannel?.id)
        coVerify(exactly = 1) { engine.prepare(match { it.sourceId == "src_ch2" }, true) }
    }

    @Test
    fun `test channel without sources results in error state`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns emptyList()

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        assertEquals(PlaybackStatus.ERROR, orchestrator.orchestratorState.value.status)
        assertEquals(PlaybackErrorType.SOURCE_UNAVAILABLE, orchestrator.orchestratorState.value.errorInfo?.errorType)
    }

    @Test
    fun `test source fallback when primary source fails`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        assertEquals("src_1", orchestrator.orchestratorState.value.currentSource?.id)

        // 底层模拟主线路抛出网络错误
        val error = PlaybackErrorInfo(PlaybackErrorType.NETWORK_TIMEOUT, "超时", canRetry = true)
        engineStateFlow.value = EngineState.Error(error)
        advanceUntilIdle()

        // 验证自动回退并调用备用源 src_2
        assertEquals("src_2", orchestrator.orchestratorState.value.currentSource?.id)
        coVerify(exactly = 1) { engine.prepare(match { it.sourceId == "src_2" }, true) }
    }

    @Test
    fun `test engine playing and buffering updates orchestrator state`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        engineStateFlow.value = EngineState.Buffering
        advanceUntilIdle()
        assertEquals(PlaybackStatus.BUFFERING, orchestrator.orchestratorState.value.status)

        engineStateFlow.value = EngineState.Playing
        advanceUntilIdle()
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)
    }

    @Test
    fun `test official web source enters web mode and responds to web controller states`() = runTest(testDispatcher) {
        val webSource = sampleSource1.copy(id = "src_web")
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(webSource)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        assertTrue(orchestrator.orchestratorState.value.isOfficialWeb)
        assertEquals("https://tv.cctv.com/live/cctv1", orchestrator.orchestratorState.value.officialWebUrl)
        coVerify(exactly = 1) { engine.stop() }

        // Web 状态流转为 Loading
        webStateFlow.value = WebPlaybackState.Loading(50)
        advanceUntilIdle()
        assertEquals(PlaybackStatus.BUFFERING, orchestrator.orchestratorState.value.status)

        // Web 状态流转为 Ready
        webStateFlow.value = WebPlaybackState.Ready
        advanceUntilIdle()
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)
    }

    @Test
    fun `test stop and release call webPlaybackController stopAndDestroy`() = runTest(testDispatcher) {
        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.stop()
        verify(exactly = 1) { webPlaybackController.stopAndDestroy() }

        orchestrator.release()
        verify(atLeast = 2) { webPlaybackController.stopAndDestroy() }
    }

    @Test
    fun `test network self healing automatically retries when network recovers`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 模拟断网引发网络错误（重试耗尽后转为不可再自动重试的 ERROR）
        isConnectedFlow.value = false
        val error = PlaybackErrorInfo(PlaybackErrorType.NETWORK_DISCONNECTED, "网络断开连接", canRetry = false)
        engineStateFlow.value = EngineState.Error(error)
        advanceUntilIdle()
        assertEquals(PlaybackStatus.ERROR, orchestrator.orchestratorState.value.status)

        // 模拟网络恢复 -> 验证自动触发 retry
        isConnectedFlow.value = true
        advanceUntilIdle()

        assertEquals(PlaybackStatus.PREPARING, orchestrator.orchestratorState.value.status)
        coVerify(atLeast = 2) { engine.prepare(any(), true) }
    }

    @Test
    fun `test playing success records switch metrics in latency tracker`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)

        latencyTracker.reset()
        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        engineStateFlow.value = EngineState.Playing
        advanceUntilIdle()

        val report = latencyTracker.latencyReport.value
        assertEquals(1, report.totalSwitches)
        assertEquals(1, report.successfulSwitches)
        assertEquals(100.0, report.successRatePercent, 0.01)
        assertTrue(report.meetsP95Target)
    }

    @Test
    fun `test fallback to backup source uses 100ms rapid retry delay`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 模拟首选源发生错误（触发源回退）
        engineStateFlow.value = EngineState.Error(
            PlaybackErrorInfo(PlaybackErrorType.NETWORK_TIMEOUT, "网络连接超时", canRetry = true)
        )
        testScheduler.runCurrent()

        // 推进 120ms（超过 100ms 快速换线延时）
        advanceTimeBy(120L)
        advanceUntilIdle()

        assertEquals("src_2", orchestrator.orchestratorState.value.currentSource?.id)
        assertEquals(1, orchestrator.orchestratorState.value.currentSourceIndex)
    }

    @Test
    fun `test updateStallTimeout dynamically updates orchestrator watchdog`() = runTest(testDispatcher) {
        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.updateStallTimeout(1200L)
        assertEquals(1200L, orchestrator.watchdogStallTimeoutMs)
    }

    @Test
    fun `test switchNextSource cycling through all sources transitions to ERROR to allow self-healing`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 第一次换线 -> 切到 source 2
        orchestrator.switchNextSource()
        advanceUntilIdle()
        assertEquals("src_2", orchestrator.orchestratorState.value.currentSource?.id)

        // 第二次换线 -> 尝试完所有备选源且未成功起播，转入 ERROR 状态以激活网络自愈
        orchestrator.switchNextSource()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.ERROR, orchestrator.orchestratorState.value.status)
        assertEquals(PlaybackErrorType.NETWORK_TIMEOUT, orchestrator.orchestratorState.value.errorInfo?.errorType)
    }

    @Test
    fun `test user preferences channelSwitchTimeoutMs dynamically updates watchdog`() = runTest(testDispatcher) {
        val prefsFlow = MutableStateFlow(com.livestream.core.model.UserPreferences(channelSwitchTimeoutMs = 6000L))
        val userPrefsRepo = mockk<com.livestream.domain.channel.repository.UserPreferencesRepository> {
            every { userPreferences } returns prefsFlow
        }

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers,
            userPrefsRepo
        )
        advanceUntilIdle()
        assertEquals(6000L, orchestrator.watchdogStallTimeoutMs)

        prefsFlow.value = com.livestream.core.model.UserPreferences(channelSwitchTimeoutMs = 3000L)
        advanceUntilIdle()
        assertEquals(3000L, orchestrator.watchdogStallTimeoutMs)
    }

    @Test
    fun `test probeCandidateSources probes multiple candidate sources concurrently`() = runTest(testDispatcher) {
        val slowRegistry = DefaultSourceAdapterRegistry().apply {
            register(object : SourceAdapter {
                override val providerId: String = "slow_cctv"
                override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                    return SourceResolution.NativeMedia("https://live.example.com/${source.id}.m3u8", "HLS")
                }
                override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                    kotlinx.coroutines.delay(500L)
                    return AvailabilityResult.Available(120L)
                }
            })
        }

        val s1 = sampleSource1.copy(id = "c1", adapterProviderId = "slow_cctv", priority = 30)
        val s2 = sampleSource1.copy(id = "c2", adapterProviderId = "slow_cctv", priority = 20)
        val s3 = sampleSource1.copy(id = "c3", adapterProviderId = "slow_cctv", priority = 10)
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(s1, s2, s3)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            slowRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        // Advance only 600ms (sequential would require 1500ms for 3 x 500ms probes)
        advanceTimeBy(600L)
        testScheduler.runCurrent()

        val latencies = orchestrator.orchestratorState.value.sourceLatenciesMs
        assertEquals(3, latencies.size)
        assertEquals(120L, latencies["c1"])
        assertEquals(120L, latencies["c2"])
        assertEquals(120L, latencies["c3"])
    }

    @Test
    fun `test stable playback automatically prewarms adjacent channels`() = runTest(testDispatcher) {
        val ch2Source = sampleSource1.copy(id = "src_ch2_primary", channelId = "cctv2", priority = 20)
        coEvery { channelRepository.observeAllVisibleChannels() } returns kotlinx.coroutines.flow.flowOf(
            listOf(sampleChannel1, sampleChannel2)
        )
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)
        coEvery { channelRepository.getSourcesForChannelSync("cctv2") } returns listOf(ch2Source)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // Transition to PLAYING to trigger adjacent channel pre-warming after stabilization delay
        engineStateFlow.value = EngineState.Playing
        advanceTimeBy(2000L)
        advanceUntilIdle()

        // Verify adjacent channel cctv2's sources were pre-fetched and its primary source was probed
        coVerify(atLeast = 1) { channelRepository.getSourcesForChannelSync("cctv2") }
        assertTrue(orchestrator.isChannelPrewarmed("cctv2"))
    }

    @Test
    fun `test playChannel uses prewarmedSourcesCache without redundant DB query`() = runTest(testDispatcher) {
        val ch2Source = sampleSource1.copy(id = "src_ch2_primary", channelId = "cctv2", priority = 20)
        coEvery { channelRepository.observeAllVisibleChannels() } returns kotlinx.coroutines.flow.flowOf(
            listOf(sampleChannel1, sampleChannel2)
        )
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)
        coEvery { channelRepository.getSourcesForChannelSync("cctv2") } returns listOf(ch2Source)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        engineStateFlow.value = EngineState.Playing
        advanceTimeBy(2000L)
        advanceUntilIdle()
        assertTrue(orchestrator.isChannelPrewarmed("cctv2"))

        // 切换到已预热的 cctv2，验证直接命中缓存且不再触发第 2 次 getSourcesForChannelSync("cctv2")
        orchestrator.playChannel(sampleChannel2)
        advanceUntilIdle()
        coVerify(exactly = 1) { channelRepository.getSourcesForChannelSync("cctv2") }
        assertEquals("src_ch2_primary", orchestrator.orchestratorState.value.currentSource?.id)
    }

    @Test
    fun `test playChannel prioritizes healthy native HLS source over OFFICIAL_WEB source`() = runTest(testDispatcher) {
        val webSource = sampleSource1.copy(
            id = "src_web",
            protocol = StreamProtocol.OFFICIAL_WEB,
            priority = 100
        )
        val nativeHlsSource = sampleSource1.copy(
            id = "src_native_hls",
            protocol = StreamProtocol.HLS,
            priority = 50
        )
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(webSource, nativeHlsSource)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 即使网页源 priority=100 高于原生 HLS 的 priority=50，仍应优先选择原生硬解 HLS 源以实现毫秒级秒开
        assertEquals("src_native_hls", orchestrator.orchestratorState.value.currentSource?.id)
    }

    @Test
    fun `test playChannel preserves isOfficialWeb container state during SWITCHING before new source resolves`() = runTest(testDispatcher) {
        val webSource1 = sampleSource1.copy(id = "src_web", channelId = "cctv1", protocol = StreamProtocol.OFFICIAL_WEB)
        val webSource2 = sampleSource1.copy(id = "src_web", channelId = "cctv2", protocol = StreamProtocol.OFFICIAL_WEB)
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(webSource1)
        coEvery { channelRepository.getSourcesForChannelSync("cctv2") } returns listOf(webSource2)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()
        assertTrue(orchestrator.orchestratorState.value.isOfficialWeb)

        // 发起切台到 cctv2（尚未执行协程体解析）
        orchestrator.playChannel(sampleChannel2)
        assertEquals(PlaybackStatus.SWITCHING, orchestrator.orchestratorState.value.status)
        // 必须保持 isOfficialWeb = true，防止 Compose 拆装 WebPlaybackContainer
        assertTrue(orchestrator.orchestratorState.value.isOfficialWeb)
    }

    @Test
    fun `test fake resolution in title is defeated by real probe resolution`() = runTest(testDispatcher) {
        val testRegistry = DefaultSourceAdapterRegistry().apply {
            register(object : SourceAdapter {
                override val providerId: String = "cctv"
                override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                    return SourceResolution.NativeMedia("https://live.example.com/${source.id}.m3u8", "HLS")
                }
                override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                    return if (source.id == "src_fake_4k") {
                        // 文本声称 4K，但探测流媒体协议真实物理尺寸仅为 720x576 标清
                        AvailabilityResult.Available(latencyMs = 80L, videoWidth = 720, videoHeight = 576)
                    } else {
                        // 真实 1080P 全高清流
                        AvailabilityResult.Available(latencyMs = 80L, videoWidth = 1920, videoHeight = 1080)
                    }
                }
            })
        }

        // 两个源初始优先级一致（不被假文本加权）
        val fake4k = sampleSource1.copy(id = "src_fake_4k", name = "线路 1 (4K 虚标)", priority = 10)
        val real1080p = sampleSource1.copy(id = "src_real_1080p", name = "线路 2", priority = 10)
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(fake4k, real1080p)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            testRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 真实探测后，真实 1080P 源必须超越虚标 4K 的标清源
        val reordered = orchestrator.orchestratorState.value.availableSources
        assertEquals("src_real_1080p", reordered.first().id)
        assertEquals(1920 to 1080, orchestrator.orchestratorState.value.sourceResolutions["src_real_1080p"])
        assertEquals(720 to 576, orchestrator.orchestratorState.value.sourceResolutions["src_fake_4k"])
    }

    @Test
    fun `test hardware decoder onVideoSizeChanged records real resolution and updates state`() = runTest(testDispatcher) {
        val s1 = sampleSource1.copy(id = "src_live_1", priority = 10)
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(s1)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 模拟 ExoPlayer 硬件解码器解码出首帧画面尺寸 1920x1080
        playbackMetricsFlow.value = PlaybackMetrics(videoWidth = 1920, videoHeight = 1080)
        advanceUntilIdle()

        assertEquals(1920 to 1080, orchestrator.orchestratorState.value.sourceResolutions["src_live_1"])
    }

    @Test
    fun `test probeCandidateSources re-ranks fast lower resolution source ahead of sluggish high resolution source`() = runTest(testDispatcher) {
        val testRegistry = DefaultSourceAdapterRegistry().apply {
            register(object : SourceAdapter {
                override val providerId: String = "cctv"
                override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                    return SourceResolution.NativeMedia("https://live.example.com/${source.id}.m3u8", "HLS")
                }
                override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                    return if (source.id == "src_sluggish_4k") {
                        // 真实 4K 但延迟极高 2500ms
                        AvailabilityResult.Available(latencyMs = 2500L, videoWidth = 3840, videoHeight = 2160)
                    } else {
                        // 真实 1080P 且延迟仅 80ms
                        AvailabilityResult.Available(latencyMs = 80L, videoWidth = 1920, videoHeight = 1080)
                    }
                }
            })
        }

        val sluggish4k = sampleSource1.copy(id = "src_sluggish_4k", name = "线路 1", priority = 10)
        val fast1080p = sampleSource1.copy(id = "src_fast_1080p", name = "线路 2", priority = 10)
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sluggish4k, fast1080p)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            testRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        // 测速后，80ms 的极速 1080P 线路应当排在 2500ms 严重卡顿的 4K 之前
        val reordered = orchestrator.orchestratorState.value.availableSources
        assertEquals("src_fast_1080p", reordered.first().id)
    }

    @Test
    fun `test switchNextSource retries playback when channel has only one source`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        orchestrator.switchNextSource()
        advanceUntilIdle()

        coVerify(exactly = 2) { engine.prepare(match { it.sourceId == "src_1" }, true) }
    }

    @Test
    fun `test handlePlaybackFailure wraps around to index 0 when preferred source at index 1 fails`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1, preferredSourceId = "src_2")
        advanceUntilIdle()
        assertEquals("src_2", orchestrator.orchestratorState.value.currentSource?.id)

        engineStateFlow.value = EngineState.Error(
            PlaybackErrorInfo(PlaybackErrorType.NETWORK_TIMEOUT, "超时", canRetry = true)
        )
        advanceUntilIdle()

        assertEquals("src_1", orchestrator.orchestratorState.value.currentSource?.id)
        coVerify(exactly = 1) { engine.prepare(match { it.sourceId == "src_1" }, true) }
    }

    @Test
    fun `test probeCandidateSources commits state updates on main dispatcher without racing IO thread`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val splitDispatchers = object : AppDispatchers {
            override val main: CoroutineDispatcher = mainDispatcher
            override val io: CoroutineDispatcher = ioDispatcher
            override val default: CoroutineDispatcher = mainDispatcher
        }
        val mainThreadStates = mutableListOf<String>()
        val probeRegistry = DefaultSourceAdapterRegistry().apply {
            register(object : SourceAdapter {
                override val providerId: String = "cctv"
                override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                    return SourceResolution.NativeMedia("https://live.example.com/${source.id}.m3u8", "HLS")
                }
                override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                    return AvailabilityResult.Available(latencyMs = if (source.id == "src_2") 40L else 600L)
                }
            })
        }
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            probeRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            splitDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()
        mainThreadStates.add(orchestrator.orchestratorState.value.availableSources.first().id)

        assertEquals("src_2", mainThreadStates.single())
        assertEquals(2, orchestrator.orchestratorState.value.sourceLatenciesMs.size)
    }

    @Test
    fun `test prewarmedSourcesCache evicts oldest entries beyond bounded capacity`() = runTest(testDispatcher) {
        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        for (i in 1..25) {
            val ch = Channel(id = "ch_$i", name = "Channel $i", channelNumber = i, categoryId = "cctv")
            coEvery { channelRepository.getSourcesForChannelSync("ch_$i") } returns listOf(
                sampleSource1.copy(id = "src_$i", channelId = "ch_$i")
            )
            orchestrator.previewChannel(ch)
            advanceUntilIdle()
        }

        assertTrue(orchestrator.prewarmedCacheSizeForTest() <= DefaultPlaybackOrchestrator.MAX_PREWARMED_CACHE_SIZE)
    }

    @Test
    fun `test playing success records playback history with active channel and source`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1)

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        engineStateFlow.value = EngineState.Playing
        advanceUntilIdle()

        coVerify(exactly = 1) { channelRepository.recordPlaybackHistory("cctv1", "src_1") }
    }

    @Test
    fun `test NativeMedia resolution forwards custom userAgent and referer headers to PlaybackEngine`() = runTest(testDispatcher) {
        val headerSource = sampleSource1.copy(
            userAgent = "IptvPlayer/2.0",
            referer = "https://portal.example.com/"
        )
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(headerSource)
        val headerRegistry = DefaultSourceAdapterRegistry().apply {
            register(
                object : SourceAdapter {
                    override val providerId: String = "cctv"
                    override suspend fun resolve(channel: Channel, source: LiveSource) = SourceResolution.NativeMedia(
                        uri = "https://cdn.example.com/stream.m3u8",
                        protocol = "HLS",
                        userAgent = source.userAgent,
                        referer = source.referer
                    )
                    override suspend fun checkAvailability(channel: Channel, source: LiveSource) =
                        AvailabilityResult.Available(10L)
                }
            )
        }

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            headerRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            engine.prepare(
                match {
                    it.userAgent == "IptvPlayer/2.0" &&
                        it.customHeaders["Referer"] == "https://portal.example.com/"
                },
                true
            )
        }
    }

    @Test
    fun `test switchNextSource on single official web source triggers webPlaybackController reload so status does not stick in PREPARING`() = runTest(testDispatcher) {
        val webSource = sampleSource1.copy(id = "src_web", protocol = StreamProtocol.OFFICIAL_WEB)
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(webSource)
        coEvery { webPlaybackController.reload() } answers {
            webStateFlow.value = WebPlaybackState.Loading(0)
        }

        val orchestrator = DefaultPlaybackOrchestrator(
            engine,
            channelRepository,
            sourceAdapterRegistry,
            webPlaybackController,
            networkMonitor,
            latencyTracker,
            testDispatchers
        )
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        webStateFlow.value = WebPlaybackState.Playing
        advanceUntilIdle()
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)

        // 用户在播放菜单中点击「重新连接线路」
        orchestrator.switchNextSource()
        advanceUntilIdle()

        verify(exactly = 1) { webPlaybackController.reload() }
        assertEquals(PlaybackStatus.BUFFERING, orchestrator.orchestratorState.value.status)

        webStateFlow.value = WebPlaybackState.Playing
        advanceUntilIdle()
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)
    }
}
