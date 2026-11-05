package com.livestream.domain.channel.usecase

import com.livestream.domain.channel.repository.EpgRepository

class RefreshEpgUseCase(
    private val epgRepository: EpgRepository
) {
    suspend operator fun invoke(channelId: String, channelName: String) {
        epgRepository.refreshEpgForChannel(channelId, channelName)
    }
}
