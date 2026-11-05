package com.livestream.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.model.Channel
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.playback.api.PlaybackOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun initStartupPlayback() {
        if (hasInitiatedStartupPlay) return
        hasInitiatedStartupPlay = true

        viewModelScope.launch {
            channelRepository.checkAndSeedDefaultChannels()
            val channels = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
            if (channels.isEmpty()) return@launch

            val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            val targetChannel = if (prefs?.lastPlayedChannelId != null) {
                channels.find { it.id == prefs.lastPlayedChannelId } ?: channels.first()
            } else {
                channels.first()
            }

            playChannel(targetChannel)
        }
    }

    fun playChannel(channel: Channel) {
        viewModelScope.launch {
            playbackOrchestrator.playChannel(channel)
            userPreferencesRepository.setLastPlayedChannelId(channel.id)
        }
    }

    fun switchNextChannel() {
        viewModelScope.launch {
            val channels = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
            if (channels.isEmpty()) return@launch

            val currentChannel = playbackOrchestrator.orchestratorState.value.currentChannel
            val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
            val nextIndex = if (currentIndex in 0 until channels.size - 1) {
                currentIndex + 1
            } else {
                0
            }
            playChannel(channels[nextIndex])
        }
    }

    fun switchPreviousChannel() {
        viewModelScope.launch {
            val channels = channelRepository.observeAllVisibleChannels().firstOrNull() ?: emptyList()
            if (channels.isEmpty()) return@launch

            val currentChannel = playbackOrchestrator.orchestratorState.value.currentChannel
            val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
            val prevIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                channels.size - 1
            }
            playChannel(channels[prevIndex])
        }
    }
}
