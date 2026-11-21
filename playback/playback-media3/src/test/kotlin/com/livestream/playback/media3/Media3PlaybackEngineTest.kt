package com.livestream.playback.media3

import androidx.media3.common.MimeTypes
import com.livestream.core.model.StreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3PlaybackEngineTest {

    @Test
    fun `resolveMimeType lets Media3 sniff progressive HTTP streams instead of forcing MP4`() {
        assertEquals(MimeTypes.APPLICATION_M3U8, Media3PlaybackEngine.resolveMimeType(StreamProtocol.HLS))
        assertEquals(MimeTypes.APPLICATION_MPD, Media3PlaybackEngine.resolveMimeType(StreamProtocol.DASH))
        assertNull(Media3PlaybackEngine.resolveMimeType(StreamProtocol.PROGRESSIVE_HTTP))
        assertNull(Media3PlaybackEngine.resolveMimeType(StreamProtocol.OFFICIAL_WEB))
    }

    @Test
    fun `buildRequestHeaders merges customHeaders and userAgent cleanly`() {
        val withHeaders = com.livestream.playback.api.model.PlaybackMediaItem(
            uri = "https://cdn.example.com/live.ts",
            protocol = StreamProtocol.PROGRESSIVE_HTTP,
            title = "CCTV-1",
            channelId = "cctv1",
            sourceId = "src1",
            customHeaders = mapOf("Referer" to "https://player.example.com/"),
            userAgent = "CustomAgent/1.0"
        )
        val built = Media3PlaybackEngine.buildRequestHeaders(withHeaders)
        assertEquals("https://player.example.com/", built["Referer"])
        assertEquals("CustomAgent/1.0", built["User-Agent"])

        val withoutHeaders = withHeaders.copy(customHeaders = emptyMap(), userAgent = null)
        assertEquals(emptyMap<String, String>(), Media3PlaybackEngine.buildRequestHeaders(withoutHeaders))
    }
}
