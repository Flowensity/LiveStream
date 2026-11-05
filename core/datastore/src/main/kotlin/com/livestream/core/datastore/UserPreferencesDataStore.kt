package com.livestream.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferencesKeys {
        val DEFAULT_CATEGORY_ID = stringPreferencesKey("default_category_id")
        val LAST_PLAYED_CHANNEL_ID = stringPreferencesKey("last_played_channel_id")
        val HARDWARE_DECODE_PREFERRED = booleanPreferencesKey("hardware_decode_preferred")
        val SURFACE_VIEW_PREFERRED = booleanPreferencesKey("surface_view_preferred")
        val SWITCH_CHANNEL_TIMEOUT_MS = longPreferencesKey("switch_channel_timeout_ms")
        val TV_KEY_MAPPING_SCHEME = stringPreferencesKey("tv_key_mapping_scheme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BOOT_STARTUP_ENABLED = booleanPreferencesKey("boot_startup_enabled")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val defaultCategory = preferences[PreferencesKeys.DEFAULT_CATEGORY_ID] ?: "cctv"
            val lastChannel = preferences[PreferencesKeys.LAST_PLAYED_CHANNEL_ID]
            val hwDecode = preferences[PreferencesKeys.HARDWARE_DECODE_PREFERRED] ?: true
            val surfaceView = preferences[PreferencesKeys.SURFACE_VIEW_PREFERRED] ?: true
            val timeout = preferences[PreferencesKeys.SWITCH_CHANNEL_TIMEOUT_MS] ?: 5000L
            val mappingSchemeStr = preferences[PreferencesKeys.TV_KEY_MAPPING_SCHEME]
            val mappingScheme = when (mappingSchemeStr) {
                TvKeyMappingScheme.MINIMAL.name -> TvKeyMappingScheme.MINIMAL
                else -> TvKeyMappingScheme.CLASSIC
            }
            val themeModeStr = preferences[PreferencesKeys.THEME_MODE]
            val themeMode = when (themeModeStr) {
                com.livestream.core.model.ThemeMode.DARK.name -> com.livestream.core.model.ThemeMode.DARK
                com.livestream.core.model.ThemeMode.LIGHT.name -> com.livestream.core.model.ThemeMode.LIGHT
                else -> com.livestream.core.model.ThemeMode.SYSTEM
            }
            val bootStartup = preferences[PreferencesKeys.BOOT_STARTUP_ENABLED] ?: false

            UserPreferences(
                defaultCategoryId = defaultCategory,
                lastPlayedChannelId = lastChannel,
                isHardwareDecodePreferred = hwDecode,
                isSurfaceViewPreferred = surfaceView,
                channelSwitchTimeoutMs = timeout,
                tvKeyMappingScheme = mappingScheme,
                themeMode = themeMode,
                isBootStartupEnabled = bootStartup
            )
        }

    suspend fun setDefaultCategoryId(categoryId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_CATEGORY_ID] = categoryId
        }
    }

    suspend fun setLastPlayedChannelId(channelId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PLAYED_CHANNEL_ID] = channelId
        }
    }

    suspend fun setHardwareDecodePreferred(preferred: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HARDWARE_DECODE_PREFERRED] = preferred
        }
    }

    suspend fun setSurfaceViewPreferred(preferred: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SURFACE_VIEW_PREFERRED] = preferred
        }
    }

    suspend fun setSwitchChannelTimeoutMs(timeoutMs: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SWITCH_CHANNEL_TIMEOUT_MS] = timeoutMs
        }
    }

    suspend fun setTvKeyMappingScheme(scheme: TvKeyMappingScheme) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TV_KEY_MAPPING_SCHEME] = scheme.name
        }
    }

    suspend fun setThemeMode(mode: com.livestream.core.model.ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setBootStartupEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOOT_STARTUP_ENABLED] = enabled
        }
    }
}
