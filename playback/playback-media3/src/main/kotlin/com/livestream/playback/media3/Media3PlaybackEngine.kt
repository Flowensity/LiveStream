package com.livestream.playback.media3

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackErrorType
import com.livestream.core.model.StreamProtocol
import com.livestream.playback.api.PlaybackEngine
import com.livestream.playback.api.model.EngineState
import com.livestream.playback.api.model.PlaybackMediaItem
import com.livestream.playback.api.model.PlaybackMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlaybackEngine {

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    override val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _playbackMetrics = MutableStateFlow(PlaybackMetrics())
    override val playbackMetrics: StateFlow<PlaybackMetrics> = _playbackMetrics.asStateFlow()

    // 正常前台直播只保留单例 ExoPlayer 实例
    private val player: ExoPlayer by lazy {
        createExoPlayer()
    }

    private var currentMediaItem: PlaybackMediaItem? = null
    private var prepareStartTimeMs = 0L

    private fun createExoPlayer(): ExoPlayer {
        // 1. 硬件解码优先策略（严格禁止默认启用软件解码扩展，仅使用平台硬件 MediaCodec）
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            setEnableDecoderFallback(true) // 硬件解码器个别崩溃时允许内部安全回退
        }

        // 2. 结合低延迟直播特点的精细化缓冲控制
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 5000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. 定制 HTTP 数据源工厂（支持自定义 User-Agent 与请求头）
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                addListener(PlayerEventListener())
            }
    }

    override suspend fun prepare(mediaItem: PlaybackMediaItem, playWhenReady: Boolean) {
        currentMediaItem = mediaItem
        prepareStartTimeMs = System.currentTimeMillis()
        _engineState.value = EngineState.Preparing

        val mimeType = when (mediaItem.protocol) {
            StreamProtocol.HLS -> MimeTypes.APPLICATION_M3U8
            StreamProtocol.DASH -> MimeTypes.APPLICATION_MPD
            StreamProtocol.PROGRESSIVE_HTTP -> MimeTypes.VIDEO_MP4
            StreamProtocol.OFFICIAL_WEB -> null
        }

        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(3000)
            .setMinOffsetMs(1000)
            .setMaxOffsetMs(8000)
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(mediaItem.uri))
            .setMediaId(mediaItem.sourceId)
            .setLiveConfiguration(liveConfig)

        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }

        val builtItem = mediaItemBuilder.build()

        try {
            // 实例复用：直接 setMediaItem 而非销毁播放器重新创建
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(builtItem)
            player.playWhenReady = playWhenReady
            player.prepare()
        } catch (e: Throwable) {
            _engineState.value = EngineState.Error(
                PlaybackErrorInfo(
                    errorType = PlaybackErrorType.DECODER_INIT_FAILED,
                    message = "播放引擎准备异常: ${e.message}",
                    canRetry = true
                )
            )
        }
    }

    override fun play() {
        try {
            player.play()
        } catch (_: Throwable) {}
    }

    override fun pause() {
        try {
            player.pause()
        } catch (_: Throwable) {}
    }

    override fun stop() {
        try {
            player.stop()
        } catch (_: Throwable) {}
        _engineState.value = EngineState.Idle
    }

    override fun release() {
        try {
            player.release()
        } catch (_: Throwable) {}
        _engineState.value = EngineState.Idle
    }

    override fun setVolume(volume: Float) {
        try {
            player.volume = volume.coerceIn(0f, 1f)
        } catch (_: Throwable) {}
    }

    override fun attachSurface(surfaceView: Any?) {
        try {
            when (surfaceView) {
                is SurfaceView -> player.setVideoSurfaceView(surfaceView)
                is TextureView -> player.setVideoTextureView(surfaceView)
                null -> player.clearVideoSurface()
            }
        } catch (_: Throwable) {}
    }

    override fun detachSurface() {
        try {
            player.clearVideoSurface()
        } catch (_: Throwable) {}
    }

    private inner class PlayerEventListener : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    if (_engineState.value !is EngineState.Error) {
                        _engineState.value = EngineState.Idle
                    }
                }
                Player.STATE_BUFFERING -> {
                    _engineState.value = EngineState.Buffering
                }
                Player.STATE_READY -> {
                    _engineState.value = if (player.playWhenReady) {
                        EngineState.Playing
                    } else {
                        EngineState.Ready
                    }
                    updateBufferedMetrics()
                }
                Player.STATE_ENDED -> {
                    _engineState.value = EngineState.Ended
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                _engineState.value = EngineState.Playing
            } else if (player.playbackState == Player.STATE_READY) {
                _engineState.value = EngineState.Paused
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _playbackMetrics.value = _playbackMetrics.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height
            )
        }

        override fun onRenderedFirstFrame() {
            val now = System.currentTimeMillis()
            val switchDuration = if (prepareStartTimeMs > 0) now - prepareStartTimeMs else 0L
            _playbackMetrics.value = _playbackMetrics.value.copy(
                firstFrameRenderedTimestampMs = now,
                switchDurationMs = switchDuration
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorInfo = Media3ErrorClassifier.classify(error)
            _engineState.value = EngineState.Error(errorInfo)
        }
    }

    private fun updateBufferedMetrics() {
        val buffered = player.totalBufferedDuration
        val latency = if (player.isCurrentMediaItemLive) {
            player.currentLiveOffset.coerceAtLeast(0L)
        } else {
            0L
        }
        _playbackMetrics.value = _playbackMetrics.value.copy(
            bufferedDurationMs = buffered,
            liveLatencyMs = latency
        )
    }
}
