package com.livestream.domain.channel.repository

import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * 用户配置偏好仓库契约
 */
interface UserPreferencesRepository {

    val userPreferences: Flow<UserPreferences>

    suspend fun setDefaultCategoryId(categoryId: String)

    suspend fun setLastPlayedChannelId(channelId: String)

    suspend fun setHardwareDecodePreferred(preferred: Boolean)

    suspend fun setSurfaceViewPreferred(preferred: Boolean)

    suspend fun setSwitchChannelTimeoutMs(timeoutMs: Long)

    suspend fun setTvKeyMappingScheme(scheme: TvKeyMappingScheme)

    suspend fun setThemeMode(mode: com.livestream.core.model.ThemeMode)

    suspend fun setBootStartupEnabled(enabled: Boolean)

    suspend fun setVideoAspectRatio(ratio: com.livestream.core.model.VideoAspectRatio) {}

    val customPlaylistSources: Flow<List<com.livestream.core.model.CustomPlaylistSource>>

    suspend fun saveCustomPlaylistSource(source: com.livestream.core.model.CustomPlaylistSource)

    suspend fun removeCustomPlaylistSource(sourceId: String)

    suspend fun clearCustomPlaylistSources()
}
