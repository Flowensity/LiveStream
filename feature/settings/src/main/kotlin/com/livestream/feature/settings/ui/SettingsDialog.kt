package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState

/**
 * TeleflowTv 规范系统与播放偏好设置面板 (AppSettingsView, 440dp 居中浮动模态)
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
            val panelShape = OneUiShapes.panel
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 440.dp)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), panelShape)
                    .background(OneUiTheme.colors.panelBackground, panelShape)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    // 1. One UI 顶部大标题与完成胶囊按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "应用设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "播放偏好与系统设置",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp,
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
                                text = "完成",
                                color = if (isFocused) Color.White else OneUiTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 分组卡片 1：播放与渲染引擎
                    SettingsSectionTitle("播放与渲染")
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
                            title = "SurfaceView 渲染图层",
                            subtitle = "独立硬件视频图层，关闭切换为 TextureView",
                            checked = uiState.isSurfaceViewPreferred,
                            onCheckedChange = onToggleSurfaceView
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. 分组卡片 2：外观与明暗主题 (TeleflowTv Segmented Options)
                    SettingsSectionTitle("外观与主题")
                    OneUiGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.values().forEach { mode ->
                                val isSelected = uiState.themeMode == mode
                                val label = when (mode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.DARK -> "深色模式"
                                    ThemeMode.LIGHT -> "浅色模式"
                                }

                                val pillShape = OneUiShapes.pill
                                TvFocusableBox(
                                    onClick = { onThemeModeSelected(mode) },
                                    shape = pillShape,
                                    unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.5.dp,
                                    modifier = Modifier.weight(1f)
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. 分组卡片 3：自定义频道源管理
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
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "导入 M3U 播放列表",
                                        color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "支持局域网扫码直推、网络链接与文件",
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(OneUiShapes.pill)
                                        .background(OneUiTheme.colors.accentSoft)
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "导入",
                                        color = OneUiTheme.colors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 内嵌导入 M3U 弹窗 (TeleflowTv Modal)
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
                            .padding(20.dp)
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
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(OneUiShapes.pill)
                                        .background(OneUiTheme.colors.surfacePill)
                                        .clickable { onCloseImportDialog() }
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "关闭",
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Android TV 局域网扫码推送区域
                            if (uiState.qrBitmap != null && uiState.localServerUrl != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                                        .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = uiState.qrBitmap.asImageBitmap(),
                                        contentDescription = "局域网推送二维码",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .padding(4.dp)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

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
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                        Text(
                                            text = "网页粘贴一键同步到电视",
                                            color = OneUiTheme.colors.textMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Text(
                                text = "或手动输入 M3U 订阅链接 / 内容：",
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
                                        "https://example.com/playlist.m3u\n或直接粘贴 #EXTM3U 文本...",
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 11.sp
                                    )
                                },
                                minLines = 3,
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OneUiTheme.colors.cardSecondary,
                                    unfocusedContainerColor = OneUiTheme.colors.cardSecondary,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    unfocusedBorderColor = OneUiTheme.colors.borderSubtle,
                                    focusedTextColor = OneUiTheme.colors.textPrimary,
                                    unfocusedTextColor = OneUiTheme.colors.textPrimary
                                ),
                                shape = OneUiShapes.cardSmall,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (uiState.importError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = uiState.importError,
                                    color = OneUiTheme.colors.danger,
                                    fontSize = 11.sp
                                )
                            }

                            if (uiState.importSuccessCount != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "成功导入 ${uiState.importSuccessCount} 个频道及播放源！",
                                    color = OneUiTheme.colors.statusSuccess,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(OneUiShapes.pill)
                                        .background(OneUiTheme.colors.surfacePill)
                                        .clickable { onCloseImportDialog() }
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "取消",
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Button(
                                    onClick = onConfirmImport,
                                    enabled = !uiState.isImporting,
                                    shape = OneUiShapes.pill,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OneUiTheme.colors.accent,
                                        disabledContainerColor = OneUiTheme.colors.accent.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = if (uiState.isImporting) "正在导入..." else "确认导入",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
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
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OneUiTheme.colors.textPrimary,
                fontSize = 14.sp,
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
