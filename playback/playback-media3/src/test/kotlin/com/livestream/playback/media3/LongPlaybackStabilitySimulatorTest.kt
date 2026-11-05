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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 阶段八性能与稳定性压测：8小时长周期连续稳定播放与缓冲恢复模拟
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LongPlaybackStabilitySimulatorTest {

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
    private val playbackMetricsFlow = MutableStateFlow(
        PlaybackMetrics(
            bitrateBps = 5_000_000L,
            videoWidth = 1920,
            videoHeight = 1080,
            frameRate = 60f,
            bufferedDurationMs = 3500L,
            isHardwareAccelerated = true
        )
    )

    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    private val sampleChannel = Channel(
        id = "cctv1",
        name = "CCTV-1 综合",
        channelNumber = 1,
        categoryId = "cctv"
    )

    private val sampleSource = LiveSource(
        id = "src_main",
        channelId = "cctv1",
        name = "1080P50 官方主线路",
        protocol = StreamProtocol.HLS,
        adapterProviderId = "cctv"
    )

    @Before
    fun setUp() {
        coEvery { engine.engineState } returns engineStateFlow
        coEvery { engine.playbackMetrics } returns playbackMetricsFlow
        coEvery { webPlaybackController.webState } returns webStateFlow
        coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource)

        sourceAdapterRegistry.register(object : SourceAdapter {
            override val providerId: String = "cctv"

            override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
                return SourceResolution.NativeMedia(
                    uri = "https://live.example.com/cctv1.m3u8",
                    protocol = source.protocol.name
                )
            }

            override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
                return AvailabilityResult.Available(40L)
            }
        })
    }

    @Test
    fun `8 hours continuous live playback simulation maintains PLAYING state and healthy metrics`() = runTest(testDispatcher) {
        val orchestrator = DefaultPlaybackOrchestrator(
            engine = engine,
            channelRepository = channelRepository,
            sourceAdapterRegistry = sourceAdapterRegistry,
            webPlaybackController = webPlaybackController,
            networkMonitor = networkMonitor,
            latencyTracker = latencyTracker,
            dispatchers = testDispatchers
        )

        // 1. 发起起播
        orchestrator.playChannel(sampleChannel)
        advanceUntilIdle()

        engineStateFlow.value = EngineState.Playing
        advanceUntilIdle()
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)

        // 2. 模拟 8 小时连续运行（每小时步进一次，期间模拟正常的短时数据缓冲和指标心跳）
        val eightHoursMs = TimeUnit.HOURS.toMillis(8)
        val oneHourMs = TimeUnit.HOURS.toMillis(1)
        var elapsedMs = 0L

        while (elapsedMs < eightHoursMs) {
            advanceTimeBy(oneHourMs)
            elapsedMs += oneHourMs

            // 模拟偶发微弱网络波动（300ms 轻微缓冲后立即恢复，未达看门狗阈值）
            engineStateFlow.value = EngineState.Buffering
            advanceTimeBy(300L)
            engineStateFlow.value = EngineState.Playing
            advanceTimeBy(100L)

            // 心跳指标刷新
            playbackMetricsFlow.value = playbackMetricsFlow.value.copy(
                bufferedDurationMs = 3200L + (elapsedMs % 500)
            )
            testScheduler.runCurrent()

            // 校验每小时状态健康
            assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)
            assertNull(orchestrator.orchestratorState.value.errorInfo)
        }

        // 3. 经过 8 小时模拟后验证最终整体状态
        assertEquals(PlaybackStatus.PLAYING, orchestrator.orchestratorState.value.status)
        assertEquals("cctv1", orchestrator.orchestratorState.value.currentChannel?.id)
        assertEquals(1920, orchestrator.orchestratorState.value.metrics.videoWidth)
        assertEquals(1080, orchestrator.orchestratorState.value.metrics.videoHeight)
        assertTrue(orchestrator.orchestratorState.value.metrics.isHardwareAccelerated)
        assertEquals(0L, orchestrator.orchestratorState.value.metrics.stallCount)

        // 释放
        orchestrator.release()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.RELEASED, orchestrator.orchestratorState.value.status)
    }
}
