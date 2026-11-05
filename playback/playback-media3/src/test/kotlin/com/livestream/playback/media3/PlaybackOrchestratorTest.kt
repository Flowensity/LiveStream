package com.livestream.playback.media3

import app.cash.turbine.test
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackErrorType
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.SourceResolution
import com.livestream.core.model.StreamProtocol
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.playback.api.PlaybackEngine
import com.livestream.playback.api.model.EngineState
import com.livestream.playback.api.model.PlaybackMetrics
import com.livestream.playback.api.web.WebPlaybackController
import com.livestream.playback.api.web.WebPlaybackState
import com.livestream.source.api.AvailabilityResult
import com.livestream.source.api.DefaultSourceAdapterRegistry
import com.livestream.source.api.SourceAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)
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

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)

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

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)
        orchestrator.playChannel(sampleChannel1)
        advanceUntilIdle()

        assertEquals(PlaybackStatus.ERROR, orchestrator.orchestratorState.value.status)
        assertEquals(PlaybackErrorType.SOURCE_UNAVAILABLE, orchestrator.orchestratorState.value.errorInfo?.errorType)
    }

    @Test
    fun `test source fallback when primary source fails`() = runTest(testDispatcher) {
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)
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

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)
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

        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)
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
        val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, testDispatchers)
        orchestrator.stop()
        verify(exactly = 1) { webPlaybackController.stopAndDestroy() }

        orchestrator.release()
        verify(atLeast = 2) { webPlaybackController.stopAndDestroy() }
    }
}
