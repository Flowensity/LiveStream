package com.livestream.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.device.DeviceType
import com.livestream.core.ui.device.rememberDeviceType
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import kotlinx.coroutines.delay

/**
 * 现代 One UI 规范二次退出确认弹窗 (312dp 居中毛玻璃卡片)
 */
@Composable
fun ExitConfirmDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    var isConfirmFocused by remember { mutableStateOf(false) }
    val isTv = rememberDeviceType() == DeviceType.TV

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        LaunchedEffect(Unit) {
            isConfirmFocused = false
            try {
                cancelFocusRequester.requestFocus()
            } catch (_: Exception) {
                try {
                    dialogFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            delay(80)
            try {
                if (isConfirmFocused) {
                    confirmFocusRequester.requestFocus()
                } else {
                    cancelFocusRequester.requestFocus()
                }
            } catch (_: Exception) {
                try {
                    dialogFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .focusRequester(dialogFocusRequester)
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionDown,
                        Key.DirectionUp,
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.Tab -> {
                            val nextConfirm = !isConfirmFocused
                            isConfirmFocused = nextConfirm
                            try {
                                if (nextConfirm) {
                                    confirmFocusRequester.requestFocus()
                                } else {
                                    cancelFocusRequester.requestFocus()
                                }
                            } catch (_: Exception) {}
                            true
                        }
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter -> {
                            if (isConfirmFocused) {
                                onConfirm()
                            } else {
                                onDismiss()
                            }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val panelShape = OneUiShapes.dialog
            val pillShape = OneUiShapes.pill
            val cancelSelected = isTv && !isConfirmFocused
            val confirmSelected = isTv && isConfirmFocused

            Box(
                modifier = Modifier
                    .width(OneUiDimensions.panelExitConfirmWidth)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), panelShape)
                    .background(OneUiTheme.colors.panelBackground, panelShape)
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "退出应用",
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "确定要退出 LiveStream 吗？",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. 继续观看按钮 (默认高亮聚焦)
                    TvFocusableBox(
                        onClick = onDismiss,
                        shape = pillShape,
                        unfocusedBackgroundColor = if (cancelSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.dp,
                        unfocusedBorderColor = if (cancelSelected) Color.White else null,
                        unfocusedBorderWidth = if (cancelSelected) 2.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .focusRequester(cancelFocusRequester)
                            .onFocusChanged { if (it.isFocused) isConfirmFocused = false }
                            .focusProperties {
                                down = confirmFocusRequester
                                up = confirmFocusRequester
                                left = confirmFocusRequester
                                right = confirmFocusRequester
                            }
                    ) { isFocused ->
                        val active = isFocused || cancelSelected
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "继续观看",
                                color = if (active) Color.White else OneUiTheme.colors.accent,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 退出应用按钮 (危险警示红)
                    TvFocusableBox(
                        onClick = onConfirm,
                        shape = pillShape,
                        unfocusedBackgroundColor = if (confirmSelected) OneUiTheme.colors.danger else OneUiTheme.colors.dangerSoft,
                        focusedBackgroundColor = OneUiTheme.colors.danger,
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.dp,
                        unfocusedBorderColor = if (confirmSelected) Color.White else null,
                        unfocusedBorderWidth = if (confirmSelected) 2.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .focusRequester(confirmFocusRequester)
                            .onFocusChanged { if (it.isFocused) isConfirmFocused = true }
                            .focusProperties {
                                up = cancelFocusRequester
                                down = cancelFocusRequester
                                left = cancelFocusRequester
                                right = cancelFocusRequester
                            }
                    ) { isFocused ->
                        val active = isFocused || confirmSelected
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "退出应用",
                                color = if (active) Color.White else OneUiTheme.colors.danger,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
