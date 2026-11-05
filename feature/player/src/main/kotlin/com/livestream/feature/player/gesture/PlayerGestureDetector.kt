package com.livestream.feature.player.gesture

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 手机/平板播放器手势识别器：
 * 1. 单击切换 OSD
 * 2. 双击缩放/切台
 * 3. 屏幕左半屏纵向滑动调节屏幕亮度
 * 4. 屏幕右半屏纵向滑动调节系统音量
 */
fun Modifier.playerTouchGestures(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLeftVerticalDrag: (deltaY: Float) -> Unit,
    onRightVerticalDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { onDoubleTap() }
            )
        }
        .pointerInput(Unit) {
            var isLeft = false
            detectDragGestures(
                onDragStart = { offset ->
                    isLeft = offset.x < size.width / 2
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    if (isLeft) {
                        onLeftVerticalDrag(dragAmount.y)
                    } else {
                        onRightVerticalDrag(dragAmount.y)
                    }
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() }
            )
        }
}
