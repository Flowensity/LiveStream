package com.livestream.feature.settings

import android.graphics.Bitmap
import com.livestream.core.model.Channel
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.TvKeyMappingScheme

data class SettingsUiState(
    val isHardwareDecodePreferred: Boolean = true,
    val isSurfaceViewPreferred: Boolean = true,
    val channelSwitchTimeoutMs: Long = 5000L,
    val tvKeyMappingScheme: TvKeyMappingScheme = TvKeyMappingScheme.CLASSIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isBootStartupEnabled: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isImportDialogOpen: Boolean = false,
    val isSourceManagementOpen: Boolean = false,
    val importText: String = "",
    val isImporting: Boolean = false,
    val importSuccessCount: Int? = null,
    val importError: String? = null,
    val localServerUrl: String? = null,
    val qrBitmap: Bitmap? = null,
    val customSources: List<CustomPlaylistSource> = emptyList(),
    val customChannels: List<Channel> = emptyList()
)
