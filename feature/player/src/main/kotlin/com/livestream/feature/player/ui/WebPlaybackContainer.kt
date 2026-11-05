package com.livestream.feature.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.livestream.playback.api.web.WebPlaybackController

/**
 * 官方合规网页播放容器：
 * 1. 独立沙箱生命周期管理，仅在官方要求网页播放时按需激活
 * 2. 退出或切换流时自动触发 stopLoading、clearHistory 与 destroy 释放 WebView
 */
@Composable
fun WebPlaybackContainer(
    controller: WebPlaybackController?,
    url: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
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
                }
            },
            update = { frameLayout ->
                if (controller != null && url.isNotBlank()) {
                    controller.loadOfficialUrl(url, frameLayout)
                }
            }
        )

        DisposableEffect(controller, url) {
            onDispose {
                controller?.stopAndDestroy()
            }
        }
    }
}
