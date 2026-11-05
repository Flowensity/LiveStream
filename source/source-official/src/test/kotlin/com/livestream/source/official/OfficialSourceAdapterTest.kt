package com.livestream.source.official

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

class OfficialSourceAdapterTest {

    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val networkProbe = mockk<NetworkProbe>(relaxed = true)

    private val sampleChannel = Channel("cctv1", "CCTV-1 综合", 1, "cctv")
    private val sampleSource = LiveSource("cctv1_src", "cctv1", "官方源", StreamProtocol.HLS, "official_source_provider")

    @Test
    fun `test resolve returns native media when url available`() = runTest {
        coEvery { channelRepository.getStreamUrl("cctv1_src") } returns "https://official.example.cn/cctv1.m3u8"

        val adapter = OfficialSourceAdapter(channelRepository, networkProbe)
        val resolution = adapter.resolve(sampleChannel, sampleSource)

        assertTrue(resolution is SourceResolution.NativeMedia)
        assertEquals("https://official.example.cn/cctv1.m3u8", (resolution as SourceResolution.NativeMedia).uri)
    }

    @Test
    fun `test resolve returns official web when protocol is official web`() = runTest {
        val webSource = LiveSource("cctv1_web", "cctv1", "央视频官方", StreamProtocol.OFFICIAL_WEB, "official_source_provider")
        coEvery { channelRepository.getStreamUrl("cctv1_web") } returns "https://www.yangshipin.cn/tv/home?pid=600001859"

        val adapter = OfficialSourceAdapter(channelRepository, networkProbe)
        val resolution = adapter.resolve(sampleChannel, webSource)

        assertTrue(resolution is SourceResolution.OfficialWeb)
        assertEquals("https://www.yangshipin.cn/tv/home?pid=600001859", (resolution as SourceResolution.OfficialWeb).uri)
    }

    @Test
    fun `test checkAvailability reports available`() = runTest {
        coEvery { channelRepository.getStreamUrl("cctv1_src") } returns "https://official.example.cn/cctv1.m3u8"
        coEvery { networkProbe.probeUrl("https://official.example.cn/cctv1.m3u8") } returns ProbeResult.Success(45L, 200)

        val adapter = OfficialSourceAdapter(channelRepository, networkProbe)
        val availability = adapter.checkAvailability(sampleChannel, sampleSource)

        assertTrue(availability is AvailabilityResult.Available)
        assertEquals(45L, (availability as AvailabilityResult.Available).latencyMs)
    }
}
