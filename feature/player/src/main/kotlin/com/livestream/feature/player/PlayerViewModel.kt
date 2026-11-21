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
import com.livestream.playback.api.model.PlaybackMetrics
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

    // 独立的高频播放监控指标流，避免码率/缓冲/延迟波动触发播放器主 UI 重组
    private val _playbackMetrics = MutableStateFlow(PlaybackMetrics())
    val playbackMetrics: StateFlow<PlaybackMetrics> = _playbackMetrics.asStateFlow()

    private var osdDismissJob: Job? = null
    private var numberKeyJob: Job? = null
    private var epgRefreshJob: Job? = null
    private var epgObserveJob: Job? = null
    private var currentObservedChannelId: String? = null
    private var isEpgPollingActive: Boolean = false

    init {
        observeOrchestratorState()
        scheduleOsdDismiss(5000L)
    }

    private fun startEpgObservation(channelId: String) {
        epgObserveJob?.cancel()
        epgObserveJob = viewModelScope.launch {
            launch {
                getCurrentProgramUseCase(channelId).collect { program ->
                    _uiState.update { it.copy(currentProgram = program) }
                }
            }
            launch {
                getNextProgramUseCase(channelId).collect { program ->
                    _uiState.update { it.copy(nextProgram = program) }
                }
            }
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
    }

    private fun observeOrchestratorState() {
        viewModelScope.launch {
            playbackOrchestrator.orchestratorState.collectLatest { state ->
                val isBuffering = state.status == PlaybackStatus.BUFFERING ||
                    state.status == PlaybackStatus.PREPARING ||
                    state.status == PlaybackStatus.RESOLVING ||
                    state.status == PlaybackStatus.SWITCHING

                val isPlaying = state.status == PlaybackStatus.PLAYING
                val shouldPollEpg = state.status != PlaybackStatus.PAUSED && state.status != PlaybackStatus.RELEASED

                val channelId = state.currentChannel?.id
                if (channelId != currentObservedChannelId) {
                    currentObservedChannelId = channelId
                    epgRefreshJob?.cancel()
                    epgObserveJob?.cancel()
                    isEpgPollingActive = false
                    if (channelId != null) {
                        val chName = state.currentChannel?.name.orEmpty()
                        _uiState.update { it.copy(isEpgLoading = true) }
                        epgRefreshJob = viewModelScope.launch {
                            try {
                                delay(300L)
                                refreshEpgUseCase(channelId, chName)
                            } finally {
                                if (currentObservedChannelId == channelId) {
                                    _uiState.update { it.copy(isEpgLoading = false) }
                                }
                            }
                        }
                        if (shouldPollEpg) {
                            isEpgPollingActive = true
                            startEpgObservation(channelId)
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                currentProgram = null,
                                nextProgram = null,
                                dayPrograms = emptyList(),
                                isEpgLoading = false
                            )
                        }
                    }
                } else if (channelId != null) {
                    if (shouldPollEpg && !isEpgPollingActive) {
                        isEpgPollingActive = true
                        startEpgObservation(channelId)
                    } else if (!shouldPollEpg && isEpgPollingActive) {
                        isEpgPollingActive = false
                        epgObserveJob?.cancel()
                    }
                }

                _playbackMetrics.value = state.metrics
                val prevStatus = _uiState.value.status

                _uiState.update { current ->
                    val layoutMetrics = if (
                        current.metrics.videoWidth != state.metrics.videoWidth ||
                        current.metrics.videoHeight != state.metrics.videoHeight ||
                        current.metrics.isHardwareAccelerated != state.metrics.isHardwareAccelerated ||
                        current.metrics.decoderName != state.metrics.decoderName
                    ) {
                        state.metrics
                    } else {
                        current.metrics
                    }
                    current.copy(
                        status = state.status,
                        isBuffering = isBuffering,
                        isPlaying = isPlaying,
                        activeChannel = state.currentChannel,
                        activeSource = state.currentSource,
                        activeSourceIndex = state.currentSourceIndex,
                        totalSources = state.totalSources,
                        availableSources = state.availableSources,
                        sourceLatenciesMs = state.sourceLatenciesMs,
                        metrics = layoutMetrics,
                        error = state.errorInfo,
                        isOfficialWeb = state.isOfficialWeb,
                        officialWebUrl = state.officialWebUrl
                    )
                }

                // 仅在首次进入 PLAYING 状态时重置 OSD 自动隐藏倒计时，防止高频指标发射打断定时器
                if (prevStatus != PlaybackStatus.PLAYING && state.status == PlaybackStatus.PLAYING && _uiState.value.isOsdVisible) {
                    scheduleOsdDismiss(4000L)
                }
            }
        }
    }

    fun attachSurface(surfaceView: Any) {
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

    fun selectSource(sourceIndex: Int) {
        showOsd(4000L)
        viewModelScope.launch {
            playbackOrchestrator.selectSource(sourceIndex)
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
