package com.livestream.feature.player.ui

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LiveVideoSurface(
    onSurfaceAttached: (SurfaceView) -> Unit,
    onSurfaceDetached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surfaceView = remember {
        SurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 确保视频层位于背景之上，但低于 UI 叠加层
            setZOrderMediaOverlay(false)
        }
    }

    DisposableEffect(surfaceView) {
        onSurfaceAttached(surfaceView)
        onDispose {
            onSurfaceDetached()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { surfaceView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
