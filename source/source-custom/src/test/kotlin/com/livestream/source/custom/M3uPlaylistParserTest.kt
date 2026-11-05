package com.livestream.source.custom

import com.livestream.core.model.StreamProtocol
import com.livestream.source.custom.parser.M3uPlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    private val sampleM3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="https://example.com/cctv1.png" group-title="央视频道",CCTV-1 综合
        https://live.example.com/cctv1.m3u8
        #EXTINF:-1 tvg-id="cctv2" group-title="央视频道",CCTV-2 财经
        https://live.example.com/cctv2.mpd
        #EXTINF:-1 group-title="地方台",测试地方台
        https://live.example.com/local.mp4
        #EXTINF:-1 group-title="非法内网",非法内网地址
        http://127.0.0.1:8080/secret.m3u8
        #EXTINF:-1 group-title="本地文件",本地文件
        file:///etc/passwd
    """.trimIndent()

    @Test
    fun `test m3u parsing and extraction`() {
        val parsed = M3uPlaylistParser.parse(sampleM3u)

        // 验证过滤掉 127.0.0.1 和 file:// 后，只保留 3 个合法外网源
        assertEquals(3, parsed.channels.size)
        assertEquals(3, parsed.sources.size)

        val ch1 = parsed.channels[0]
        assertEquals("CCTV-1 综合", ch1.name)
        assertEquals("https://example.com/cctv1.png", ch1.logoUrl)
        assertEquals("cctv1", ch1.epgSourceId)

        val src1 = parsed.sources[0]
        assertEquals(StreamProtocol.HLS, src1.protocol)

        val src2 = parsed.sources[1]
        assertEquals(StreamProtocol.DASH, src2.protocol)

        val src3 = parsed.sources[2]
        assertEquals(StreamProtocol.PROGRESSIVE_HTTP, src3.protocol)
    }

    @Test
    fun `test security url validator rejects malicious schemes and loopback addresses but allows lan iptv`() {
        assertFalse(M3uPlaylistParser.isSafeAndValidStreamUrl("file:///system/build.prop"))
        assertFalse(M3uPlaylistParser.isSafeAndValidStreamUrl("content://media/external/images"))
        assertFalse(M3uPlaylistParser.isSafeAndValidStreamUrl("javascript:alert(1)"))
        assertFalse(M3uPlaylistParser.isSafeAndValidStreamUrl("http://localhost:8080/live"))
        assertFalse(M3uPlaylistParser.isSafeAndValidStreamUrl("http://127.0.0.1/live.m3u8"))
        assertFalse(M3uPlaylistParser.isSafeAndValidStreamUrl("http://0.0.0.0:52026/"))

        // 家庭局域网 IPTV / UDPXY 代理流放行
        assertTrue(M3uPlaylistParser.isSafeAndValidStreamUrl("http://192.168.1.1:4022/udp/239.3.1.1:5140"))
        assertTrue(M3uPlaylistParser.isSafeAndValidStreamUrl("http://10.0.0.1:4022/udp/239.3.1.2:5140"))
        assertTrue(M3uPlaylistParser.isSafeAndValidStreamUrl("http://172.16.0.1:4022/udp/239.3.1.3:5140"))

        assertTrue(M3uPlaylistParser.isSafeAndValidStreamUrl("https://live.cctv.cn/live.m3u8"))
        assertTrue(M3uPlaylistParser.isSafeAndValidStreamUrl("http://stream.example.com/dash/channel.mpd"))
    }
}
