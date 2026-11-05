package com.livestream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.feature.settings.server.LocalImportServer
import com.livestream.source.custom.parser.M3uPlaylistParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val channelRepository: ChannelRepository,
    private val localImportServer: LocalImportServer,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun openImportDialog() {
        val serverUrl = localImportServer.start { rawInput ->
            importM3uInternal(rawInput)
        }
        val qr = serverUrl?.let {
            try {
                LocalImportServer.generateQrCodeBitmap(it, 512)
            } catch (_: Exception) {
                null
            }
        }

        _uiState.update {
            it.copy(
                isImportDialogOpen = true,
                importText = "",
                importSuccessCount = null,
                importError = null,
                localServerUrl = serverUrl,
                qrBitmap = qr
            )
        }
    }

    fun closeImportDialog() {
        localImportServer.stop()
        _uiState.update {
            it.copy(
                isImportDialogOpen = false,
                localServerUrl = null,
                qrBitmap = null
            )
        }
    }

    fun onImportTextChanged(text: String) {
        _uiState.update { it.copy(importText = text, importError = null) }
    }

    suspend fun importM3uInternal(input: String): Result<Int> = withContext(dispatchers.io) {
        try {
            val trimmed = input.trim()
            val content = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val req = Request.Builder().url(trimmed).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("下载 M3U 订阅失败: HTTP ${resp.code}"))
                    }
                    resp.body?.string() ?: ""
                }
            } else {
                trimmed
            }

            if (content.isBlank()) {
                return@withContext Result.failure(Exception("导入内容不能为空"))
            }

            val parsed = M3uPlaylistParser.parse(content)
            if (parsed.channels.isEmpty()) {
                return@withContext Result.failure(Exception("未能从播放列表中解析出合法频道或所有源均被安全策略拦截"))
            }

            val existingChannels = channelRepository.getAllChannelsSync()
            val matchedSources = mutableListOf<LiveSource>()
            for (parsedCh in parsed.channels) {
                val matched = matchPresetChannel(parsedCh.name, existingChannels)
                if (matched != null) {
                    val chSources = parsed.sources.filter { it.channelId == parsedCh.id }
                    chSources.forEachIndexed { idx, src ->
                        matchedSources.add(
                            src.copy(
                                id = "matched_${matched.id}_${UUID.nameUUIDFromBytes("${src.id}_${matched.id}".toByteArray())}",
                                channelId = matched.id,
                                name = "网络线路 ${idx + 1}",
                                priority = 50
                            )
                        )
                    }
                }
            }

            channelRepository.saveChannels(parsed.channels)
            channelRepository.saveSources(parsed.sources + matchedSources)

            _uiState.update {
                it.copy(
                    isImporting = false,
                    importSuccessCount = parsed.channels.size,
                    importError = null
                )
            }
            Result.success(parsed.channels.size)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importError = "解析导入失败: ${e.message}"
                )
            }
            Result.failure(e)
        }
    }

    fun importM3uContent() {
        val content = _uiState.value.importText.trim()
        if (content.isEmpty()) {
            _uiState.update { it.copy(importError = "导入内容不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            importM3uInternal(content)
        }
    }

    private fun normalizeChannelName(raw: String): String {
        return raw.uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("HD", "")
            .replace("FHD", "")
            .replace("4K", "")
            .replace("8K", "")
            .replace("超高清", "")
            .replace("高清", "")
            .replace("标清", "")
            .replace("频道", "")
            .trim()
    }

    private fun matchPresetChannel(name: String, existingChannels: List<Channel>): Channel? {
        val normName = normalizeChannelName(name)
        if (normName.isEmpty()) return null

        // 1. Exact match on normalized
        existingChannels.firstOrNull { normalizeChannelName(it.name) == normName }?.let { return it }

        // 2. Special CCTV handling (e.g. CCTV1 matches CCTV1综合)
        if (normName.startsWith("CCTV")) {
            val numMatch = Regex("""CCTV(\d+\+?)""").find(normName)
            if (numMatch != null) {
                val cctvNum = numMatch.groupValues[1]
                existingChannels.firstOrNull {
                    val existingNorm = normalizeChannelName(it.name)
                    val exMatch = Regex("""CCTV(\d+\+?)""").find(existingNorm)
                    exMatch != null && exMatch.groupValues[1] == cctvNum
                }?.let { return it }
            }
        }

        // 3. Prefix / contains match (e.g. "湖南卫视" vs "湖南卫视-标清")
        existingChannels.firstOrNull {
            val exNorm = normalizeChannelName(it.name)
            exNorm.isNotEmpty() && (normName.startsWith(exNorm) || exNorm.startsWith(normName))
        }?.let { return it }

        return null
    }

    override fun onCleared() {
        super.onCleared()
        localImportServer.stop()
    }
}
