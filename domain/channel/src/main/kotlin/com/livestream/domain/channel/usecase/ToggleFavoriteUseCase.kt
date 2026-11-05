package com.livestream.domain.channel.usecase

import com.livestream.domain.channel.repository.ChannelRepository

/**
 * 收藏状态切换用例
 */
class ToggleFavoriteUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(channelId: String, isFavorite: Boolean) {
        channelRepository.toggleFavorite(channelId, isFavorite)
    }
}
