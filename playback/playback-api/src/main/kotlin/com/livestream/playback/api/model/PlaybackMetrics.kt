package com.livestream.playback.api.model

/**
 * 实时播放性能与监控指标统计
 */
data class PlaybackMetrics(
    val bitrateBps: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val frameRate: Float = 0f,
    val droppedFrames: Long = 0L,
    val liveLatencyMs: Long = 0L,
    val bufferedDurationMs: Long = 0L,
    val decoderName: String? = null,
    val isHardwareAccelerated: Boolean = true,
    val firstFrameRenderedTimestampMs: Long = 0L,
    val switchDurationMs: Long = 0L,
    val networkResolutionMs: Long = 0L,
    val enginePrepareMs: Long = 0L,
    val decodeToFirstFrameMs: Long = 0L,
    val stallCount: Long = 0L
)
