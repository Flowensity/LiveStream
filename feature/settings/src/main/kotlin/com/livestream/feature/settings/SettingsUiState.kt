package com.livestream.feature.settings

import com.livestream.core.model.TvKeyMappingScheme

data class SettingsUiState(
    val isHardwareDecodePreferred: Boolean = true,
    val isSurfaceViewPreferred: Boolean = true,
    val channelSwitchTimeoutMs: Long = 5000L,
    val tvKeyMappingScheme: TvKeyMappingScheme = TvKeyMappingScheme.CLASSIC,
    val isSettingsOpen: Boolean = false,
    val isImportDialogOpen: Boolean = false,
    val importText: String = "",
    val isImporting: Boolean = false,
    val importSuccessCount: Int? = null,
    val importError: String? = null
)
