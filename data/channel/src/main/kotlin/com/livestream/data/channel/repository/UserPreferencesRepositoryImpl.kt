package com.livestream.data.channel.repository

import com.livestream.core.datastore.UserPreferencesDataStore
import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.core.model.UserPreferences
import com.livestream.domain.channel.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.userPreferences

    override suspend fun setDefaultCategoryId(categoryId: String) {
        dataStore.setDefaultCategoryId(categoryId)
    }

    override suspend fun setLastPlayedChannelId(channelId: String) {
        dataStore.setLastPlayedChannelId(channelId)
    }

    override suspend fun setHardwareDecodePreferred(preferred: Boolean) {
        dataStore.setHardwareDecodePreferred(preferred)
    }

    override suspend fun setSurfaceViewPreferred(preferred: Boolean) {
        dataStore.setSurfaceViewPreferred(preferred)
    }

    override suspend fun setSwitchChannelTimeoutMs(timeoutMs: Long) {
        dataStore.setSwitchChannelTimeoutMs(timeoutMs)
    }

    override suspend fun setTvKeyMappingScheme(scheme: TvKeyMappingScheme) {
        dataStore.setTvKeyMappingScheme(scheme)
    }

    override suspend fun setThemeMode(mode: com.livestream.core.model.ThemeMode) {
        dataStore.setThemeMode(mode)
    }

    override suspend fun setBootStartupEnabled(enabled: Boolean) {
        dataStore.setBootStartupEnabled(enabled)
    }
}
