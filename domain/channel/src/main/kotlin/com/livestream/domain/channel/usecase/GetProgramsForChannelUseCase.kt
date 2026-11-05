package com.livestream.domain.channel.usecase

import com.livestream.core.model.EpgProgram
import com.livestream.domain.channel.repository.EpgRepository
import kotlinx.coroutines.flow.Flow

/**
 * 获取频道指定时间范围内的全部节目单（用于节目时间轴展示）
 */
class GetProgramsForChannelUseCase(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(channelId: String, fromTime: Long, toTime: Long): Flow<List<EpgProgram>> {
        return epgRepository.observeProgramsForChannel(channelId, fromTime, toTime)
    }
}
