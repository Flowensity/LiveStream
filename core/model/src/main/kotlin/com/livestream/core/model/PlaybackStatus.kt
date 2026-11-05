package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 播放调度器核心状态规范
 */
@Serializable
enum class PlaybackStatus {
    IDLE,
    RESOLVING,
    PREPARING,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    SWITCHING,
    ERROR,
    RELEASED
}

/**
 * 播放错误详情分类
 */
@Serializable
enum class PlaybackErrorType {
    NETWORK_TIMEOUT,
    NETWORK_DISCONNECTED,
    SOURCE_UNAVAILABLE,
    DRM_AUTHORIZATION_FAILED,
    DECODER_INIT_FAILED,
    RENDERER_CRASHED,
    UNKNOWN
}

@Serializable
data class PlaybackErrorInfo(
    val errorType: PlaybackErrorType,
    val message: String,
    val canRetry: Boolean = true,
    val retryCount: Int = 0
)
