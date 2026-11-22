package com.livestream.feature.settings

import android.graphics.Bitmap
import com.livestream.core.model.Channel
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.core.model.VideoAspectRatio

data class SettingsUiState(
    val isHardwareDecodePreferred: Boolean = true,
    val isSurfaceViewPreferred: Boolean = true,
    val isPipEnabled: Boolean = false,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    val channelSwitchTimeoutMs: Long = 5000L,
    val tvKeyMappingScheme: TvKeyMappingScheme = TvKeyMappingScheme.CLASSIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isSettingsOpen: Boolean = false,
    val isImportDialogOpen: Boolean = false,
    val isSourceManagementOpen: Boolean = false,
    val importText: String = "",
    val isImporting: Boolean = false,
    val refreshingSourceId: String? = null,
    val importSuccessCount: Int? = null,
    val importError: String? = null,
    val localServerUrl: String? = null,
    val qrBitmap: Bitmap? = null,
    val customSources: List<CustomPlaylistSource> = emptyList(),
    val customChannels: List<Channel> = emptyList(),
    val isProbingSources: Boolean = false,
    val unavailableCustomChannelIds: Set<String> = emptySet(),
    val sourceHealthSummary: String? = null
)
