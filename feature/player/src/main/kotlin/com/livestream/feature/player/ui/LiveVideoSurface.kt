package com.livestream.feature.player.ui

import android.graphics.Rect
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.livestream.core.model.VideoAspectRatio
import kotlin.math.abs

@Composable
fun LiveVideoSurface(
    onSurfaceAttached: (View) -> Unit,
    onSurfaceDetached: () -> Unit,
    modifier: Modifier = Modifier,
    isSurfaceViewPreferred: Boolean = true,
    aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    videoWidth: Int = 0,
    videoHeight: Int = 0
) {
    val context = LocalContext.current
    val latestAspectRatio by rememberUpdatedState(aspectRatio)
    val latestVideoWidth by rememberUpdatedState(videoWidth)
    val latestVideoHeight by rememberUpdatedState(videoHeight)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        key(isSurfaceViewPreferred) {
            val videoView = remember(isSurfaceViewPreferred) {
                if (isSurfaceViewPreferred) {
                    SurfaceView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                        keepScreenOn = true
                        setZOrderMediaOverlay(false)
                    }
                } else {
                    TextureView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                        keepScreenOn = true
                    }
                }
            }

            DisposableEffect(videoView) {
                onSurfaceAttached(videoView)
                onDispose {
                    onSurfaceDetached()
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        clipChildren = true
                        clipToPadding = true
                        (videoView.parent as? ViewGroup)?.removeView(videoView)
                        addView(videoView)
                        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                                applyNativeVideoAspectRatio(
                                    container = this,
                                    videoView = videoView,
                                    aspectRatio = latestAspectRatio,
                                    videoWidth = latestVideoWidth,
                                    videoHeight = latestVideoHeight
                                )
                            }
                        }
                    }
                },
                update = { container ->
                    applyNativeVideoAspectRatio(
                        container = container,
                        videoView = videoView,
                        aspectRatio = aspectRatio,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight
                    )
                }
            )
        }
    }
}

private fun applyNativeVideoAspectRatio(
    container: FrameLayout,
    videoView: View,
    aspectRatio: VideoAspectRatio,
    videoWidth: Int,
    videoHeight: Int
) {
    fun updateLayout() {
        val parentW = container.width
        val parentH = container.height
        if (parentW <= 0 || parentH <= 0) return

        val containerRatio = parentW.toFloat() / parentH.toFloat()
        val naturalRatio = if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.5f, 3.0f)
        } else {
            16f / 9f
        }

        val (targetW, targetH) = when (aspectRatio) {
            VideoAspectRatio.ORIGINAL -> {
                if (containerRatio > naturalRatio) {
                    (parentH * naturalRatio).toInt() to parentH
                } else {
                    parentW to (parentW / naturalRatio).toInt()
                }
            }
            VideoAspectRatio.SIXTEEN_NINE -> {
                val ratio169 = 16f / 9f
                if (containerRatio > ratio169) {
                    (parentH * ratio169).toInt() to parentH
                } else {
                    parentW to (parentW / ratio169).toInt()
                }
            }
            VideoAspectRatio.FOUR_THREE -> {
                val ratio43 = 4f / 3f
                if (containerRatio > ratio43) {
                    (parentH * ratio43).toInt() to parentH
                } else {
                    parentW to (parentW / ratio43).toInt()
                }
            }
            VideoAspectRatio.ZOOM_FILL -> {
                val (coverW, coverH) = if (containerRatio > naturalRatio) {
                    parentW to (parentW / naturalRatio).toInt()
                } else {
                    (parentH * naturalRatio).toInt() to parentH
                }
                val zoom = if (abs(containerRatio - naturalRatio) < 0.08f) 1.22f else 1.08f
                (coverW * zoom).toInt() to (coverH * zoom).toInt()
            }
        }

        val lp = (videoView.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(targetW, targetH, Gravity.CENTER)
        if (lp.width != targetW || lp.height != targetH || lp.gravity != Gravity.CENTER) {
            lp.width = targetW
            lp.height = targetH
            lp.gravity = Gravity.CENTER
            videoView.layoutParams = lp
        }

        val dx = (targetW - parentW).coerceAtLeast(0) / 2
        val dy = (targetH - parentH).coerceAtLeast(0) / 2
        videoView.clipBounds = if (dx > 0 || dy > 0) {
            Rect(dx, dy, targetW - dx, targetH - dy)
        } else {
            null
        }
        videoView.requestLayout()
    }

    if (container.width > 0 && container.height > 0) {
        updateLayout()
    } else {
        container.post { updateLayout() }
    }
}
