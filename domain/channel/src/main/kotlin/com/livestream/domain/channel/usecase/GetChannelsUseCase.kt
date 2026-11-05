package com.livestream.domain.channel.usecase

import com.livestream.core.model.Channel
import com.livestream.domain.channel.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow

/**
 * 获取可见频道用例（支持全量或按分类过滤）
 */
class GetChannelsUseCase(
    private val channelRepository: ChannelRepository
) {
    operator fun invoke(categoryId: String? = null): Flow<List<Channel>> {
        return if (categoryId.isNullOrBlank()) {
            channelRepository.observeAllVisibleChannels()
        } else {
            channelRepository.observeChannelsByCategory(categoryId)
        }
    }
}
