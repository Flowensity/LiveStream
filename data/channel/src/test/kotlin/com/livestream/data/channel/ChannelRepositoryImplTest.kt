package com.livestream.data.channel

import app.cash.turbine.test
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.database.dao.CategoryDao
import com.livestream.core.database.dao.ChannelDao
import com.livestream.core.database.dao.LiveSourceDao
import com.livestream.core.database.entity.ChannelEntity
import com.livestream.core.database.entity.LiveSourceEntity
import com.livestream.core.datastore.UserPreferencesDataStore
import com.livestream.data.channel.preset.PresetChannelDataProvider
import com.livestream.data.channel.repository.ChannelRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRepositoryImplTest {

    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val liveSourceDao = mockk<LiveSourceDao>(relaxed = true)
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val testDispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher get() = testDispatcher
        override val io: CoroutineDispatcher get() = testDispatcher
        override val default: CoroutineDispatcher get() = testDispatcher
    }

    private val sampleChannelEntity = ChannelEntity(
        id = "cctv1",
        name = "CCTV-1",
        channelNumber = 1,
        categoryId = "cctv"
    )

    @Test
    fun `test observeAllVisibleChannels maps entities properly`() = runTest(testDispatcher) {
        every { channelDao.observeAllVisibleChannels() } returns flowOf(listOf(sampleChannelEntity))

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)

        repo.observeAllVisibleChannels().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("cctv1", list.first().id)
            awaitComplete()
        }
    }

    @Test
    fun `test toggleFavorite updates channelDao`() = runTest(testDispatcher) {
        coEvery { channelDao.updateFavoriteStatus("cctv1", true, any()) } returns Unit

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        repo.toggleFavorite("cctv1", true)

        coVerify(exactly = 1) { channelDao.updateFavoriteStatus("cctv1", true, any()) }
    }

    @Test
    fun `test observeSourcesForChannel maps source entities`() = runTest(testDispatcher) {
        val sourceEntity = LiveSourceEntity(
            id = "s1",
            channelId = "cctv1",
            name = "线路1",
            url = "https://example.com/stream.m3u8",
            protocol = "HLS",
            adapterProviderId = "test_adapter"
        )
        every { liveSourceDao.observeSourcesByChannelId("cctv1") } returns flowOf(listOf(sourceEntity))

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)

        repo.observeSourcesForChannel("cctv1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("s1", list.first().id)
            awaitComplete()
        }
    }

    @Test
    fun `test checkAndSeedDefaultChannels seeds data when empty`() = runTest(testDispatcher) {
        coEvery { channelDao.getAllChannelsSync() } returns emptyList()

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        repo.checkAndSeedDefaultChannels()

        coVerify(exactly = 1) { categoryDao.insertCategories(any()) }
        coVerify(exactly = 1) { channelDao.insertChannels(any()) }
        coVerify(exactly = 1) { liveSourceDao.insertSources(any()) }
    }

    @Test
    fun `test getAllChannelsSync returns domain models`() = runTest(testDispatcher) {
        val channelEntity = ChannelEntity(
            id = "cctv1",
            name = "CCTV-1",
            channelNumber = 1,
            categoryId = "cctv"
        )
        coEvery { channelDao.getAllChannelsSync() } returns listOf(channelEntity)

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        val result = repo.getAllChannelsSync()

        assertEquals(1, result.size)
        assertEquals("cctv1", result.first().id)
        assertEquals("CCTV-1", result.first().name)
    }

    @Test
    fun `test checkAndSeedDefaultChannels preserves user favorites on existing channels`() = runTest(testDispatcher) {
        val existingCctv1 = ChannelEntity(
            id = "cctv_600001859",
            name = "CCTV-1 综合",
            channelNumber = 1,
            categoryId = "cctv",
            isFavorite = true,
            isHidden = false
        )
        coEvery { channelDao.getAllChannelsSync() } returns listOf(existingCctv1)

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        repo.checkAndSeedDefaultChannels()

        coVerify(exactly = 1) {
            channelDao.insertChannels(match { channels ->
                val cctv1 = channels.firstOrNull { it.id == "cctv_600001859" }
                val satellite = channels.firstOrNull { it.id == "weishi_600002309" }
                cctv1 != null && cctv1.isFavorite && satellite != null
            })
        }
    }

    @Test
    fun `test saveChannels batch inserts missing categories without N+1 queries and chunks large channel lists`() = runTest(testDispatcher) {
        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        val largeChannels = (1..1200).map { idx ->
            com.livestream.core.model.Channel(
                id = "ch_$idx",
                name = "频道 $idx",
                channelNumber = idx,
                categoryId = "cat_${idx % 5}"
            )
        }

        repo.saveChannels(largeChannels)

        // Should never perform N+1 getCategoryById queries
        coVerify(exactly = 0) { categoryDao.getCategoryById(any()) }
        coVerify(exactly = 1) { categoryDao.insertCategoriesIgnore(match { it.size == 5 }) }
        // 1200 channels chunked by 500 -> 3 batch inserts
        coVerify(exactly = 3) { channelDao.insertChannels(any()) }
    }

    @Test
    fun `test deleteChannelsByIds chunks large ID lists to avoid SQLite 999 variable limit`() = runTest(testDispatcher) {
        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        val ids = (1..1200).map { "ch_$it" }

        repo.deleteChannelsByIds(ids)

        coVerify(exactly = 3) { channelDao.deleteChannelsByIds(match { it.size <= 500 }) }
        coVerify(exactly = 3) { liveSourceDao.deleteSourcesByChannelIds(match { it.size <= 500 }) }
    }

    @Test
    fun `preset channels and sources exclude VIP CCTV channels`() {
        val removedVipIds = setOf(
            "cctv_600099637", // CCTV世界地理频道
            "cctv_600099660", // CCTV风云音乐频道
            "cctv_600099649", // CCTV兵器科技频道
            "cctv_600099636", // CCTV风云足球频道
            "cctv_600099659", // CCTV高尔夫·网球频道
            "cctv_600099650", // CCTV女性时尚频道
            "cctv_600099653", // CCTV央视文化精品频道
            "cctv_600099652", // CCTV央视台球频道
            "cctv_600099656", // CCTV电视指南频道
            "cctv_600099651"  // CCTV卫生健康频道
        )
        val removedVipNames = setOf(
            "CCTV世界地理频道",
            "CCTV风云音乐频道",
            "CCTV兵器科技频道",
            "CCTV风云足球频道",
            "CCTV高尔夫·网球频道",
            "CCTV女性时尚频道",
            "CCTV央视文化精品频道",
            "CCTV央视台球频道",
            "CCTV电视指南频道",
            "CCTV卫生健康频道"
        )

        val allChannels = PresetChannelDataProvider.allDefaultChannels
        val allSources = PresetChannelDataProvider.allDefaultSourceEntities

        assertTrue(allChannels.none { it.id in removedVipIds || it.name in removedVipNames })
        assertTrue(allSources.none { it.channelId in removedVipIds })
    }

    @Test
    fun `preset channels and sources exclude defunct satellite channels absent from Yangshipin`() {
        val removedSatelliteIds = setOf(
            "weishi_600190405", // 吉林卫视
            "weishi_600190400", // 陕西卫视
            "weishi_600190408", // 甘肃卫视
            "weishi_600190737", // 宁夏卫视
            "weishi_600190401", // 内蒙古卫视
            "weishi_600190402", // 云南卫视
            "weishi_600190407", // 山西卫视
            "weishi_600190406", // 青海卫视
            "weishi_600190403"  // 西藏卫视
        )
        val removedSatelliteNames = setOf(
            "吉林卫视",
            "陕西卫视",
            "甘肃卫视",
            "宁夏卫视",
            "内蒙古卫视",
            "云南卫视",
            "山西卫视",
            "青海卫视",
            "西藏卫视"
        )

        val allChannels = PresetChannelDataProvider.allDefaultChannels
        val allSources = PresetChannelDataProvider.allDefaultSourceEntities

        assertTrue(allChannels.none { it.id in removedSatelliteIds || it.name in removedSatelliteNames })
        assertTrue(allSources.none { it.channelId in removedSatelliteIds })
        assertEquals(54, allChannels.size)
        assertEquals((1..54).toList(), allChannels.map { it.channelNumber })
    }

    @Test
    fun `checkAndSeedDefaultChannels deletes obsolete preset channels from database`() = runTest(testDispatcher) {
        val obsoleteVipChannel = ChannelEntity(
            id = "cctv_600099637",
            name = "CCTV世界地理频道",
            channelNumber = 31,
            categoryId = "cctv"
        )
        val customUserChannel = ChannelEntity(
            id = "user_ch_1",
            name = "自建频道",
            channelNumber = 100,
            categoryId = "custom"
        )
        coEvery { channelDao.getAllChannelsSync() } returns listOf(obsoleteVipChannel, customUserChannel)

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        repo.checkAndSeedDefaultChannels()

        coVerify(exactly = 1) { channelDao.deleteChannelsByIds(listOf("cctv_600099637")) }
        coVerify(exactly = 1) { liveSourceDao.deleteSourcesByChannelIds(listOf("cctv_600099637")) }
    }

    @Test
    fun `checkAndSeedDefaultChannels reads existing channels before upserting categories`() = runTest(testDispatcher) {
        coEvery { channelDao.getAllChannelsSync() } returns emptyList()

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers)
        repo.checkAndSeedDefaultChannels()

        coVerifyOrder {
            channelDao.getAllChannelsSync()
            categoryDao.insertCategories(any())
            channelDao.insertChannels(any())
            liveSourceDao.insertSources(any())
        }
    }

    @Test
    fun `checkAndSeedDefaultChannels skips DB writes when preset version already seeded`() = runTest(testDispatcher) {
        val prefs = mockk<UserPreferencesDataStore>(relaxed = true)
        coEvery { prefs.getPresetSeedVersion() } returns PresetChannelDataProvider.PRESET_DATA_VERSION
        coEvery { channelDao.getChannelCount() } returns 63

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers, prefs)
        repo.checkAndSeedDefaultChannels()

        coVerify(exactly = 0) { categoryDao.insertCategories(any()) }
        coVerify(exactly = 0) { channelDao.insertChannels(any()) }
        coVerify(exactly = 0) { liveSourceDao.insertSources(any()) }
    }

    @Test
    fun `checkAndSeedDefaultChannels persists seed version after seeding`() = runTest(testDispatcher) {
        val prefs = mockk<UserPreferencesDataStore>(relaxed = true)
        coEvery { prefs.getPresetSeedVersion() } returns 0
        coEvery { channelDao.getAllChannelsSync() } returns emptyList()

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers, prefs)
        repo.checkAndSeedDefaultChannels()

        coVerify(exactly = 1) { prefs.setPresetSeedVersion(PresetChannelDataProvider.PRESET_DATA_VERSION) }
    }

    @Test
    fun `recordPlaybackHistory and observeRecentHistoryChannels preserve MRU order and exclude hidden channels`() = runTest(testDispatcher) {
        val historyDao = mockk<com.livestream.core.database.dao.PlaybackHistoryDao>(relaxed = true)
        val ch1 = ChannelEntity(id = "cctv1", name = "CCTV-1", channelNumber = 1, categoryId = "cctv")
        val ch2 = ChannelEntity(id = "cctv2", name = "CCTV-2", channelNumber = 2, categoryId = "cctv")
        val h2 = com.livestream.core.database.entity.PlaybackHistoryEntity("cctv2", 2000L, "src2")
        val hMissing = com.livestream.core.database.entity.PlaybackHistoryEntity("hidden_or_deleted", 1500L, "srcX")
        val h1 = com.livestream.core.database.entity.PlaybackHistoryEntity("cctv1", 1000L, "src1")

        every { historyDao.observeRecentHistory(any()) } returns flowOf(listOf(h2, hMissing, h1))
        every { channelDao.observeAllVisibleChannels() } returns flowOf(listOf(ch1, ch2))

        val repo = ChannelRepositoryImpl(channelDao, liveSourceDao, categoryDao, testDispatchers, null, historyDao)
        repo.recordPlaybackHistory("cctv2", "src2")
        coVerify(exactly = 1) {
            historyDao.recordPlayback(match { it.channelId == "cctv2" && it.lastSourceId == "src2" })
        }

        repo.observeRecentHistoryChannels(30).test {
            val recent = awaitItem()
            assertEquals(listOf("cctv2", "cctv1"), recent.map { it.id })
            awaitComplete()
        }
    }
}
