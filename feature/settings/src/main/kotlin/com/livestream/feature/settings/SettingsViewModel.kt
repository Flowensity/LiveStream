package com.livestream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.Channel
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.LiveSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.TvKeyMappingScheme
import com.livestream.core.model.VideoAspectRatio
import com.livestream.domain.channel.repository.CategoryRepository
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.EpgRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.feature.settings.server.LocalImportServer
import com.livestream.feature.settings.server.WebChannelInfo
import com.livestream.feature.settings.server.WebConsoleActions
import com.livestream.playback.api.PlaybackOrchestrator
import com.livestream.playback.api.SwitchDirection
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
    private val categoryRepository: CategoryRepository,
    private val localImportServer: LocalImportServer,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val epgRepository: EpgRepository?,
    private val playbackOrchestrator: PlaybackOrchestrator?
) : ViewModel() {

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        channelRepository: ChannelRepository,
        categoryRepository: CategoryRepository,
        localImportServer: LocalImportServer,
        okHttpClient: OkHttpClient,
        dispatchers: AppDispatchers
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        channelRepository = channelRepository,
        categoryRepository = categoryRepository,
        localImportServer = localImportServer,
        okHttpClient = okHttpClient,
        dispatchers = dispatchers,
        epgRepository = null,
        playbackOrchestrator = null
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collectLatest { prefs ->
                _uiState.update { current ->
                    current.copy(
                        isHardwareDecodePreferred = prefs.isHardwareDecodePreferred,
                        isSurfaceViewPreferred = prefs.isSurfaceViewPreferred,
                        aspectRatio = prefs.aspectRatio,
                        channelSwitchTimeoutMs = prefs.channelSwitchTimeoutMs,
                        tvKeyMappingScheme = prefs.tvKeyMappingScheme,
                        themeMode = prefs.themeMode,
                        isBootStartupEnabled = prefs.isBootStartupEnabled
                    )
                }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.customPlaylistSources.collectLatest { sources ->
                _uiState.update { it.copy(customSources = sources) }
            }
        }

        viewModelScope.launch {
            channelRepository.observeCustomChannels().collectLatest { customChs ->
                _uiState.update { it.copy(customChannels = customChs) }
            }
        }

        ensureLocalServerStarted()
    }

    fun ensureLocalServerStarted() {
        localImportServer.registerActions(createWebConsoleActions())
        val serverUrl = localImportServer.start { rawInput ->
            importM3uInternal(rawInput)
        }
        if (!serverUrl.isNullOrBlank()) {
            val qr = _uiState.value.qrBitmap ?: try {
                LocalImportServer.generateQrCodeBitmap(serverUrl, 512)
            } catch (_: Exception) {
                null
            }
            _uiState.update {
                it.copy(
                    localServerUrl = serverUrl,
                    qrBitmap = qr
                )
            }
        }
    }

    fun openSettings() {
        ensureLocalServerStarted()
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun toggleSettings() {
        val nextOpen = !_uiState.value.isSettingsOpen
        if (nextOpen) {
            ensureLocalServerStarted()
        }
        _uiState.update { it.copy(isSettingsOpen = nextOpen) }
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

    fun setVideoAspectRatio(ratio: VideoAspectRatio) {
        viewModelScope.launch {
            userPreferencesRepository.setVideoAspectRatio(ratio)
        }
    }

    fun cycleVideoAspectRatio() {
        val next = _uiState.value.aspectRatio.next()
        setVideoAspectRatio(next)
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

    fun setBootStartupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBootStartupEnabled(enabled)
        }
    }

    fun openImportDialog() {
        ensureLocalServerStarted()
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
        // 保持局域网遥控与推流服务后台常驻运行，确保关闭二维码或导入弹窗后手机端依然可随时遥控电视
        _uiState.update {
            it.copy(
                isImportDialogOpen = false
            )
        }
    }

    fun onImportTextChanged(text: String) {
        _uiState.update { it.copy(importText = text, importError = null) }
    }

    private fun createWebConsoleActions(): WebConsoleActions {
        return object : WebConsoleActions {
            override suspend fun getChannels(): List<WebChannelInfo> {
                val activeId = playbackOrchestrator?.orchestratorState?.value?.currentChannel?.id
                return channelRepository.getAllChannelsSync()
                    .filter { !it.isHidden }
                    .map { ch ->
                        WebChannelInfo(
                            id = ch.id,
                            name = ch.name,
                            channelNumber = ch.channelNumber,
                            categoryId = ch.categoryId,
                            isFavorite = ch.isFavorite,
                            isPlaying = ch.id == activeId
                        )
                    }
            }

            override suspend fun playChannel(channelId: String): Boolean {
                val ch = channelRepository.getChannelById(channelId) ?: return false
                playbackOrchestrator?.playChannel(ch)
                userPreferencesRepository.setLastPlayedChannelId(ch.id)
                return true
            }

            override suspend fun toggleFavorite(channelId: String): Boolean {
                val ch = channelRepository.getChannelById(channelId) ?: return false
                channelRepository.toggleFavorite(ch.id, !ch.isFavorite)
                return true
            }

            override suspend fun executeRemoteCommand(command: String): Boolean {
                return when (command.uppercase(Locale.ROOT)) {
                    "PREV_CHANNEL" -> {
                        playbackOrchestrator?.switchChannel(SwitchDirection.PREVIOUS)
                        true
                    }
                    "NEXT_CHANNEL" -> {
                        playbackOrchestrator?.switchChannel(SwitchDirection.NEXT)
                        true
                    }
                    "SWITCH_SOURCE" -> {
                        playbackOrchestrator?.switchNextSource()
                        true
                    }
                    "RETRY" -> {
                        playbackOrchestrator?.retry()
                        true
                    }
                    "ASPECT_RATIO" -> {
                        cycleVideoAspectRatio()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    suspend fun importM3uInternal(
        input: String,
        existingSourceToReplace: CustomPlaylistSource? = null
    ): Result<Int> = withContext(dispatchers.io) {
        try {
            val trimmed = input.trim()
            var downloadUrl = trimmed
            if (downloadUrl.contains("github.com/") && downloadUrl.contains("/blob/")) {
                downloadUrl = downloadUrl.replace("github.com/", "raw.githubusercontent.com/").replace("/blob/", "/")
            }
            val content = if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                val req = Request.Builder().url(downloadUrl).build()
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

            // 若是刷新已有订阅源，先清理该源旧频道与关联源，避免残留失效记录
            if (existingSourceToReplace != null) {
                if (existingSourceToReplace.channelIds.isNotEmpty()) {
                    channelRepository.deleteChannelsByIds(existingSourceToReplace.channelIds)
                }
                if (existingSourceToReplace.matchedSourceIds.isNotEmpty()) {
                    channelRepository.deleteSourcesByIds(existingSourceToReplace.matchedSourceIds)
                }
            }

            if (parsed.categories.isNotEmpty()) {
                categoryRepository.saveCategories(parsed.categories)
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

            val sourceTitle = existingSourceToReplace?.name ?: when {
                downloadUrl.startsWith("http") -> {
                    val candidate = downloadUrl.substringAfterLast('/').substringBefore('?')
                    if (candidate.isNotBlank() && candidate.contains(".")) candidate else "网络订阅源"
                }
                else -> "文本导入源"
            }
            val effectiveEpgUrl = parsed.epgUrl ?: existingSourceToReplace?.epgUrl.orEmpty()
            val record = CustomPlaylistSource(
                id = existingSourceToReplace?.id ?: UUID.randomUUID().toString(),
                name = sourceTitle,
                url = if (downloadUrl.startsWith("http")) downloadUrl else existingSourceToReplace?.url.orEmpty(),
                channelCount = parsed.channels.size,
                channelIds = parsed.channels.map { it.id },
                matchedSourceIds = matchedSources.map { it.id },
                timestamp = System.currentTimeMillis(),
                epgUrl = effectiveEpgUrl
            )
            userPreferencesRepository.saveCustomPlaylistSource(record)

            // 若播放列表头部包含 x-tvg-url / url-tvg，后台异步拉取 XMLTV 节目单入库
            if (effectiveEpgUrl.isNotBlank() && epgRepository != null) {
                viewModelScope.launch(dispatchers.io) {
                    val allNow = channelRepository.getAllChannelsSync()
                    epgRepository.importXmltvFromUrl(effectiveEpgUrl, allNow)
                }
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    refreshingSourceId = null,
                    importSuccessCount = parsed.channels.size,
                    importError = null
                )
            }
            Result.success(parsed.channels.size)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isImporting = false,
                    refreshingSourceId = null,
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

    fun refreshCustomPlaylistSource(source: CustomPlaylistSource) {
        if (source.url.isBlank() || _uiState.value.refreshingSourceId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(refreshingSourceId = source.id, importError = null) }
            importM3uInternal(source.url, existingSourceToReplace = source)
        }
    }

    fun refreshAllCustomSources() {
        val networkSources = _uiState.value.customSources.filter { it.url.startsWith("http") }
        if (networkSources.isEmpty() || _uiState.value.refreshingSourceId != null) return
        viewModelScope.launch {
            for (src in networkSources) {
                _uiState.update { it.copy(refreshingSourceId = src.id) }
                importM3uInternal(src.url, existingSourceToReplace = src)
            }
            _uiState.update { it.copy(refreshingSourceId = null) }
        }
    }

    fun openSourceManagement() {
        _uiState.update { it.copy(isSourceManagementOpen = true) }
    }

    fun closeSourceManagement() {
        _uiState.update { it.copy(isSourceManagementOpen = false) }
    }

    fun deleteCustomPlaylistSource(source: CustomPlaylistSource) {
        viewModelScope.launch {
            if (source.channelIds.isNotEmpty()) {
                channelRepository.deleteChannelsByIds(source.channelIds)
            }
            if (source.matchedSourceIds.isNotEmpty()) {
                channelRepository.deleteSourcesByIds(source.matchedSourceIds)
            }
            userPreferencesRepository.removeCustomPlaylistSource(source.id)
        }
    }

    fun deleteSingleCustomChannel(channelId: String) {
        viewModelScope.launch {
            channelRepository.deleteChannelById(channelId)
        }
    }

    fun clearAllCustomSources() {
        viewModelScope.launch {
            channelRepository.deleteAllCustomChannelsAndSources()
            userPreferencesRepository.clearCustomPlaylistSources()
            _uiState.update { it.copy(importSuccessCount = null, importError = null) }
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
            val numMatch = CCTV_NUM_REGEX.find(normName)
            if (numMatch != null) {
                val cctvNum = numMatch.groupValues[1]
                existingChannels.firstOrNull {
                    val existingNorm = normalizeChannelName(it.name)
                    val exMatch = CCTV_NUM_REGEX.find(existingNorm)
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

    companion object {
        private val CCTV_NUM_REGEX = Regex("""CCTV(\d+\+?)""")
    }

    override fun onCleared() {
        super.onCleared()
        localImportServer.stop()
    }
}
