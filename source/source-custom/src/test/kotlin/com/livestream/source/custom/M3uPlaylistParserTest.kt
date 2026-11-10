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
        assertEquals("https://live.example.com/cctv1.m3u8", src1.url)

        val src2 = parsed.sources[1]
        assertEquals(StreamProtocol.DASH, src2.protocol)
        assertEquals("https://live.example.com/cctv2.mpd", src2.url)

        val src3 = parsed.sources[2]
        assertEquals(StreamProtocol.PROGRESSIVE_HTTP, src3.protocol)
        assertEquals("https://live.example.com/local.mp4", src3.url)
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

    @Test
    fun `test x-tvg-url header extraction`() {
        val m3uWithEpg = """
            #EXTM3U x-tvg-url="https://epg.example.com/xmltv.xml.gz"
            #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1 综合
            https://live.example.com/cctv1.m3u8
        """.trimIndent()

        val parsed = M3uPlaylistParser.parse(m3uWithEpg)
        assertEquals("https://epg.example.com/xmltv.xml.gz", parsed.epgUrl)
        assertEquals(1, parsed.channels.size)
    }

    @Test
    fun `test zero-regex single-pass attribute scanner extracts all EXTINF fields and supports Reader streaming`() {
        val line = """#EXTINF:-1 tvg-id="cctv5plus" tvg-name="CCTV5+" tvg-logo="https://img.example.com/5p.png" group-title="央视体育",CCTV-5+ 体育赛事"""
        val attrs = M3uPlaylistParser.extractExtInfAttributes(line)

        assertEquals("cctv5plus", attrs.tvgId)
        assertEquals("CCTV5+", attrs.tvgName)
        assertEquals("https://img.example.com/5p.png", attrs.tvgLogo)
        assertEquals("央视体育", attrs.groupTitle)
        assertEquals("CCTV-5+ 体育赛事", attrs.channelName)

        val streamed = M3uPlaylistParser.parse(java.io.StringReader(sampleM3u))
        assertEquals(3, streamed.channels.size)
    }

    @Test
    fun `test duplicate channels in same group are aggregated into single channel with multiple sources and quality tags`() {
        val m3uWithDuplicates = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="https://example.com/cctv1.png" group-title="央视频道",CCTV-1 综合 [4K 50fps]
            https://live.example.com/cctv1_4k.m3u8
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" group-title="央视频道",CCTV-1 (1080P 电信)
            https://live.example.com/cctv1_1080p_ct.m3u8
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" group-title="央视频道",CCTV-1 高清 (移动)
            https://live.example.com/cctv1_hd_cm.m3u8
            #EXTINF:-1 tvg-id="cctv2" group-title="央视频道",CCTV-2 财经
            https://live.example.com/cctv2.m3u8
        """.trimIndent()

        val parsed = M3uPlaylistParser.parse(m3uWithDuplicates)
        // 验证聚合后仅有 2 个频道（CCTV-1 与 CCTV-2），而不是 4 个重复条目
        assertEquals(2, parsed.channels.size)

        val cctv1 = parsed.channels[0]
        assertEquals("cctv1", cctv1.epgSourceId)
        assertEquals("CCTV-1 综合", cctv1.name)
        val cctv1Sources = parsed.sources.filter { it.channelId == cctv1.id }
        assertEquals(3, cctv1Sources.size)

        // 验证文本命名不应赋予虚假分辨率加权，所有线路保持客观基准 priority=10，仅保留真实网络标签
        val src4k = cctv1Sources.first { it.url.contains("4k") }
        val src1080p = cctv1Sources.first { it.url.contains("1080p") }
        val srcHd = cctv1Sources.first { it.url.contains("hd") }

        assertEquals(10, src4k.priority)
        assertEquals(10, src1080p.priority)
        assertEquals(10, srcHd.priority)
        assertTrue(src1080p.name.contains("电信"))
        assertTrue(srcHd.name.contains("移动"))
    }

    @Test
    fun `test duplicate stream URLs for same channel are deduplicated`() {
        val m3uWithDuplicateUrls = """
            #EXTM3U
            #EXTINF:-1 tvg-name="湖南卫视" group-title="卫视",湖南卫视
            https://live.example.com/hunan.m3u8
            #EXTINF:-1 tvg-name="湖南卫视" group-title="卫视",湖南卫视 [高清]
            https://live.example.com/hunan.m3u8
        """.trimIndent()

        val parsed = M3uPlaylistParser.parse(m3uWithDuplicateUrls)
        assertEquals(1, parsed.channels.size)
        assertEquals(1, parsed.sources.size)
        assertEquals("https://live.example.com/hunan.m3u8", parsed.sources[0].url)
    }
}
