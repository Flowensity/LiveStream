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
}
