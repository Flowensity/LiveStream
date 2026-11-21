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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState
import kotlinx.coroutines.delay

/**
 * 导入 M3U 播放列表弹窗（支持局域网扫码推送、剪贴板一键粘贴与直接链接/文本导入）
 */
@Composable
internal fun M3uImportDialog(
    uiState: SettingsUiState,
    importCancelFocusRequester: FocusRequester,
    onCloseImportDialog: () -> Unit,
    onImportTextChanged: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onEnlargeQrCode: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        val importDialogShape = OneUiShapes.dialog

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(min = 310.dp, max = 396.dp)
                .fillMaxWidth(0.9f)
                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), importDialogShape)
                .background(OneUiTheme.colors.panelBackground, importDialogShape)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "导入 M3U 播放列表",
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "自动识别频道分组与 XMLTV 节目单",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Android TV 局域网扫码推送区域（带放大按钮，仅在 Android TV 模式下展示）
                if (isTv && uiState.qrBitmap != null && uiState.localServerUrl != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                            .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEnlargeQrCode() }
                        ) {
                            Image(
                                bitmap = uiState.qrBitmap.asImageBitmap(),
                                contentDescription = "局域网推送二维码",
                                modifier = Modifier
                                    .size(66.dp)
                                    .background(Color.White)
                                    .padding(3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "手机扫码推送与遥控",
                                color = OneUiTheme.colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.localServerUrl,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 2. M3U 订阅链接或内容直接输入框 + 粘贴/清空快捷操作
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTv) "或输入订阅链接 / M3U 内容" else "订阅链接或 M3U 内容",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TvFocusableBox(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text?.trim()
                                if (!clipText.isNullOrEmpty()) {
                                    onImportTextChanged(clipText)
                                }
                            },
                            shape = OneUiShapes.pill,
                            unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                            focusedBackgroundColor = OneUiTheme.colors.accent,
                            focusedBorderColor = OneUiTheme.colors.accent,
                            focusedBorderWidth = 1.dp
                        ) { isFocused ->
                            Text(
                                text = "粘贴",
                                color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        if (uiState.importText.isNotEmpty()) {
                            TvFocusableBox(
                                onClick = { onImportTextChanged("") },
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.danger,
                                focusedBorderColor = OneUiTheme.colors.danger,
                                focusedBorderWidth = 1.dp
                            ) { isFocused ->
                                Text(
                                    text = "清空",
                                    color = if (isFocused) Color.White else OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                val inputShape = OneUiShapes.cardMedium
                BasicTextField(
                    value = uiState.importText,
                    onValueChange = onImportTextChanged,
                    textStyle = TextStyle(
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    ),
                    cursorBrush = SolidColor(OneUiTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), inputShape)
                        .background(OneUiTheme.colors.cardBackground, inputShape)
                        .padding(10.dp),
                    decorationBox = { innerTextField ->
                        if (uiState.importText.isEmpty()) {
                            Text(
                                text = "输入 http(s):// 订阅链接，或直接粘贴 #EXTM3U 格式文本...",
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (uiState.importError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OneUiTheme.colors.dangerSoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = uiState.importError,
                            color = OneUiTheme.colors.danger,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (uiState.importSuccessCount != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OneUiTheme.colors.statusSuccess.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "已成功导入 ${uiState.importSuccessCount} 个频道",
                            color = OneUiTheme.colors.statusSuccess,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    TvFocusableBox(
                        onClick = { if (!uiState.isImporting) onConfirmImport() },
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = if (uiState.isImporting) OneUiTheme.colors.accent.copy(alpha = 0.55f) else OneUiTheme.colors.accent,
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.dp
                    ) { _ ->
                        Text(
                            text = if (uiState.isImporting) "正在导入..." else "确认导入",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
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
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "手机扫码遥控与推流",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "同一 Wi-Fi 下扫码即可连接；关闭此窗口后手机端仍可继续切台与推送播放列表",
                color = OneUiTheme.colors.textSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(10.dp),
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
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(18.dp))

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
                    text = "关闭二维码",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}
