package com.livestream.feature.settings.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState
import kotlinx.coroutines.delay

/**
 * 内嵌导入 M3U 弹窗（支持局域网扫码推送与直接链接/文本导入）
 */
@Composable
internal fun M3uImportDialog(
    uiState: SettingsUiState,
    importCancelFocusRequester: FocusRequester,
    onCloseImportDialog: () -> Unit,
    onImportTextChanged: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onEnlargeQrCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        val importDialogShape = OneUiShapes.panel

        Box(
            modifier = Modifier
                .width(268.dp)
                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), importDialogShape)
                .background(OneUiTheme.colors.panelBackground, importDialogShape)
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "导入 M3U 列表",
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TvFocusableBox(
                        onClick = onCloseImportDialog,
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        focusedBorderWidth = 1.5.dp
                    ) { isFocused ->
                        Text(
                            text = "关闭",
                            color = if (isFocused) Color.White else OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Android TV 局域网扫码推送区域（带放大按钮）
                if (uiState.qrBitmap != null && uiState.localServerUrl != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                            .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onEnlargeQrCode() }
                        ) {
                            Image(
                                bitmap = uiState.qrBitmap.asImageBitmap(),
                                contentDescription = "局域网推送二维码",
                                modifier = Modifier
                                    .size(62.dp)
                                    .background(Color.White)
                                    .padding(2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "局域网扫码推流",
                                color = OneUiTheme.colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.localServerUrl,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // 二维码放大按钮 (TvFocusableBox)
                            TvFocusableBox(
                                onClick = onEnlargeQrCode,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.dp
                            ) { isFocused ->
                                Text(
                                    text = "放大二维码",
                                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 2. M3U 订阅链接或内容直接输入框
                Text(
                    text = "或粘贴链接 / M3U 文本：",
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val inputShape = OneUiShapes.cardMedium
                BasicTextField(
                    value = uiState.importText,
                    onValueChange = onImportTextChanged,
                    textStyle = TextStyle(
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 11.sp
                    ),
                    cursorBrush = SolidColor(OneUiTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), inputShape)
                        .background(OneUiTheme.colors.cardBackground, inputShape)
                        .padding(8.dp),
                    decorationBox = { innerTextField ->
                        if (uiState.importText.isEmpty()) {
                            Text(
                                text = "支持 http(s):// 地址、GitHub 订阅或 M3U 文本...",
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (uiState.importError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.importError,
                        color = OneUiTheme.colors.danger,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (uiState.importSuccessCount != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "成功导入 ${uiState.importSuccessCount} 个频道！",
                        color = OneUiTheme.colors.statusSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvFocusableBox(
                        onClick = onCloseImportDialog,
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                        focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        focusedBorderWidth = 1.5.dp,
                        modifier = Modifier.focusRequester(importCancelFocusRequester)
                    ) { isFocused ->
                        Text(
                            text = "取消",
                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TvFocusableBox(
                        onClick = { if (!uiState.isImporting) onConfirmImport() },
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = if (uiState.isImporting) OneUiTheme.colors.accent.copy(alpha = 0.5f) else OneUiTheme.colors.accent,
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.dp
                    ) { _ ->
                        Text(
                            text = if (uiState.isImporting) "导入中..." else "确认导入",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 二维码全屏居中超大放大弹窗 (支持电视遥控器一键关闭)
 */
@Composable
internal fun QrCodeEnlargedOverlay(
    qrBitmap: Bitmap,
    localServerUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qrCloseFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try {
            qrCloseFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable { onDismiss() }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Back ||
                     keyEvent.key == Key.Escape ||
                     keyEvent.key == Key.DirectionCenter ||
                     keyEvent.key == Key.Enter)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "手机扫码遥控与推流",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "扫码后可关闭此窗口，手机端保持后台常驻连接，随时切台与推送源",
                color = OneUiTheme.colors.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "放大二维码",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = localServerUrl ?: "",
                color = OneUiTheme.colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            TvFocusableBox(
                onClick = onDismiss,
                shape = OneUiShapes.pill,
                unfocusedBackgroundColor = Color(0x33FFFFFF),
                focusedBackgroundColor = OneUiTheme.colors.accent,
                focusedBorderColor = Color.White,
                focusedBorderWidth = 2.dp,
                modifier = Modifier.focusRequester(qrCloseFocusRequester)
            ) { _ ->
                Text(
                    text = "关闭放大",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 7.dp)
                )
            }
        }
    }
}
