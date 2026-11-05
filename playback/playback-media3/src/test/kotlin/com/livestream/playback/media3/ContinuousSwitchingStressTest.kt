package com.livestream.playback.media3

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.common.network.NetworkMonitor
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackStatus
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

/**
 * 阶段八性能与稳定性压测：连续快速切台 100 次防异步竞争与状态一致性验证
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousSwitchingStressTest {

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
    private val latencyTracker = DefaultLatencyTracker(maxWindowSize = 200)
    private val playbackMetricsFlow = MutableStateFlow(PlaybackMetrics())

    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    @Before
    fun setUp() {
        coEvery { engine.engineState } returns engineStateFlow
        coEvery { engine.playbackMetrics } returns playbackMetricsFlow
        coEvery { webPlaybackController.webState } returns webStateFlow

        sourceAdapterRegistry.register(object : SourceAdapter {
            override val providerId: String = "cctv"

            override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                return SourceResolution.NativeMedia(
                    uri = "https://live.example.com/${channel.id}/${source.id}.m3u8",
                    protocol = source.protocol.name
                )
            }

            override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                return AvailabilityResult.Available(30L)
            }
        })
    }

    @Test
    fun `rapid continuous switching of 100 channels converges strictly to the final channel without stale corruption`() = runTest(testDispatcher) {
        val totalChannels = 100
        val channelList = (1..totalChannels).map { i ->
            Channel(
                id = "cctv_$i",
                name = "CCTV-$i",
                channelNumber = i,
                categoryId = "cctv"
            )
        }

        channelList.forEach { ch ->
            val source = LiveSource(
                id = "src_${ch.id}",
                channelId = ch.id,
                name = "高清源",
                protocol = StreamProtocol.HLS,
                adapterProviderId = "cctv"
            )
            coEvery { channelRepository.getSourcesForChannelSync(ch.id) } returns listOf(source)
        }

        val orchestrator = DefaultPlaybackOrchestrator(
            engine = engine,
            channelRepository = channelRepository,
            sourceAdapterRegistry = sourceAdapterRegistry,
            webPlaybackController = webPlaybackController,
            networkMonitor = networkMonitor,
            latencyTracker = latencyTracker,
            dispatchers = testDispatchers
        )

        // 模拟用户在 TV 遥控器上以极高频率狂按方向键连续切台 100 次
        for (i in 0 until totalChannels) {
            orchestrator.playChannel(channelList[i])
        }

        // 推进所有协程调度队列至空闲
        advanceUntilIdle()

        // 验证 1：最终状态必须严格收敛于第 100 个频道，无旧频道异步回调污染
        val finalChannel = channelList.last()
        assertEquals(finalChannel.id, orchestrator.orchestratorState.value.currentChannel?.id)
        assertEquals("src_cctv_100", orchestrator.orchestratorState.value.currentSource?.id)
        assertEquals(PlaybackStatus.PREPARING, orchestrator.orchestratorState.value.status)

        // 模拟底层播放引擎渲染上屏
        engineStateFlow.value = EngineState.Playing
        advanceUntilIdle()

        // 验证 2：状态成功进入 PLAYING
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)

        // 验证 3：最终只针对最后一个频道调用了 prepare
        coVerify(exactly = 1) {
            engine.prepare(match { it.channelId == "cctv_100" && it.sourceId == "src_cctv_100" }, true)
        }

        // 验证 4：性能时延追踪器完整采集了最终切台指标
        val report = latencyTracker.latencyReport.value
        assertEquals(1, report.totalSwitches)
        assertEquals(1, report.successfulSwitches)
        assertTrue(report.meetsP95Target)
    }
}
