package com.livestream.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme

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
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            try {
                cancelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            val panelShape = OneUiShapes.dialog
            val pillShape = OneUiShapes.pill

            Box(
                modifier = Modifier
                    .width(OneUiDimensions.panelExitConfirmWidth)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), panelShape)
                    .background(OneUiTheme.colors.panelBackground, panelShape)
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
                        unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        focusedBorderWidth = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .focusRequester(cancelFocusRequester)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "继续观看",
                                color = if (isFocused) Color.White else OneUiTheme.colors.accent,
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
                        unfocusedBackgroundColor = OneUiTheme.colors.dangerSoft,
                        focusedBackgroundColor = OneUiTheme.colors.danger,
                        focusedBorderColor = OneUiTheme.colors.danger,
                        focusedBorderWidth = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "退出应用",
                                color = if (isFocused) Color.White else OneUiTheme.colors.danger,
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
