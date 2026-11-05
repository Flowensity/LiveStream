package com.livestream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.source.custom.parser.M3uPlaylistParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collectLatest { prefs ->
                _uiState.update { current ->
                    current.copy(
                        isHardwareDecodePreferred = prefs.isHardwareDecodePreferred,
                        isSurfaceViewPreferred = prefs.isSurfaceViewPreferred,
                        channelSwitchTimeoutMs = prefs.channelSwitchTimeoutMs,
                        tvKeyMappingScheme = prefs.tvKeyMappingScheme,
                        themeMode = prefs.themeMode
                    )
                }
            }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(isSettingsOpen = !it.isSettingsOpen) }
    }

    fun setHardwareDecodePreferred(preferred: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHardwareDecodePreferred(preferred)
        }
    }

    fun setSurfaceViewPreferred(preferred: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSurfaceViewPreferred(preferred)
        }
    }

    fun setChannelSwitchTimeoutMs(timeoutMs: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setSwitchChannelTimeoutMs(timeoutMs)
        }
    }

    fun setTvKeyMappingScheme(scheme: TvKeyMappingScheme) {
        viewModelScope.launch {
            userPreferencesRepository.setTvKeyMappingScheme(scheme)
        }
    }

    fun setThemeMode(mode: com.livestream.core.model.ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun openImportDialog() {
        _uiState.update {
            it.copy(
                isImportDialogOpen = true,
                importText = "",
                importSuccessCount = null,
                importError = null
            )
        }
    }

    fun closeImportDialog() {
        _uiState.update { it.copy(isImportDialogOpen = false) }
    }

    fun onImportTextChanged(text: String) {
        _uiState.update { it.copy(importText = text, importError = null) }
    }

    fun importM3uContent() {
        val content = _uiState.value.importText.trim()
        if (content.isEmpty()) {
            _uiState.update { it.copy(importError = "导入内容不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            try {
                val parsed = M3uPlaylistParser.parse(content)
                if (parsed.channels.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importError = "未能从播放列表中解析出合法频道或所有源均被安全策略拦截"
                        )
                    }
                    return@launch
                }

                channelRepository.saveChannels(parsed.channels)
                channelRepository.saveSources(parsed.sources)

                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importSuccessCount = parsed.channels.size
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importError = "解析导入失败: ${e.message}"
                    )
                }
            }
        }
    }
}
