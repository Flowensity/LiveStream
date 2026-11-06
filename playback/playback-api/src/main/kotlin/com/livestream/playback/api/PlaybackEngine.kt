package com.livestream.playback.api

import com.livestream.playback.api.model.EngineState
import com.livestream.playback.api.model.PlaybackMediaItem
import com.livestream.playback.api.model.PlaybackMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器引擎基础契约（独立于具体播放引擎实现如 Media3/ExoPlayer/IjkPlayer）
 */
interface PlaybackEngine {

    val engineState: StateFlow<EngineState>

    val playbackMetrics: StateFlow<PlaybackMetrics>

    suspend fun prepare(mediaItem: PlaybackMediaItem, playWhenReady: Boolean = true)

    fun play()

    fun pause()

    fun stop()

    fun release()

    fun setVolume(volume: Float)

    /**
     * 绑定渲染表面输出（SurfaceView 或 TextureView）
     */
    fun attachSurface(surfaceView: Any?)

    fun detachSurface()

    /**
     * 动态设置是否优先启用硬件加速解码（false 时优先软件解码器并支持回退）
     */
    fun setHardwareDecodePreferred(preferred: Boolean) {}
}
