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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.content.Context
import com.livestream.core.network.NetworkProbe
import com.livestream.core.network.ProbeResult
import com.livestream.core.ui.device.DeviceDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@HiltViewModel
class SettingsViewModel internal constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val localImportServer: LocalImportServer,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val epgRepository: EpgRepository?,
    private val playbackOrchestrator: PlaybackOrchestrator?,
    private val networkProbe: NetworkProbe? = null,
    val isTvDevice: Boolean
) : ViewModel() {

    @Inject
    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        channelRepository: ChannelRepository,
        categoryRepository: CategoryRepository,
        localImportServer: LocalImportServer,
        okHttpClient: OkHttpClient,
        dispatchers: AppDispatchers,
        epgRepository: EpgRepository?,
        playbackOrchestrator: PlaybackOrchestrator?,
        networkProbe: NetworkProbe?,
        @ApplicationContext context: Context
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        channelRepository = channelRepository,
        categoryRepository = categoryRepository,
        localImportServer = localImportServer,
        okHttpClient = okHttpClient,
        dispatchers = dispatchers,
        epgRepository = epgRepository,
        playbackOrchestrator = playbackOrchestrator,
        networkProbe = networkProbe,
        isTvDevice = DeviceDetector.isTv(context)
    )

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        channelRepository: ChannelRepository,
        categoryRepository: CategoryRepository,
        localImportServer: LocalImportServer,
        okHttpClient: OkHttpClient,
        dispatchers: AppDispatchers,
        epgRepository: EpgRepository?,
        playbackOrchestrator: PlaybackOrchestrator?
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        channelRepository = channelRepository,
        categoryRepository = categoryRepository,
        localImportServer = localImportServer,
        okHttpClient = okHttpClient,
        dispatchers = dispatchers,
        epgRepository = epgRepository,
        playbackOrchestrator = playbackOrchestrator,
        networkProbe = null,
        isTvDevice = true
    )

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
        playbackOrchestrator = null,
        isTvDevice = true
    )

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        channelRepository: ChannelRepository,
        categoryRepository: CategoryRepository,
        localImportServer: LocalImportServer,
        okHttpClient: OkHttpClient,
        dispatchers: AppDispatchers,
        isTv: Boolean
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        channelRepository = channelRepository,
        categoryRepository = categoryRepository,
        localImportServer = localImportServer,
        okHttpClient = okHttpClient,
        dispatchers = dispatchers,
        epgRepository = null,
        playbackOrchestrator = null,
        isTvDevice = isTv
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var serverStartJob: Job? = null
    private var importJob: Job? = null
    private var epgImportJob: Job? = null
    private var probeJob: Job? = null
    private val playlistMutationMutex = Mutex()
    private val mutationEpoch = AtomicLong(0L)
    @Volatile
    private var activeHttpCall: Call? = null

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collectLatest { prefs ->
                _uiState.update { current ->
                    current.copy(
                        isHardwareDecodePreferred = prefs.isHardwareDecodePreferred,
                        isSurfaceViewPreferred = prefs.isSurfaceViewPreferred,
                        isPipEnabled = prefs.isPipEnabled,
                        aspectRatio = prefs.aspectRatio,
                        channelSwitchTimeoutMs = prefs.channelSwitchTimeoutMs,
                        tvKeyMappingScheme = prefs.tvKeyMappingScheme,
                        themeMode = prefs.themeMode
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
        if (!isTvDevice) return
        if (serverStartJob?.isActive == true) return
        serverStartJob = viewModelScope.launch {
            val serverUrl = withContext(dispatchers.io) {
                localImportServer.registerActions(createWebConsoleActions())
                localImportServer.start { rawInput ->
                    importM3uInternal(rawInput)
                }
            }
            if (!serverUrl.isNullOrBlank()) {
                val existingQr = _uiState.value.qrBitmap
                val qr = existingQr ?: withContext(dispatchers.default) {
                    try {
                        LocalImportServer.generateQrCodeBitmap(serverUrl, 512)
                    } catch (_: Exception) {
                        null
                    }
                }
                _uiState.update {
                    it.copy(
                        localServerUrl = serverUrl,
                        qrBitmap = qr
                    )
                }
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

    fun setPipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPipEnabled(enabled)
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
        val startEpoch = mutationEpoch.get()
        try {
            val trimmed = input.trim()
            var downloadUrl = trimmed
            if (downloadUrl.contains("github.com/") && downloadUrl.contains("/blob/")) {
                downloadUrl = downloadUrl.replace("github.com/", "raw.githubusercontent.com/").replace("/blob/", "/")
            }
            val parsed = if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                if (!M3uPlaylistParser.isSafeAndValidStreamUrl(downloadUrl)) {
                    val err = Exception("订阅链接格式无效，或包含受限的本地地址")
                    _uiState.update {
                        it.copy(isImporting = false, refreshingSourceId = null, importError = err.message)
                    }
                    return@withContext Result.failure(err)
                }
                val req = Request.Builder().url(downloadUrl).build()
                val call = okHttpClient.newCall(req)
                activeHttpCall = call
                try {
                    call.execute().use { resp ->
                        val finalUrl = resp.request.url.toString()
                        if (!M3uPlaylistParser.isSafeAndValidStreamUrl(finalUrl)) {
                            val err = Exception("订阅链接跳转目标受安全限制，无法导入")
                            _uiState.update {
                                it.copy(isImporting = false, refreshingSourceId = null, importError = err.message)
                            }
                            return@withContext Result.failure(err)
                        }
                        if (!resp.isSuccessful) {
                            return@withContext Result.failure(Exception("下载播放列表失败 (HTTP ${resp.code})"))
                        }
                        val body = resp.body
                            ?: return@withContext Result.failure(Exception("请输入订阅链接或 M3U 播放列表内容"))
                        body.charStream().use { reader ->
                            M3uPlaylistParser.parse(reader)
                        }
                    }
                } finally {
                    if (activeHttpCall === call) {
                        activeHttpCall = null
                    }
                }
            } else {
                if (trimmed.isBlank()) {
                    return@withContext Result.failure(Exception("请输入订阅链接或 M3U 播放列表内容"))
                }
                M3uPlaylistParser.parse(trimmed)
            }

            coroutineContext.ensureActive()
            if (mutationEpoch.get() != startEpoch) {
                return@withContext Result.failure(CancellationException("导入已被清理操作取消"))
            }

            if (parsed.channels.isEmpty()) {
                return@withContext Result.failure(Exception("未解析到有效频道，请检查链接或 M3U 格式"))
            }

            var effectiveEpgUrl = ""
            playlistMutationMutex.withLock {
                coroutineContext.ensureActive()
                if (mutationEpoch.get() != startEpoch) {
                    return@withContext Result.failure(CancellationException("导入已被清理操作取消"))
                }

                // 若是刷新已有订阅源或重复导入同一订阅 URL，先清理该源独有的旧频道与关联源，避免残留失效记录
                val currentSources = _uiState.value.customSources
                val effectiveSourceToReplace = existingSourceToReplace ?: if (downloadUrl.startsWith("http")) {
                    currentSources.find { it.url == downloadUrl }
                } else {
                    null
                }
                val otherSources = if (effectiveSourceToReplace != null) {
                    currentSources.filterNot { it.id == effectiveSourceToReplace.id }
                } else {
                    currentSources
                }
                val retainedByOthersChannelIds = otherSources.flatMapTo(HashSet()) { it.channelIds }
                val retainedByOthersSourceIds = otherSources.flatMapTo(HashSet()) { it.matchedSourceIds }
                val replacedDeletedChannelIds = HashSet<String>()

                if (effectiveSourceToReplace != null) {
                    val channelsToDelete = effectiveSourceToReplace.channelIds.filterNot { it in retainedByOthersChannelIds }
                    if (channelsToDelete.isNotEmpty()) {
                        channelRepository.deleteChannelsByIds(channelsToDelete)
                        replacedDeletedChannelIds.addAll(channelsToDelete)
                    }
                    val sourcesToDelete = effectiveSourceToReplace.matchedSourceIds.filterNot { it in retainedByOthersSourceIds }
                    if (sourcesToDelete.isNotEmpty()) {
                        channelRepository.deleteSourcesByIds(sourcesToDelete)
                    }
                }

                val existingChannels = channelRepository.getAllChannelsSync()
                    .filterNot { it.id in replacedDeletedChannelIds }
                coroutineContext.ensureActive()
                if (mutationEpoch.get() != startEpoch) {
                    return@withContext Result.failure(CancellationException("导入已被清理操作取消"))
                }

                var nextCustomChannelNumber = ((existingChannels.maxOfOrNull { it.channelNumber } ?: 800).coerceAtLeast(800)) + 1
                // 仅与内置预设频道进行匹配聚合，严禁将已入库的自定义频道误判为内置频道
                val presetChannels = existingChannels.filterNot {
                    it.id.startsWith("user_ch_") || it.categoryId.startsWith("custom_")
                }
                val matchIndex = ChannelMatchIndex(presetChannels)
                val sourcesByChannelId = parsed.sources.groupBy { it.channelId }
                val matchedSources = mutableListOf<LiveSource>()
                val unmatchedChannels = mutableListOf<Channel>()
                val unmatchedSources = mutableListOf<LiveSource>()

                for (parsedCh in parsed.channels) {
                    val matched = matchIndex.match(parsedCh.name)
                    val chSources = sourcesByChannelId[parsedCh.id].orEmpty()
                    if (matched != null) {
                        chSources.forEachIndexed { idx, src ->
                            val lineLabel = if (src.name.contains("(")) {
                                "网络线路 ${idx + 1} (" + src.name.substringAfter("(")
                            } else {
                                "网络线路 ${idx + 1}"
                            }
                            matchedSources.add(
                                src.copy(
                                    id = "matched_${matched.id}_${UUID.nameUUIDFromBytes("${src.url}_${matched.id}".toByteArray())}",
                                    channelId = matched.id,
                                    name = lineLabel,
                                    priority = 50
                                )
                            )
                        }
                    } else {
                        unmatchedChannels.add(parsedCh.copy(channelNumber = nextCustomChannelNumber++))
                        unmatchedSources.addAll(chSources)
                    }
                }

                val usedCategoryIds = unmatchedChannels.mapTo(HashSet()) { it.categoryId }
                val categoriesToSave = parsed.categories.filter { it.id in usedCategoryIds }
                if (categoriesToSave.isNotEmpty()) {
                    categoryRepository.saveCategories(categoriesToSave)
                }

                if (unmatchedChannels.isNotEmpty()) {
                    channelRepository.saveChannels(unmatchedChannels)
                }
                val allSourcesToSave = unmatchedSources + matchedSources
                if (allSourcesToSave.isNotEmpty()) {
                    channelRepository.saveSources(allSourcesToSave)
                }

                val sourceTitle = effectiveSourceToReplace?.name ?: when {
                    downloadUrl.startsWith("http") -> {
                        val candidate = downloadUrl.substringAfterLast('/').substringBefore('?')
                        if (candidate.isNotBlank() && candidate.contains(".")) candidate else "网络订阅源"
                    }
                    else -> "文本导入源"
                }
                effectiveEpgUrl = parsed.epgUrl ?: effectiveSourceToReplace?.epgUrl.orEmpty()
                val record = CustomPlaylistSource(
                    id = effectiveSourceToReplace?.id ?: UUID.randomUUID().toString(),
                    name = sourceTitle,
                    url = if (downloadUrl.startsWith("http")) downloadUrl else effectiveSourceToReplace?.url.orEmpty(),
                    channelCount = parsed.channels.size,
                    channelIds = unmatchedChannels.map { it.id },
                    matchedSourceIds = matchedSources.map { it.id },
                    timestamp = System.currentTimeMillis(),
                    epgUrl = effectiveEpgUrl
                )
                userPreferencesRepository.saveCustomPlaylistSource(record)
                val updatedSources = listOf(record) + currentSources.filterNot {
                    it.id == record.id || (it.url.isNotBlank() && it.url == record.url)
                }
                _uiState.update {
                    it.copy(
                        customSources = updatedSources,
                        isImporting = false,
                        refreshingSourceId = null,
                        importSuccessCount = parsed.channels.size,
                        importError = null
                    )
                }
            }

            // 若播放列表头部包含 x-tvg-url / url-tvg，后台异步拉取 XMLTV 节目单入库
            if (effectiveEpgUrl.isNotBlank() && epgRepository != null && mutationEpoch.get() == startEpoch) {
                epgImportJob?.cancel()
                epgImportJob = viewModelScope.launch(dispatchers.io) {
                    val allNow = channelRepository.getAllChannelsSync()
                    epgRepository.importXmltvFromUrl(effectiveEpgUrl, allNow)
                }
            }

            Result.success(parsed.channels.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (mutationEpoch.get() == startEpoch) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        refreshingSourceId = null,
                        importError = "导入失败：${e.message}"
                    )
                }
            }
            Result.failure(e)
        }
    }

    fun importM3uContent() {
        val content = _uiState.value.importText.trim()
        if (content.isEmpty()) {
            _uiState.update { it.copy(importError = "请输入订阅链接或 M3U 播放列表内容") }
            return
        }

        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            importM3uInternal(content)
        }
    }

    fun refreshCustomPlaylistSource(source: CustomPlaylistSource) {
        if (source.url.isBlank() || _uiState.value.refreshingSourceId != null) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(refreshingSourceId = source.id, importError = null) }
            importM3uInternal(source.url, existingSourceToReplace = source)
        }
    }

    fun refreshAllCustomSources() {
        val networkSources = _uiState.value.customSources.filter { it.url.startsWith("http") }
        if (networkSources.isEmpty() || _uiState.value.refreshingSourceId != null) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
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
        mutationEpoch.incrementAndGet()
        activeHttpCall?.cancel()
        importJob?.cancel()
        epgImportJob?.cancel()
        probeJob?.cancel()
        viewModelScope.launch {
            playlistMutationMutex.withLock {
                val remainingSources = _uiState.value.customSources.filterNot { it.id == source.id }
                if (remainingSources.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            customSources = emptyList(),
                            isImporting = false,
                            refreshingSourceId = null,
                            isProbingSources = false,
                            importSuccessCount = null,
                            importError = null,
                            unavailableCustomChannelIds = emptySet(),
                            sourceHealthSummary = null
                        )
                    }
                    channelRepository.deleteAllCustomChannelsAndSources()
                    userPreferencesRepository.clearCustomPlaylistSources()
                    recoverPlaybackIfCurrentChannelDeleted()
                } else {
                    val retainedChannelIds = remainingSources.flatMapTo(HashSet()) { it.channelIds }
                    val retainedMatchedSourceIds = remainingSources.flatMapTo(HashSet()) { it.matchedSourceIds }
                    val channelsToDelete = source.channelIds.filterNot { it in retainedChannelIds }
                    val sourcesToDelete = source.matchedSourceIds.filterNot { it in retainedMatchedSourceIds }

                    _uiState.update {
                        it.copy(
                            customSources = remainingSources,
                            isImporting = false,
                            refreshingSourceId = null,
                            isProbingSources = false,
                            unavailableCustomChannelIds = it.unavailableCustomChannelIds - channelsToDelete.toSet()
                        )
                    }
                    if (channelsToDelete.isNotEmpty()) {
                        channelRepository.deleteChannelsByIds(channelsToDelete)
                    }
                    if (sourcesToDelete.isNotEmpty()) {
                        channelRepository.deleteSourcesByIds(sourcesToDelete)
                    }
                    userPreferencesRepository.removeCustomPlaylistSource(source.id)
                    recoverPlaybackIfCurrentChannelDeleted(channelsToDelete.toSet())
                }
            }
        }
    }

    fun deleteSingleCustomChannel(channelId: String) {
        viewModelScope.launch {
            playlistMutationMutex.withLock {
                channelRepository.deleteChannelById(channelId)
                syncCustomSourcesAfterChannelDeletion(setOf(channelId))
                recoverPlaybackIfCurrentChannelDeleted(setOf(channelId))
            }
        }
    }

    fun probeCustomSources() {
        val channels = _uiState.value.customChannels
        if (channels.isEmpty() || _uiState.value.isProbingSources) return

        probeJob?.cancel()
        probeJob = viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isProbingSources = true, sourceHealthSummary = "正在检测 ${channels.size} 个自定义频道的线路连通性...") }
            val probe = networkProbe ?: NetworkProbe(okHttpClient)
            val semaphore = Semaphore(6)
            val deadChannelIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

            coroutineScope {
                channels.map { ch ->
                    async {
                        semaphore.withPermit {
                            val sources = channelRepository.getSourcesForChannelSync(ch.id)
                            var hasAliveSource = false
                            for (src in sources) {
                                if (src.url.isBlank()) continue
                                when (probe.probeUrl(src.url, userAgent = src.userAgent, referer = src.referer)) {
                                    is ProbeResult.Success -> {
                                        hasAliveSource = true
                                        channelRepository.updateSourceHealth(
                                            src.id,
                                            com.livestream.core.model.SourceHealthStatus.HEALTHY
                                        )
                                    }
                                    is ProbeResult.Failure -> {
                                        channelRepository.updateSourceHealth(
                                            src.id,
                                            com.livestream.core.model.SourceHealthStatus.UNAVAILABLE
                                        )
                                    }
                                }
                            }
                            if (!hasAliveSource && sources.isNotEmpty()) {
                                deadChannelIds.add(ch.id)
                            }
                        }
                    }
                }.awaitAll()
            }

            val aliveCount = (channels.size - deadChannelIds.size).coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    isProbingSources = false,
                    unavailableCustomChannelIds = deadChannelIds.toSet(),
                    sourceHealthSummary = "检测完成：$aliveCount 个可用，${deadChannelIds.size} 个无法连接"
                )
            }
        }
    }

    fun removeUnavailableCustomChannels() {
        val deadIds = _uiState.value.unavailableCustomChannelIds
        if (deadIds.isEmpty()) return

        viewModelScope.launch {
            playlistMutationMutex.withLock {
                channelRepository.deleteChannelsByIds(deadIds.toList())
                syncCustomSourcesAfterChannelDeletion(deadIds)
                _uiState.update {
                    it.copy(
                        unavailableCustomChannelIds = emptySet(),
                        sourceHealthSummary = "已清理 ${deadIds.size} 个无法连接的频道"
                    )
                }
                recoverPlaybackIfCurrentChannelDeleted(deadIds)
            }
        }
    }

    private suspend fun syncCustomSourcesAfterChannelDeletion(deletedIds: Set<String>) {
        val updatedList = _uiState.value.customSources.map { source ->
            val removedCount = source.channelIds.count { it in deletedIds }
            if (removedCount > 0) {
                val updatedChannelIds = source.channelIds.filterNot { it in deletedIds }
                val updatedCount = (source.channelCount - removedCount).coerceAtLeast(updatedChannelIds.size)
                val updatedSource = source.copy(
                    channelIds = updatedChannelIds,
                    channelCount = updatedCount
                )
                userPreferencesRepository.saveCustomPlaylistSource(updatedSource)
                updatedSource
            } else {
                source
            }
        }
        _uiState.update { it.copy(customSources = updatedList) }
    }

    fun clearAllCustomSources() {
        mutationEpoch.incrementAndGet()
        activeHttpCall?.cancel()
        importJob?.cancel()
        epgImportJob?.cancel()
        probeJob?.cancel()
        viewModelScope.launch {
            playlistMutationMutex.withLock {
                _uiState.update {
                    it.copy(
                        customSources = emptyList(),
                        isImporting = false,
                        refreshingSourceId = null,
                        isProbingSources = false,
                        importSuccessCount = null,
                        importError = null,
                        unavailableCustomChannelIds = emptySet(),
                        sourceHealthSummary = null
                    )
                }
                channelRepository.deleteAllCustomChannelsAndSources()
                userPreferencesRepository.clearCustomPlaylistSources()
                recoverPlaybackIfCurrentChannelDeleted()
            }
        }
    }

    private suspend fun recoverPlaybackIfCurrentChannelDeleted(deletedChannelIds: Set<String>? = null) {
        val orchestrator = playbackOrchestrator ?: return
        val currentCh = orchestrator.orchestratorState.value.currentChannel ?: return
        val isDeleted = if (deletedChannelIds != null) {
            currentCh.id in deletedChannelIds
        } else {
            currentCh.id.startsWith("user_ch_") ||
                currentCh.categoryId.startsWith("custom_") ||
                channelRepository.getChannelById(currentCh.id) == null
        }
        if (isDeleted) {
            val fallback = channelRepository.getAllChannelsSync()
                .filter { !it.isHidden && (deletedChannelIds == null || it.id !in deletedChannelIds) }
                .minByOrNull { it.channelNumber }
            if (fallback != null) {
                orchestrator.playChannel(fallback)
                userPreferencesRepository.setLastPlayedChannelId(fallback.id)
            }
        }
    }

    /**
     * 预计算哈希索引，将频道名称匹配从 O(N × M) 降至 O(1) 哈希查找
     */
    class ChannelMatchIndex(existingChannels: List<Channel>) {
        private val exactMap = LinkedHashMap<String, Channel>(existingChannels.size)
        private val cctvNumMap = LinkedHashMap<String, Channel>()
        private val normalizedEntries = ArrayList<Pair<String, Channel>>(existingChannels.size)

        init {
            for (ch in existingChannels) {
                val norm = normalizeChannelName(ch.name)
                if (norm.isEmpty()) continue
                exactMap.putIfAbsent(norm, ch)
                normalizedEntries.add(norm to ch)
                if (norm.startsWith("CCTV")) {
                    val numMatch = CCTV_NUM_REGEX.find(norm)
                    if (numMatch != null) {
                        cctvNumMap.putIfAbsent(numMatch.groupValues[1], ch)
                    }
                }
            }
        }

        fun match(name: String): Channel? {
            val normName = normalizeChannelName(name)
            if (normName.isEmpty()) return null

            // 1. Exact match on normalized (O(1))
            exactMap[normName]?.let { return it }

            // 2. Special CCTV handling (O(1), e.g. CCTV1 matches CCTV1综合)
            if (normName.startsWith("CCTV")) {
                val numMatch = CCTV_NUM_REGEX.find(normName)
                if (numMatch != null) {
                    cctvNumMap[numMatch.groupValues[1]]?.let { return it }
                }
            }

            // 3. Prefix match on pre-normalized entries (no repeated string replacement/regex)
            for ((exNorm, channel) in normalizedEntries) {
                if (normName.startsWith(exNorm) || exNorm.startsWith(normName)) {
                    return channel
                }
            }

            return null
        }
    }

    companion object {
        private val CCTV_NUM_REGEX = Regex("""CCTV(\d+\+?)""")
        private val NOISE_TOKEN_REGEX = Regex("""(?:FHD|HD|4K|8K|超高清|高清|标清|频道|[\s\-_])+""")

        internal fun normalizeChannelName(raw: String): String {
            if (raw.isBlank()) return ""
            return NOISE_TOKEN_REGEX.replace(raw.uppercase(Locale.ROOT), "").trim()
        }
    }

    override fun onCleared() {
        super.onCleared()
        localImportServer.stop()
    }
}
