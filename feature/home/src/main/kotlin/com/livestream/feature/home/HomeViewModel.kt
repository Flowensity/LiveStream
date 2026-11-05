package com.livestream.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.model.Channel
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.playback.api.PlaybackOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackOrchestrator: PlaybackOrchestrator
) : ViewModel() {

    private var hasInitiatedStartupPlay = false
    private var cachedChannels: List<Channel> = emptyList()
    private var targetChannelIndex: Int = -1
    private var switchChannelJob: Job? = null

    init {
        viewModelScope.launch {
            channelRepository.observeAllVisibleChannels().collectLatest { channels ->
                cachedChannels = channels
                val currentId = playbackOrchestrator.orchestratorState.value.currentChannel?.id
                if (currentId != null) {
                    val idx = channels.indexOfFirst { it.id == currentId }
                    if (idx >= 0) {
                        targetChannelIndex = idx
                    }
                }
            }
        }
    }

    fun initStartupPlayback() {
        if (hasInitiatedStartupPlay) return
        hasInitiatedStartupPlay = true

        viewModelScope.launch {
            channelRepository.checkAndSeedDefaultChannels()
            val channels = if (cachedChannels.isNotEmpty()) {
                cachedChannels
            } else {
                channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
            }
            if (channels.isEmpty()) return@launch
            cachedChannels = channels

            val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            val targetChannel = if (prefs?.lastPlayedChannelId != null) {
                channels.find { it.id == prefs.lastPlayedChannelId } ?: channels.first()
            } else {
                channels.first()
            }

            targetChannelIndex = channels.indexOfFirst { it.id == targetChannel.id }.coerceAtLeast(0)
            playChannel(targetChannel)
        }
    }

    fun playChannel(channel: Channel) {
        switchChannelJob?.cancel()
        if (cachedChannels.isNotEmpty()) {
            val idx = cachedChannels.indexOfFirst { it.id == channel.id }
            if (idx >= 0) {
                targetChannelIndex = idx
            }
        }
        viewModelScope.launch {
            playbackOrchestrator.playChannel(channel)
            userPreferencesRepository.setLastPlayedChannelId(channel.id)
        }
    }

    fun switchNextChannel() {
        viewModelScope.launch {
            val channels = if (cachedChannels.isNotEmpty()) {
                cachedChannels
            } else {
                val list = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
                cachedChannels = list
                list
            }
            if (channels.isEmpty()) return@launch

            if (targetChannelIndex < 0 || targetChannelIndex !in channels.indices) {
                val currentId = playbackOrchestrator.orchestratorState.value.currentChannel?.id
                targetChannelIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            }

            targetChannelIndex = (targetChannelIndex + 1) % channels.size
            val targetChannel = channels[targetChannelIndex]
            commitChannelSwitch(targetChannel)
        }
    }

    fun switchPreviousChannel() {
        viewModelScope.launch {
            val channels = if (cachedChannels.isNotEmpty()) {
                cachedChannels
            } else {
                val list = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
                cachedChannels = list
                list
            }
            if (channels.isEmpty()) return@launch

            if (targetChannelIndex < 0 || targetChannelIndex !in channels.indices) {
                val currentId = playbackOrchestrator.orchestratorState.value.currentChannel?.id
                targetChannelIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            }

            targetChannelIndex = if (targetChannelIndex <= 0) channels.size - 1 else targetChannelIndex - 1
            val targetChannel = channels[targetChannelIndex]
            commitChannelSwitch(targetChannel)
        }
    }

    private fun commitChannelSwitch(channel: Channel) {
        switchChannelJob?.cancel()
        switchChannelJob = viewModelScope.launch {
            // 1. 立即更新播放器调度层的预览频道（触发 OSD 状态实时展示）
            playbackOrchestrator.previewChannel(channel)

            // 2. 长按或连续切台防抖：延迟 250ms 后若无新按键才真正拉流与保存偏好
            delay(250L)

            // 3. 真正发起切台拉流
            playbackOrchestrator.playChannel(channel)

            // 4. 持久化记录最后播放频道
            userPreferencesRepository.setLastPlayedChannelId(channel.id)
        }
    }
}
