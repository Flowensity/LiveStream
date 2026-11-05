package com.livestream.domain.channel.usecase

import com.livestream.core.model.Channel
import com.livestream.domain.channel.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 频道搜索用例（按名称与台号模糊搜索）
 */
class SearchChannelsUseCase(
    private val channelRepository: ChannelRepository
) {
    operator fun invoke(query: String): Flow<List<Channel>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return flowOf(emptyList())
        }
        return channelRepository.searchChannels(trimmed)
    }
}
