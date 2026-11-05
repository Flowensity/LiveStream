package com.livestream.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livestream.core.ui.theme.AppSurface
import com.livestream.core.ui.theme.AppSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight

/**
 * Android TV 与触摸屏通用的焦点容器组件。
 * 当通过 D-pad 遥控器获取焦点时，自动触发：
 * 1. 尺度微放大 (1.04x) 提供远距离可见性
 * 2. 高对比度高亮边框 (FocusHighlight)
 * 3. 提升表面背景对比度
 */
@Composable
fun TvFocusableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedBorderWidth: Dp = 2.dp,
    focusedBorderColor: Color = FocusHighlight,
    unfocusedBackgroundColor: Color = AppSurface,
    focusedBackgroundColor: Color = AppSurfaceElevated,
    scaleOnFocus: Float = 1.04f,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_focus_scale"
    )

    val currentBackground = if (isFocused) focusedBackgroundColor else unfocusedBackgroundColor
    val borderStroke = if (isFocused) {
        BorderStroke(focusedBorderWidth, focusedBorderColor)
    } else {
        null
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier
            )
            .background(currentBackground, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        content(isFocused)
    }
}
