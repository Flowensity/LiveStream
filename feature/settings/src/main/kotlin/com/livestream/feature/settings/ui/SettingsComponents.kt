package com.livestream.feature.settings.ui

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onOpenImportDialog: () -> Unit,
    onOpenSourceManagement: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onToggleHardwareDecode: (Boolean) -> Unit,
    onToggleSurfaceView: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    onEnlargeQrCode: () -> Unit = onOpenImportDialog,
    isTv: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val firstItemModifier = if (firstFocusRequester != null) {
            Modifier.focusRequester(firstFocusRequester)
        } else {
            Modifier
        }

        // 1. 置顶独立入口：手机扫码遥控与选台（仅 Android TV 电视端展示）
        if (isTv) {
            SettingsSectionTitle("手机扫码遥控")
            OneUiGroupCard {
                TvFocusableBox(
                    onClick = {
                        if (uiState.qrBitmap != null) {
                            onEnlargeQrCode()
                        } else {
                            onOpenImportDialog()
                        }
                    },
                    shape = OneUiShapes.cardMedium,
                    unfocusedBackgroundColor = Color.Transparent,
                    focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                    focusedBorderColor = OneUiTheme.colors.accent,
                    focusedBorderWidth = 1.5.dp,
                    modifier = firstItemModifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 60.dp)
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.qrBitmap != null) {
                            Image(
                                bitmap = uiState.qrBitmap.asImageBitmap(),
                                contentDescription = "手机扫码遥控二维码",
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.White)
                                    .padding(2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "手机扫码遥控与选台",
                                color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.localServerUrl ?: "同一 Wi-Fi 扫码连接 · 关闭窗口仍可遥控",
                                color = OneUiTheme.colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "支持手机端切台、搜台点播、换线与推送列表",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(OneUiShapes.pill)
                                .background(OneUiTheme.colors.accent)
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "扫码连接",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. 频道源管理卡片
        val importItemModifier = if (!isTv) firstItemModifier else Modifier
        SettingsSectionTitle("频道源管理")
        OneUiGroupCard {
            TvFocusableBox(
                onClick = onOpenImportDialog,
                shape = OneUiShapes.cardMedium,
                unfocusedBackgroundColor = Color.Transparent,
                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp,
                modifier = importItemModifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "导入 M3U 播放列表",
                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isTv) "支持手机扫码推送、订阅链接或文本导入" else "支持网络订阅链接或 #EXTM3U 文本导入",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
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
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OneUiCardDivider()

            TvFocusableBox(
                onClick = onOpenSourceManagement,
                shape = OneUiShapes.cardMedium,
                unfocusedBackgroundColor = Color.Transparent,
                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "自定义源管理",
                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.customChannels.isNotEmpty()) {
                                "已导入 ${uiState.customChannels.size} 个频道 · ${uiState.customSources.size} 个列表源"
                            } else {
                                "检测线路连通性、刷新订阅或清理自定义频道"
                            },
                            color = if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(OneUiShapes.pill)
                            .background(
                                if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accentSoft
                                else OneUiTheme.colors.surfacePill
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (uiState.customChannels.isNotEmpty()) "管理" else "查看",
                            color = if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. 外观与主题模式卡片
        SettingsSectionTitle("外观与主题")
        OneUiGroupCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    ThemeMode.LIGHT to "浅色模式",
                    ThemeMode.DARK to "深色模式",
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
                                .padding(vertical = 8.dp),
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

        // 4. 解码与渲染卡片
        SettingsSectionTitle("解码与渲染")
        OneUiGroupCard {
            SettingToggleRow(
                title = "硬件加速解码",
                subtitle = "优先使用 GPU 硬件解码，画面更流畅；若遇黑屏或花屏可关闭",
                checked = uiState.isHardwareDecodePreferred,
                onCheckedChange = onToggleHardwareDecode
            )
            OneUiCardDivider()
            SettingToggleRow(
                title = "高性能渲染图层 (SurfaceView)",
                subtitle = "降低播放功耗与画面延迟；若切换画面偶现残影可关闭",
                checked = uiState.isSurfaceViewPreferred,
                onCheckedChange = onToggleSurfaceView
            )
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = OneUiTheme.colors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
internal fun OneUiGroupCard(
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
internal fun OneUiCardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OneUiTheme.colors.divider)
    )
}

@Composable
internal fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableBox(
        onClick = { onCheckedChange(!checked) },
        shape = OneUiShapes.cardSmall,
        scaleOnFocus = 1.0f,
        unfocusedBackgroundColor = Color.Transparent,
        focusedBackgroundColor = OneUiTheme.colors.accentSoft,
        focusedBorderColor = OneUiTheme.colors.accent,
        focusedBorderWidth = 1.5.dp,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 54.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = title,
                    color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = OneUiTheme.colors.accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = OneUiTheme.colors.textMuted,
                    uncheckedTrackColor = OneUiTheme.colors.surfacePill,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
