package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState

/**
 * Samsung One UI 风格系统与播放偏好设置面板：
 * 1. 采用 One UI 标志性分组卡片式布局 (Grouped Card Containers)
 * 2. 圆润胶囊组件与高雅精致的层次感设计
 * 3. 完备的三星明暗主题模式切换 (浅色 / 深色 / 跟随系统)
 * 4. 局域网扫码推送与 M3U 导入卡片无缝融合
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
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isSettingsOpen) {
        if (uiState.isSettingsOpen && !uiState.isImportDialogOpen) {
            firstFocusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = uiState.isSettingsOpen,
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
            val panelShape = RoundedCornerShape(26.dp)
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .width(500.dp)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.divider), panelShape)
                    .background(OneUiTheme.colors.background, panelShape)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    // 1. One UI 顶部导航操作栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "播放器核心与视觉偏好",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        TvFocusableBox(
                            onClick = onCloseSettings,
                            shape = RoundedCornerShape(50),
                            unfocusedBackgroundColor = OneUiTheme.colors.accent,
                            focusedBackgroundColor = OneUiTheme.colors.accent
                        ) { isFocused ->
                            Text(
                                text = "完成",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. 分组卡片 1：解码与渲染引擎 (One UI Grouped Card)
                    SettingsSectionTitle("播放与渲染引擎")
                    OneUiGroupCard {
                        SettingToggleRow(
                            title = "硬件加速解码",
                            subtitle = "优先启用 MediaCodec GPU 硬解，低功耗高帧率",
                            checked = uiState.isHardwareDecodePreferred,
                            onCheckedChange = onToggleHardwareDecode,
                            modifier = Modifier.focusRequester(firstFocusRequester)
                        )
                        OneUiCardDivider()
                        SettingToggleRow(
                            title = "SurfaceView 渲染图层",
                            subtitle = "开启独立硬件合成图层，关闭则切换为 TextureView",
                            checked = uiState.isSurfaceViewPreferred,
                            onCheckedChange = onToggleSurfaceView
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. 分组卡片 2：外观与明暗主题
                    SettingsSectionTitle("外观与显示主题")
                    OneUiGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.values().forEach { mode ->
                                val isSelected = uiState.themeMode == mode
                                val label = when (mode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.DARK -> "深色模式"
                                    ThemeMode.LIGHT -> "浅色模式"
                                }
                                val icon = when (mode) {
                                    ThemeMode.SYSTEM -> "⚙"
                                    ThemeMode.DARK -> "🌙"
                                    ThemeMode.LIGHT -> "☀️"
                                }

                                val pillShape = RoundedCornerShape(14.dp)
                                TvFocusableBox(
                                    onClick = { onThemeModeSelected(mode) },
                                    shape = pillShape,
                                    unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accentSoft else OneUiTheme.colors.cardSecondary,
                                    focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accentSoft else OneUiTheme.colors.cardSecondary,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    modifier = Modifier.weight(1f)
                                ) { isFocused ->
                                    val borderStroke = if (isSelected) {
                                        BorderStroke(1.5.dp, OneUiTheme.colors.accent)
                                    } else {
                                        null
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (borderStroke != null) Modifier.border(borderStroke, pillShape) else Modifier
                                            )
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = icon, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            color = if (isSelected || isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4. 分组卡片 3：自定义资源管理
                    SettingsSectionTitle("自定义资源扩展")
                    OneUiGroupCard {
                        TvFocusableBox(
                            onClick = onOpenImportDialog,
                            shape = RoundedCornerShape(16.dp),
                            unfocusedBackgroundColor = Color.Transparent,
                            focusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                            modifier = Modifier.fillMaxWidth()
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "导入 M3U 直播源列表",
                                        color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "支持局域网扫码直推、网络订阅 URL 与标准 #EXTINF",
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = "›",
                                    color = OneUiTheme.colors.accent,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 内嵌导入 M3U 弹窗 (One UI Modal)
            if (uiState.isImportDialogOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    val importDialogShape = RoundedCornerShape(26.dp)

                    Box(
                        modifier = Modifier
                            .width(560.dp)
                            .border(BorderStroke(1.dp, OneUiTheme.colors.divider), importDialogShape)
                            .background(OneUiTheme.colors.background, importDialogShape)
                            .padding(24.dp)
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
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TvFocusableBox(
                                    onClick = onCloseImportDialog,
                                    shape = RoundedCornerShape(50),
                                    unfocusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                                    focusedBackgroundColor = OneUiTheme.colors.cardSecondary
                                ) { isFocused ->
                                    Text(
                                        text = "关闭",
                                        color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Android TV 局域网扫码推送区域
                            if (uiState.qrBitmap != null && uiState.localServerUrl != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.divider), RoundedCornerShape(16.dp))
                                        .background(OneUiTheme.colors.cardBackground, RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = uiState.qrBitmap.asImageBitmap(),
                                        contentDescription = "局域网推送二维码",
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                            .padding(4.dp)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📱 局域网扫码直推",
                                            color = OneUiTheme.colors.accent,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "同一 Wi-Fi 下使用手机或电脑访问：",
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = uiState.localServerUrl,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                        Text(
                                            text = "在网页直接粘贴链接一键推送同步到电视",
                                            color = OneUiTheme.colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            Text(
                                text = "或手动输入 M3U 订阅 URL / 内容：",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = uiState.importText,
                                onValueChange = onImportTextChanged,
                                placeholder = {
                                    Text(
                                        "https://example.com/playlist.m3u\n或直接粘贴 #EXTM3U 文本内容...",
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 12.sp
                                    )
                                },
                                minLines = 3,
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OneUiTheme.colors.cardSecondary,
                                    unfocusedContainerColor = OneUiTheme.colors.cardSecondary,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    unfocusedBorderColor = OneUiTheme.colors.divider,
                                    focusedTextColor = OneUiTheme.colors.textPrimary,
                                    unfocusedTextColor = OneUiTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (uiState.importError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.importError,
                                    color = OneUiTheme.colors.statusError,
                                    fontSize = 12.sp
                                )
                            }

                            if (uiState.importSuccessCount != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "🎉 成功安全导入 ${uiState.importSuccessCount} 个频道及播放源！",
                                    color = OneUiTheme.colors.statusSuccess,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TvFocusableBox(
                                    onClick = onCloseImportDialog,
                                    shape = RoundedCornerShape(50),
                                    unfocusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                                    focusedBackgroundColor = OneUiTheme.colors.cardSecondary
                                ) { isFocused ->
                                    Text(
                                        text = "取消",
                                        color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Button(
                                    onClick = onConfirmImport,
                                    enabled = !uiState.isImporting,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OneUiTheme.colors.accent,
                                        disabledContainerColor = OneUiTheme.colors.accent.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = if (uiState.isImporting) "正在导入..." else "确认导入",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                            }
                        }
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
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun OneUiGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, OneUiTheme.colors.divider), cardShape)
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OneUiTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = OneUiTheme.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OneUiTheme.colors.accent,
                uncheckedThumbColor = OneUiTheme.colors.textMuted,
                uncheckedTrackColor = OneUiTheme.colors.cardSecondary
            )
        )
    }
}
