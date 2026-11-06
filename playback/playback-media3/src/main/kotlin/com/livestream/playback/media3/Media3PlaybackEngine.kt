package com.livestream.playback.media3

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
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

@OptIn(UnstableApi::class)
@Singleton
class Media3PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlaybackEngine {

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    override val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _playbackMetrics = MutableStateFlow(PlaybackMetrics())
    override val playbackMetrics: StateFlow<PlaybackMetrics> = _playbackMetrics.asStateFlow()

    // 正常前台直播保留单例 ExoPlayer 实例，支持 release() 后按需安全重建
    private var _player: ExoPlayer? = null
    private val player: ExoPlayer
        get() = _player ?: createExoPlayer().also { _player = it }

    private var currentMediaItem: PlaybackMediaItem? = null
    private var prepareStartTimeMs = 0L
    private var attachedOutput: Any? = null
    private var hardwareDecodePreferred: Boolean = true

    private fun createExoPlayer(): ExoPlayer {
        // 1. 软硬解自适应策略：根据用户设置切换硬件 MediaCodec 优先或软件解码器优先，并开启安全回退
        val renderersFactory = DefaultRenderersFactory(context).apply {
            if (hardwareDecodePreferred) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            } else {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val defaultList = MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    // 软件解码优先排序：将 c2.android / OMX.google 或 softwareOnly 解码器排在前面，同时保留硬解兜底
                    defaultList.sortedByDescending { info ->
                        !info.hardwareAccelerated ||
                            info.name.startsWith("c2.android.", ignoreCase = true) ||
                            info.name.startsWith("OMX.google.", ignoreCase = true)
                    }
                }
            }
            setEnableDecoderFallback(true) // 解码器异常时允许自动安全回退
        }
        _playbackMetrics.value = _playbackMetrics.value.copy(
            isHardwareAccelerated = hardwareDecodePreferred
        )

        // 2. 结合低延迟直播特点的精细化缓冲控制（首帧极速起播优化）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 5000,
                /* bufferForPlaybackMs = */ 250, // 调优：由 500ms 降至 250ms，显著提升安卓 TV 首帧呈现出图速度
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. 定制 HTTP 数据源工厂（缩短握手超时至 3000ms，加速死源剔除并防止 IPv6 握手阻塞）
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(3000) // 调优：由 8000ms 降至 3000ms
            .setReadTimeoutMs(4000)    // 调优：由 8000ms 降至 4000ms
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
                addListener(PlayerEventListener())
                when (val output = attachedOutput) {
                    is SurfaceView -> setVideoSurfaceView(output)
                    is TextureView -> setVideoTextureView(output)
                }
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
            _player?.play()
        } catch (_: Throwable) {}
    }

    override fun pause() {
        try {
            _player?.pause()
        } catch (_: Throwable) {}
    }

    override fun stop() {
        try {
            _player?.stop()
        } catch (_: Throwable) {}
        _engineState.value = EngineState.Idle
    }

    override fun release() {
        try {
            _player?.release()
        } catch (_: Throwable) {}
        _player = null
        _engineState.value = EngineState.Idle
    }

    override fun setVolume(volume: Float) {
        try {
            player.volume = volume.coerceIn(0f, 1f)
        } catch (_: Throwable) {}
    }

    override fun attachSurface(surfaceView: Any?) {
        attachedOutput = surfaceView
        try {
            when (surfaceView) {
                is SurfaceView -> player.setVideoSurfaceView(surfaceView)
                is TextureView -> player.setVideoTextureView(surfaceView)
                null -> _player?.clearVideoSurface()
            }
        } catch (_: Throwable) {}
    }

    override fun detachSurface() {
        attachedOutput = null
        try {
            _player?.clearVideoSurface()
        } catch (_: Throwable) {}
    }

    override fun setHardwareDecodePreferred(preferred: Boolean) {
        if (this.hardwareDecodePreferred == preferred) return
        this.hardwareDecodePreferred = preferred
        _playbackMetrics.value = _playbackMetrics.value.copy(
            isHardwareAccelerated = preferred
        )
        val activePlayer = _player ?: return
        val wasPlayWhenReady = activePlayer.playWhenReady
        val mediaItemToRestore = currentMediaItem
        try {
            activePlayer.release()
        } catch (_: Throwable) {}
        _player = null
        if (mediaItemToRestore != null) {
            val newPlayer = player
            val mimeType = when (mediaItemToRestore.protocol) {
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
            val builder = MediaItem.Builder()
                .setUri(Uri.parse(mediaItemToRestore.uri))
                .setMediaId(mediaItemToRestore.sourceId)
                .setLiveConfiguration(liveConfig)
            if (mimeType != null) {
                builder.setMimeType(mimeType)
            }
            try {
                newPlayer.setMediaItem(builder.build())
                newPlayer.playWhenReady = wasPlayWhenReady
                newPlayer.prepare()
            } catch (_: Throwable) {}
        }
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
