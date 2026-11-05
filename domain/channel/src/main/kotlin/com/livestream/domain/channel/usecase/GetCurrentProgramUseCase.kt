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
 * 获取频道当前正在播出的节目单（支持随时间流逝自动推演刷新）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetCurrentProgramUseCase(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(
        channelId: String,
        currentTime: Long? = null,
        refreshIntervalMs: Long = 30_000L
    ): Flow<EpgProgram?> {
        if (currentTime != null) {
            return epgRepository.observeCurrentProgram(channelId, currentTime)
        }

        // 默认按刷新间隔（默认 30 秒）推动时钟，让 Room Flow 能随真实时间推移自动切换到下一档节目
        return flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(refreshIntervalMs)
            }
        }.flatMapLatest { now ->
            epgRepository.observeCurrentProgram(channelId, now)
        }.distinctUntilChanged()
    }
}
