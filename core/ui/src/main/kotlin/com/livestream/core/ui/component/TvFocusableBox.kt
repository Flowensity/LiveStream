package com.livestream.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livestream.core.ui.device.DeviceType
import com.livestream.core.ui.device.rememberDeviceType
import com.livestream.core.ui.theme.AppSurface
import com.livestream.core.ui.theme.AppSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.OneUiTheme

/**
 * Samsung One UI 与 Android TV 通用的焦点/交互容器组件。
 * 当触控按压或遥控器获取焦点时：
 * 1. 尺度微放大 (1.03x) 平滑反馈
 * 2. 柔和 One UI 强调色边框
 * 3. 柔和圆角卡片过渡 (默认 14.dp)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    focusedBorderWidth: Dp = 2.dp,
    focusedBorderColor: Color = OneUiTheme.colors.accent,
    unfocusedBorderWidth: Dp = 1.dp,
    unfocusedBorderColor: Color? = null,
    unfocusedBackgroundColor: Color = OneUiTheme.colors.cardBackground,
    focusedBackgroundColor: Color = OneUiTheme.colors.cardSecondary,
    scaleOnFocus: Float = 1.03f,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rawFocused by interactionSource.collectIsFocusedAsState()
    val isTv = rememberDeviceType() == DeviceType.TV
    val inputModeManager = LocalInputModeManager.current
    val isFocused = rawFocused && (isTv || inputModeManager.inputMode == InputMode.Keyboard)

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_focus_scale"
    )

    val currentBackground = if (isFocused) focusedBackgroundColor else unfocusedBackgroundColor
    val borderStroke = when {
        isFocused -> BorderStroke(focusedBorderWidth, focusedBorderColor)
        unfocusedBorderColor != null -> BorderStroke(unfocusedBorderWidth, unfocusedBorderColor)
        else -> null
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier
            )
            .background(currentBackground, shape)
            .then(clickModifier)
    ) {
        content(isFocused)
    }
}
