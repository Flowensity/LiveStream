package com.livestream.domain.channel.usecase

import com.livestream.core.model.EpgProgram
import com.livestream.domain.channel.repository.EpgRepository
import kotlinx.coroutines.flow.Flow

/**
 * 获取频道当前正在播出的节目单
 */
class GetCurrentProgramUseCase(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(channelId: String, currentTime: Long = System.currentTimeMillis()): Flow<EpgProgram?> {
        return epgRepository.observeCurrentProgram(channelId, currentTime)
    }
}
