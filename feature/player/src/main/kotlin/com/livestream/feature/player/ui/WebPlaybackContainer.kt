package com.livestream.feature.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.livestream.core.model.VideoAspectRatio
import com.livestream.playback.api.web.WebPlaybackController

/**
 * 官方合规网页播放容器：
 * 1. 独立沙箱生命周期管理，仅在官方要求网页播放时按需激活
 * 2. 屏幕旋转或布局重组时无缝迁移 WebView 图层，不重载流
 * 3. 退出或切换流时由 PlaybackOrchestrator 释放 WebView
 * 4. 上层覆盖透明交互图层，确保点击与手势能正常触发 OSD 与控制
 */
@Composable
fun WebPlaybackContainer(
    controller: WebPlaybackController?,
    url: String,
    modifier: Modifier = Modifier,
    overlayModifier: Modifier = Modifier,
    aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    onContainerClick: (() -> Unit)? = null
) {
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }
    val latestController by rememberUpdatedState(controller)
    val latestAspectRatio by rememberUpdatedState(aspectRatio)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    clipChildren = true
                    clipToPadding = true
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                            latestController?.setAspectRatio(latestAspectRatio)
                        }
                    }
                    containerRef = this
                }
            },
            update = { frameLayout ->
                containerRef = frameLayout
                if (controller != null && url.isNotBlank()) {
                    controller.setAspectRatio(aspectRatio)
                    controller.loadOfficialUrl(url, frameLayout)
                }
            }
        )

        // 透明手势与点击层：保证即使是网页流，用户点击屏幕也能正常响应呼出/隐藏 OSD
        val clickModifier = if (onContainerClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onContainerClick
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(clickModifier)
                .then(overlayModifier)
        )

        DisposableEffect(controller, url) {
            onDispose {
                controller?.detachContainer(containerRef)
            }
        }
    }
}
