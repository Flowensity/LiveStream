package com.livestream.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.livestream.core.model.TvKeyMappingScheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesDataStoreTest {

    private val dataStore = mockk<DataStore<Preferences>>()

    @Test
    fun `test default preferences emitted when empty`() = runTest {
        val emptyPrefs = preferencesOf()
        every { dataStore.data } returns flowOf(emptyPrefs)

        val preferencesDataStore = UserPreferencesDataStore(dataStore)

        preferencesDataStore.userPreferences.test {
            val item = awaitItem()
            assertEquals("cctv", item.defaultCategoryId)
            assertTrue(item.isHardwareDecodePreferred)
            assertTrue(item.isSurfaceViewPreferred)
            assertEquals(5000L, item.channelSwitchTimeoutMs)
            assertEquals(TvKeyMappingScheme.CLASSIC, item.tvKeyMappingScheme)
            awaitComplete()
        }
    }

    @Test
    fun `test populated preferences mapping`() = runTest {
        val customPrefs = preferencesOf(
            stringPreferencesKey("default_category_id") to "satellite",
            stringPreferencesKey("last_played_channel_id") to "cctv1",
            stringPreferencesKey("tv_key_mapping_scheme") to "MINIMAL"
        )
        every { dataStore.data } returns flowOf(customPrefs)

        val preferencesDataStore = UserPreferencesDataStore(dataStore)

        preferencesDataStore.userPreferences.test {
            val item = awaitItem()
            assertEquals("satellite", item.defaultCategoryId)
            assertEquals("cctv1", item.lastPlayedChannelId)
            assertEquals(TvKeyMappingScheme.MINIMAL, item.tvKeyMappingScheme)
            awaitComplete()
        }
    }
}
