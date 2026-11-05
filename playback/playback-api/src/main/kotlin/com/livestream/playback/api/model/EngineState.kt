package com.livestream.playback.api.model

import com.livestream.core.model.PlaybackErrorInfo

/**
 * 底层播放器引擎裸状态
 */
sealed interface EngineState {
    data object Idle : EngineState
    data object Preparing : EngineState
    data object Buffering : EngineState
    data object Ready : EngineState
    data object Playing : EngineState
    data object Paused : EngineState
    data object Ended : EngineState
    data class Error(val errorInfo: PlaybackErrorInfo) : EngineState
}
