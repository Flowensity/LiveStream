package com.livestream.domain.channel.usecase

import com.livestream.core.model.EpgProgram
import com.livestream.domain.channel.repository.EpgRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * 获取频道即将播出的下一档节目单（支持随时间流逝自动推演刷新）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetNextProgramUseCase(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(
        channelId: String,
        currentTime: Long? = null,
        refreshIntervalMs: Long = 30_000L
    ): Flow<EpgProgram?> {
        if (currentTime != null) {
            return epgRepository.observeNextProgram(channelId, currentTime)
        }

        return flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(refreshIntervalMs)
            }
        }.flatMapLatest { now ->
            epgRepository.observeNextProgram(channelId, now)
        }.distinctUntilChanged()
    }
}
