package com.livestream.data.channel

import com.livestream.core.database.entity.CategoryEntity
import com.livestream.core.database.entity.ChannelEntity
import com.livestream.core.database.entity.LiveSourceEntity
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceHealthStatus
import com.livestream.core.model.StreamProtocol
import com.livestream.data.channel.mapper.toDomain
import com.livestream.data.channel.mapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMapperTest {

    @Test
    fun `test category entity to domain and back`() {
        val entity = CategoryEntity("cctv", "央视频道", "CCTV", 1)
        val domain = entity.toDomain()

        assertEquals("cctv", domain.id)
        assertEquals("央视频道", domain.name)
        assertEquals(ChannelCategoryGroup.CCTV, domain.group)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `test channel entity to domain and back`() {
        val entity = ChannelEntity(
            id = "cctv1",
            name = "CCTV-1",
            channelNumber = 1,
            categoryId = "cctv",
            isFavorite = true,
            isHidden = false,
            orderIndex = 1
        )
        val domain = entity.toDomain()

        assertEquals("cctv1", domain.id)
        assertTrue(domain.isFavorite)

        val backToEntity = domain.toEntity()
        assertEquals(entity.id, backToEntity.id)
        assertEquals(entity.name, backToEntity.name)
        assertEquals(entity.isFavorite, backToEntity.isFavorite)
    }

    @Test
    fun `test live source entity to domain and back`() {
        val entity = LiveSourceEntity(
            id = "s1",
            channelId = "cctv1",
            name = "高清1",
            url = "https://example.com/live.m3u8",
            protocol = "HLS",
            adapterProviderId = "cctv",
            priority = 5,
            healthStatus = "HEALTHY"
        )
        val domain = entity.toDomain()

        assertEquals(StreamProtocol.HLS, domain.protocol)
        assertEquals(SourceHealthStatus.HEALTHY, domain.healthStatus)

        val backToEntity = domain.toEntity(rawUrl = "https://example.com/live.m3u8")
        assertEquals(entity.id, backToEntity.id)
        assertEquals(entity.url, backToEntity.url)
    }
}
