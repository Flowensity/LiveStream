package com.livestream.core.ui.component

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.livestream.core.ui.theme.OneUiTheme

/**
 * 移动端触控与 Android TV 遥控器通用的焦点/按压交互容器组件。
 * 1. TV 遥控或键盘聚焦时：平滑放大 (默认 1.02x) + 强调色高光描边 + 背景过渡
 * 2. 手机/平板触控按压时：细腻弹性微缩 (0.97x) + 按压态高亮色彩反馈
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
    scaleOnFocus: Float = 1.02f,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rawFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isTv = rememberDeviceType() == DeviceType.TV
    val inputModeManager = LocalInputModeManager.current
    val isFocused = rawFocused && (isTv || inputModeManager.inputMode == InputMode.Keyboard)
    val isHighlighted = isFocused || isPressed

    val targetScale = when {
        isPressed -> 0.97f
        isFocused -> scaleOnFocus
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 130),
        label = "interactive_scale"
    )

    val currentBackground by animateColorAsState(
        targetValue = if (isHighlighted) focusedBackgroundColor else unfocusedBackgroundColor,
        animationSpec = tween(durationMillis = 130),
        label = "interactive_bg"
    )

    val borderStroke = when {
        isFocused -> BorderStroke(focusedBorderWidth, focusedBorderColor)
        isPressed && unfocusedBorderColor != null -> BorderStroke(unfocusedBorderWidth, focusedBorderColor.copy(alpha = 0.6f))
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
        content(isHighlighted)
    }
}
