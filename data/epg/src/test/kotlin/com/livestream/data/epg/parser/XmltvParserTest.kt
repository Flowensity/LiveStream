package com.livestream.data.epg.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmltvParserTest {

    @Test
    fun `parse valid xmltv content returns programs`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tv>
              <channel id="cctv1">
                <display-name>CCTV-1 综合</display-name>
              </channel>
              <programme channel="cctv1" start="20261105080000 +0800" stop="20261105090000 +0800">
                <title lang="zh">朝闻天下</title>
                <desc lang="zh">大型早间新闻直播节目</desc>
              </programme>
              <programme channel="cctv1" start="20261105090000 +0800" stop="20261105120000 +0800">
                <title lang="zh">生活圈</title>
                <desc lang="zh">健康生活服务类节目</desc>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmltvParser.parse(xml)
        assertEquals(2, programs.size)

        val p1 = programs[0]
        assertEquals("cctv1", p1.channelId)
        assertEquals("朝闻天下", p1.title)
        assertEquals("大型早间新闻直播节目", p1.description)
        assertTrue(p1.startTimestamp > 0)
        assertTrue(p1.endTimestamp > p1.startTimestamp)

        val p2 = programs[1]
        assertEquals("生活圈", p2.title)
        assertEquals(p1.endTimestamp, p2.startTimestamp)
    }
}
