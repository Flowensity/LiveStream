package com.livestream.core.database

import com.livestream.core.database.entity.CategoryEntity
import com.livestream.core.database.entity.ChannelEntity
import com.livestream.core.database.entity.EpgProgramEntity
import com.livestream.core.database.entity.LiveSourceEntity
import com.livestream.core.database.entity.PlaybackHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityValidationTest {

    @Test
    fun `test category entity creation`() {
        val category = CategoryEntity(
            id = "cctv",
            name = "央视频道",
            group = "CCTV",
            orderIndex = 0
        )
        assertEquals("cctv", category.id)
        assertEquals("CCTV", category.group)
    }

    @Test
    fun `test channel entity default values`() {
        val channel = ChannelEntity(
            id = "cctv1",
            name = "CCTV-1 综合",
            channelNumber = 1,
            categoryId = "cctv"
        )
        assertFalse(channel.isFavorite)
        assertFalse(channel.isHidden)
        assertEquals(0, channel.orderIndex)
        assertTrue(channel.createdAt > 0L)
    }

    @Test
    fun `test live source entity independence from channel`() {
        val source = LiveSourceEntity(
            id = "src_1",
            channelId = "cctv1",
            name = "官方超清线路",
            url = "https://example.com/live/cctv1.m3u8",
            protocol = "HLS",
            adapterProviderId = "cctv_official",
            priority = 10,
            healthStatus = "HEALTHY"
        )
        assertEquals("src_1", source.id)
        assertEquals("cctv1", source.channelId)
        assertEquals(10, source.priority)
    }

    @Test
    fun `test epg program entity timestamps`() {
        val program = EpgProgramEntity(
            id = "epg_1",
            channelId = "cctv1",
            title = "朝闻天下",
            startTimestamp = 1700000000L,
            endTimestamp = 1700003600L
        )
        assertTrue(program.endTimestamp > program.startTimestamp)
    }

    @Test
    fun `test playback history entity`() {
        val history = PlaybackHistoryEntity(
            channelId = "cctv1",
            lastPlayedTimestamp = 1700000500L,
            lastSourceId = "src_1",
            durationWatchedMs = 120000L
        )
        assertEquals("cctv1", history.channelId)
        assertEquals(120000L, history.durationWatchedMs)
    }
}
