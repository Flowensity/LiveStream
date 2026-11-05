package com.livestream.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.livestream.core.ui.theme.OneUiTheme

/**
 * Samsung One UI 风格二次退出确认弹窗：
 * 1. 采用 One UI 标志性大圆角 (26.dp)
 * 2. 区分主次胶囊按钮，默认聚焦在取消按钮防止误触
 * 3. 完美适配浅色与深色主题
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
            cancelFocusRequester.requestFocus()
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
            val dialogShape = RoundedCornerShape(26.dp)
            val pillShape = RoundedCornerShape(50)

            Box(
                modifier = Modifier
                    .width(360.dp)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.divider), dialogShape)
                    .background(OneUiTheme.colors.cardBackground, dialogShape)
                    .padding(26.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "退出应用",
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "确定要退出 LiveStream 电视直播应用吗？",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 取消按钮（次级操作，默认高亮聚焦）
                        TvFocusableBox(
                            onClick = onDismiss,
                            shape = pillShape,
                            unfocusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                            focusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                            focusedBorderColor = OneUiTheme.colors.accent,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(cancelFocusRequester)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "取消",
                                    color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // 确认退出按钮（主要操作，柔和红色胶囊）
                        TvFocusableBox(
                            onClick = onConfirm,
                            shape = pillShape,
                            unfocusedBackgroundColor = OneUiTheme.colors.statusError,
                            focusedBackgroundColor = OneUiTheme.colors.statusError,
                            focusedBorderColor = OneUiTheme.colors.accent,
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "退出",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
