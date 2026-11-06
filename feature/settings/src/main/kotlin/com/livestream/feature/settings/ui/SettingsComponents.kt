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
import com.livestream.core.model.VideoAspectRatio
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
    onToggleBootStartup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    onEnlargeQrCode: () -> Unit = onOpenImportDialog
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val firstItemModifier = if (firstFocusRequester != null) {
            Modifier.focusRequester(firstFocusRequester)
        } else {
            Modifier
        }

        // 1. 置顶独立入口：手机扫码遥控与选台（后台常驻，关闭二维码后依然可控）
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
                    .defaultMinSize(minHeight = 56.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.qrBitmap != null) {
                        Image(
                            bitmap = uiState.qrBitmap.asImageBitmap(),
                            contentDescription = "手机扫码遥控二维码",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.localServerUrl ?: "局域网扫码 · 关掉弹窗仍可随时遥控",
                            color = OneUiTheme.colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = "支持手机换台、搜台点播、换线与推送源",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(OneUiShapes.pill)
                            .background(OneUiTheme.colors.accent)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "扫码遥控",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. 频道源管理卡片
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
                    .defaultMinSize(minHeight = 48.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "局域网扫码推送 / 网络链接",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
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
                            fontSize = 11.sp,
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
                    .defaultMinSize(minHeight = 48.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.customChannels.isNotEmpty()) {
                                "已导入 ${uiState.customChannels.size} 个节目 (${uiState.customSources.size} 个源)"
                            } else {
                                "管理或删除自定义源与节目"
                            },
                            color = if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(OneUiShapes.pill)
                            .background(
                                if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accentSoft
                                else OneUiTheme.colors.surfacePill
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.customChannels.isNotEmpty()) "管理" else "查看",
                            color = if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
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

        // 解码与渲染卡片（画面比例已迁移至「播放菜单」）
        SettingsSectionTitle("解码与渲染")
        OneUiGroupCard {
            SettingToggleRow(
                title = "硬件加速解码",
                subtitle = "MediaCodec GPU 硬解，关闭则优先软解兼容老盒子",
                checked = uiState.isHardwareDecodePreferred,
                onCheckedChange = onToggleHardwareDecode
            )
            OneUiCardDivider()
            SettingToggleRow(
                title = "SurfaceView 优先",
                subtitle = "专用硬件视频图层，关闭则使用 TextureView 防穿透",
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
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = OneUiTheme.colors.textSecondary,
        fontSize = 11.5.sp,
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
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 1.dp)
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
