package com.livestream.source.custom

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceResolution
import com.livestream.core.model.StreamProtocol
import com.livestream.core.network.NetworkProbe
import com.livestream.core.network.ProbeResult
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.source.api.AvailabilityResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSourceAdapterTest {

    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val networkProbe = mockk<NetworkProbe>(relaxed = true)

    private val sampleChannel = Channel("ch1", "自定义频道", 801, "custom_1")
    private val sampleSource = LiveSource("src1", "ch1", "线路1", StreamProtocol.HLS, "custom_user_source")

    @Test
    fun `test resolve returns native media when url found`() = runTest {
        coEvery { channelRepository.getStreamUrl("src1") } returns "https://example.com/live.m3u8"

        val adapter = CustomSourceAdapter(channelRepository, networkProbe)
        val resolution = adapter.resolve(sampleChannel, sampleSource)

        assertTrue(resolution is SourceResolution.NativeMedia)
        assertEquals("https://example.com/live.m3u8", (resolution as SourceResolution.NativeMedia).uri)
    }

    @Test
    fun `test resolve returns unavailable when url missing`() = runTest {
        coEvery { channelRepository.getStreamUrl("src1") } returns null

        val adapter = CustomSourceAdapter(channelRepository, networkProbe)
        val resolution = adapter.resolve(sampleChannel, sampleSource)

        assertTrue(resolution is SourceResolution.Unavailable)
    }

    @Test
    fun `test checkAvailability with probe result`() = runTest {
        coEvery { channelRepository.getStreamUrl("src1") } returns "https://example.com/live.m3u8"
        coEvery { networkProbe.probeUrl("https://example.com/live.m3u8") } returns ProbeResult.Success(80L, 200)

        val adapter = CustomSourceAdapter(channelRepository, networkProbe)
        val availability = adapter.checkAvailability(sampleChannel, sampleSource)

        assertTrue(availability is AvailabilityResult.Available)
        assertEquals(80L, (availability as AvailabilityResult.Available).latencyMs)
    }
}
