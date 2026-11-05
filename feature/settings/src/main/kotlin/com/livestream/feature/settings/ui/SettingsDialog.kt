package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState

/**
 * 规范系统与偏好设置右侧抽屉面板 (SettingsDrawerView, 居右对齐，与频道列表 ChannelDrawer 风格高度统一)
 */
@Composable
fun SettingsDialog(
    uiState: SettingsUiState,
    onCloseSettings: () -> Unit,
    onToggleHardwareDecode: (Boolean) -> Unit,
    onToggleSurfaceView: (Boolean) -> Unit,
    onOpenImportDialog: () -> Unit,
    onCloseImportDialog: () -> Unit,
    onImportTextChanged: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onToggleBootStartup: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!uiState.isSettingsOpen && !uiState.isImportDialogOpen) return

    val firstFocusRequester = remember { FocusRequester() }
    val importCancelFocusRequester = remember { FocusRequester() }
    var isQrEnlarged by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSettingsOpen, uiState.isImportDialogOpen) {
        if (uiState.isImportDialogOpen) {
            kotlinx.coroutines.delay(150)
            try {
                importCancelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (uiState.isSettingsOpen) {
            firstFocusRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 半透明背景遮罩（点击可关闭设置）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (uiState.isImportDialogOpen) {
                        onCloseImportDialog()
                    } else {
                        onCloseSettings()
                    }
                }
        )

        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val drawerShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(OneUiDimensions.panelPlaylistWidth)
                    .clip(drawerShape)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
                    .background(OneUiTheme.colors.panelBackground)
                    .padding(vertical = 14.dp, horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部大标题与关闭胶囊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统与设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "播放与偏好配置",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        TvFocusableBox(
                            onClick = onCloseSettings,
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 设置内容滚动区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 频道源管理卡片
                        SettingsSectionTitle("频道源管理")
                        OneUiGroupCard {
                            TvFocusableBox(
                                onClick = onOpenImportDialog,
                                shape = OneUiShapes.cardMedium,
                                unfocusedBackgroundColor = Color.Transparent,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(OneUiDimensions.rowHeightSetting)
                            ) { isFocused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "导入 M3U 播放列表",
                                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "局域网扫码推送 / 网络链接",
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(OneUiShapes.pill)
                                            .background(OneUiTheme.colors.accentSoft)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "导入",
                                            color = OneUiTheme.colors.accent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 界面主题模式卡片
                        SettingsSectionTitle("界面主题")
                        OneUiGroupCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val modes = listOf(
                                    ThemeMode.LIGHT to "浅色",
                                    ThemeMode.DARK to "深色",
                                    ThemeMode.SYSTEM to "跟随系统"
                                )
                                modes.forEach { (mode, label) ->
                                    val isSelected = uiState.themeMode == mode
                                    TvFocusableBox(
                                        onClick = { onThemeModeSelected(mode) },
                                        shape = OneUiShapes.pill,
                                        unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.surfacePill,
                                        focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                                        focusedBorderColor = OneUiTheme.colors.accent,
                                        focusedBorderWidth = 1.5.dp,
                                        modifier = Modifier.weight(1f)
                                    ) { isFocused ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 播放与渲染引擎卡片
                        SettingsSectionTitle("画面与解码")
                        OneUiGroupCard {
                            SettingToggleRow(
                                title = "硬件加速解码",
                                subtitle = "MediaCodec GPU 硬解，低功耗高帧率",
                                checked = uiState.isHardwareDecodePreferred,
                                onCheckedChange = onToggleHardwareDecode,
                                modifier = Modifier.focusRequester(firstFocusRequester)
                            )
                            OneUiCardDivider()
                            SettingToggleRow(
                                title = "SurfaceView 优先",
                                subtitle = "专用硬件视频图层，画面流畅",
                                checked = uiState.isSurfaceViewPreferred,
                                onCheckedChange = onToggleSurfaceView
                            )
                        }

                        // 系统设置卡片（开机自启动）
                        SettingsSectionTitle("系统设置")
                        OneUiGroupCard {
                            SettingToggleRow(
                                title = "开机自启动",
                                subtitle = "设备开机后自动拉起直播并播放",
                                checked = uiState.isBootStartupEnabled,
                                onCheckedChange = onToggleBootStartup
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 内嵌导入 M3U 弹窗
        if (uiState.isImportDialogOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                val importDialogShape = OneUiShapes.panel

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 420.dp)
                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), importDialogShape)
                        .background(OneUiTheme.colors.panelBackground, importDialogShape)
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "导入 M3U 播放列表",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 15.sp,
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. Android TV 局域网扫码推送区域（带放大按钮）
                        if (uiState.qrBitmap != null && uiState.localServerUrl != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                                    .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isQrEnlarged = true }
                                ) {
                                    Image(
                                        bitmap = uiState.qrBitmap.asImageBitmap(),
                                        contentDescription = "局域网推送二维码",
                                        modifier = Modifier
                                            .size(76.dp)
                                            .background(Color.White)
                                            .padding(3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "局域网扫码输入",
                                        color = OneUiTheme.colors.accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "手机浏览器访问：",
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = uiState.localServerUrl,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // 二维码放大按钮 (TvFocusableBox)
                                    TvFocusableBox(
                                        onClick = { isQrEnlarged = true },
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
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // 2. M3U 订阅链接或内容直接输入框
                        Text(
                            text = "或手动粘贴订阅链接 / M3U 文本：",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        val inputShape = OneUiShapes.cardMedium
                        BasicTextField(
                            value = uiState.importText,
                            onValueChange = onImportTextChanged,
                            textStyle = TextStyle(
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 12.sp
                            ),
                            cursorBrush = SolidColor(OneUiTheme.colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), inputShape)
                                .background(OneUiTheme.colors.cardBackground, inputShape)
                                .padding(10.dp),
                            decorationBox = { innerTextField ->
                                if (uiState.importText.isEmpty()) {
                                    Text(
                                        text = "支持 http(s):// 开头的订阅地址，GitHub 链接，或 #EXTM3U 完整源文本...",
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (uiState.importError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.importError,
                                color = OneUiTheme.colors.danger,
                                fontSize = 11.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (uiState.importSuccessCount != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "成功导入 ${uiState.importSuccessCount} 个频道及播放源！",
                                color = OneUiTheme.colors.statusSuccess,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            TvFocusableBox(
                                onClick = { if (!uiState.isImporting) onConfirmImport() },
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = if (uiState.isImporting) OneUiTheme.colors.accent.copy(alpha = 0.5f) else OneUiTheme.colors.accent,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = Color.White,
                                focusedBorderWidth = 2.dp
                            ) { _ ->
                                Text(
                                    text = if (uiState.isImporting) "正在导入..." else "确认导入",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 二维码全屏居中超大放大弹窗
        if (isQrEnlarged && uiState.qrBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { isQrEnlarged = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "手机扫码推流至电视",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = uiState.qrBitmap.asImageBitmap(),
                            contentDescription = "放大二维码",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = uiState.localServerUrl ?: "",
                        color = OneUiTheme.colors.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TvFocusableBox(
                        onClick = { isQrEnlarged = false },
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = Color(0x33FFFFFF),
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.dp
                    ) { _ ->
                        Text(
                            text = "关闭放大",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = OneUiTheme.colors.textSecondary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun OneUiGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = OneUiShapes.cardMedium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
            .background(OneUiTheme.colors.cardBackground, cardShape)
    ) {
        content()
    }
}

@Composable
private fun OneUiCardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OneUiTheme.colors.divider)
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OneUiDimensions.rowHeightSetting)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OneUiTheme.colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = OneUiTheme.colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OneUiTheme.colors.accent,
                uncheckedThumbColor = OneUiTheme.colors.textMuted,
                uncheckedTrackColor = OneUiTheme.colors.surfacePill
            )
        )
    }
}
