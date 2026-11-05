package com.livestream.domain.channel.usecase

import com.livestream.core.model.EpgProgram
import com.livestream.domain.channel.repository.EpgRepository
import kotlinx.coroutines.flow.Flow

/**
 * 获取频道即将播出的下一档节目单
 */
class GetNextProgramUseCase(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(channelId: String, currentTime: Long = System.currentTimeMillis()): Flow<EpgProgram?> {
        return epgRepository.observeNextProgram(channelId, currentTime)
    }
}
