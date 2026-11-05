package com.livestream.feature.player

import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.model.Channel
import com.livestream.core.model.PlaybackStatus
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.usecase.GetCurrentProgramUseCase
import com.livestream.domain.channel.usecase.GetNextProgramUseCase
import com.livestream.domain.channel.usecase.GetProgramsForChannelUseCase
import com.livestream.domain.channel.usecase.RefreshEpgUseCase
import com.livestream.playback.api.PlaybackOrchestrator
import com.livestream.playback.api.web.WebPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackOrchestrator: PlaybackOrchestrator,
    private val channelRepository: ChannelRepository,
    private val getCurrentProgramUseCase: GetCurrentProgramUseCase,
    private val getNextProgramUseCase: GetNextProgramUseCase,
    private val getProgramsForChannelUseCase: GetProgramsForChannelUseCase,
    private val refreshEpgUseCase: RefreshEpgUseCase
) : ViewModel() {

    val webPlaybackController: WebPlaybackController?
        get() = playbackOrchestrator.webPlaybackController

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var osdDismissJob: Job? = null
    private var numberKeyJob: Job? = null
    private var epgJob: Job? = null
    private var currentObservedChannelId: String? = null

    init {
        observeOrchestratorState()
        scheduleOsdDismiss(5000L)
    }

    private fun observeOrchestratorState() {
        viewModelScope.launch {
            playbackOrchestrator.orchestratorState.collectLatest { state ->
                val isBuffering = state.status == PlaybackStatus.BUFFERING ||
                    state.status == PlaybackStatus.PREPARING ||
                    state.status == PlaybackStatus.RESOLVING ||
                    state.status == PlaybackStatus.SWITCHING

                val isPlaying = state.status == PlaybackStatus.PLAYING

                val channelId = state.currentChannel?.id
                if (channelId != currentObservedChannelId) {
                    currentObservedChannelId = channelId
                    epgJob?.cancel()
                    if (channelId != null) {
                        val chName = state.currentChannel?.name.orEmpty()
                        epgJob = viewModelScope.launch {
                            // 1. 异步触发网络拉取真实 CNTV 节目单入库（增加防抖，避免切台过程中高频触发网络拉取）
                            launch {
                                delay(300L)
                                refreshEpgUseCase(channelId, chName)
                            }
                            // 2. 观察当前节目
                            launch {
                                getCurrentProgramUseCase(channelId).collect { program ->
                                    _uiState.update { it.copy(currentProgram = program) }
                                }
                            }
                            // 3. 观察下一节目
                            launch {
                                getNextProgramUseCase(channelId).collect { program ->
                                    _uiState.update { it.copy(nextProgram = program) }
                                }
                            }
                            // 4. 观察当天完整节目单
                            launch {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                val startOfDay = cal.timeInMillis
                                val endOfDay = startOfDay + 24 * 60 * 60 * 1000L
                                getProgramsForChannelUseCase(channelId, startOfDay, endOfDay).collect { list ->
                                    _uiState.update { it.copy(dayPrograms = list) }
                                }
                            }
                        }
                    } else {
                        _uiState.update { it.copy(currentProgram = null, nextProgram = null, dayPrograms = emptyList()) }
                    }
                }

                _uiState.update { current ->
                    current.copy(
                        status = state.status,
                        isBuffering = isBuffering,
                        isPlaying = isPlaying,
                        activeChannel = state.currentChannel,
                        activeSource = state.currentSource,
                        activeSourceIndex = state.currentSourceIndex,
                        totalSources = state.totalSources,
                        metrics = state.metrics,
                        error = state.errorInfo,
                        isOfficialWeb = state.isOfficialWeb,
                        officialWebUrl = state.officialWebUrl
                    )
                }

                // If playback enters READY or PLAYING, ensure OSD dismiss schedule is active
                if (state.status == PlaybackStatus.PLAYING && _uiState.value.isOsdVisible) {
                    scheduleOsdDismiss(4000L)
                }
            }
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        playbackOrchestrator.attachOutput(surfaceView)
    }

    fun detachSurface() {
        playbackOrchestrator.detachOutput()
    }

    fun playChannel(channel: Channel) {
        showOsd(5000L)
        viewModelScope.launch {
            playbackOrchestrator.playChannel(channel)
        }
    }

    fun retry() {
        viewModelScope.launch {
            playbackOrchestrator.retry()
        }
    }

    fun switchNextSource() {
        showOsd(4000L)
        viewModelScope.launch {
            playbackOrchestrator.switchNextSource()
        }
    }

    fun showOsd(autoDismissMs: Long = 5000L) {
        _uiState.update { it.copy(isOsdVisible = true) }
        scheduleOsdDismiss(autoDismissMs)
    }

    fun hideOsd() {
        osdDismissJob?.cancel()
        _uiState.update { it.copy(isOsdVisible = false) }
    }

    fun toggleOsd() {
        if (_uiState.value.isOsdVisible) {
            hideOsd()
        } else {
            showOsd()
        }
    }

    fun toggleEpgSheet() {
        _uiState.update { it.copy(isEpgSheetVisible = !it.isEpgSheetVisible) }
    }

    fun showEpgSheet() {
        _uiState.update { it.copy(isEpgSheetVisible = true) }
    }

    fun hideEpgSheet() {
        _uiState.update { it.copy(isEpgSheetVisible = false) }
    }

    fun onNumberKeyInput(digit: Int, onChannelConfirmed: ((Channel) -> Unit)? = null) {
        val currentNumber = _uiState.value.quickChannelNumber + digit
        _uiState.update { it.copy(quickChannelNumber = currentNumber, isOsdVisible = true) }

        numberKeyJob?.cancel()
        numberKeyJob = viewModelScope.launch {
            delay(1200L)
            val channelNum = currentNumber.toIntOrNull()
            if (channelNum != null) {
                val channels = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
                val targetChannel = channels.find { it.channelNumber == channelNum }
                if (targetChannel != null) {
                    showOsd(5000L)
                    if (onChannelConfirmed != null) {
                        onChannelConfirmed(targetChannel)
                    } else {
                        playChannel(targetChannel)
                    }
                }
            }
            _uiState.update { it.copy(quickChannelNumber = "") }
        }
    }

    private fun scheduleOsdDismiss(delayMs: Long) {
        osdDismissJob?.cancel()
        osdDismissJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.update { it.copy(isOsdVisible = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        osdDismissJob?.cancel()
        numberKeyJob?.cancel()
    }
}
