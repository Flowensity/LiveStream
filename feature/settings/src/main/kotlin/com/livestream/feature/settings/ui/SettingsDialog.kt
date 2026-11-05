package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.DarkSurface
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.StatusError
import com.livestream.core.ui.theme.StatusSuccess
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.core.ui.theme.TextSecondary
import com.livestream.feature.settings.SettingsUiState

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
    AnimatedVisibility(
        visible = uiState.isSettingsOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .background(DarkBackground, RoundedCornerShape(14.dp))
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 标题
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "播放与系统设置",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TvFocusableBox(
                            onClick = onCloseSettings,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "关闭",
                                color = TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. 硬件解码优先
                    SettingToggleRow(
                        title = "硬件解码优先",
                        subtitle = "默认启用 MediaCodec 硬件加速解码，降低功耗与延迟",
                        checked = uiState.isHardwareDecodePreferred,
                        onCheckedChange = onToggleHardwareDecode
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. 视频渲染表面 (SurfaceView / TextureView)
                    SettingToggleRow(
                        title = "SurfaceView 渲染图层",
                        subtitle = "开启为高性能独立硬件合成图层，关闭则使用 TextureView",
                        checked = uiState.isSurfaceViewPreferred,
                        onCheckedChange = onToggleSurfaceView
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. 主题模式切换
                    Text(
                        text = "显示主题模式",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.values().forEach { mode ->
                            val isSelected = uiState.themeMode == mode
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.DARK -> "深色模式"
                                ThemeMode.LIGHT -> "浅色模式"
                            }
                            TvFocusableBox(
                                onClick = { onThemeModeSelected(mode) },
                                unfocusedBackgroundColor = if (isSelected) DarkSurfaceElevated else DarkSurface,
                                focusedBackgroundColor = DarkSurfaceElevated,
                                focusedBorderColor = FocusHighlight,
                                modifier = Modifier.weight(1f)
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected || isFocused) FocusHighlight else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. 自定义源管理
                    Text(
                        text = "自定义资源管理",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TvFocusableBox(
                        onClick = onOpenImportDialog,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "导入 M3U 直播源列表",
                                    color = if (isFocused) FocusHighlight else TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "支持标准 #EXTINF 格式，带内网 SSRF 自动过滤",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Text(text = "导入 >", color = FocusHighlight, fontSize = 14.sp)
                        }
                    }
                }
            }

            // 内嵌导入 M3U 弹窗
            if (uiState.isImportDialogOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(520.dp)
                            .background(DarkSurfaceElevated, RoundedCornerShape(12.dp))
                            .padding(24.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "导入 M3U 播放列表",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.importText,
                                onValueChange = onImportTextChanged,
                                placeholder = {
                                    Text(
                                        "#EXTM3U\n#EXTINF:-1 group-title=\"央视\",CCTV-1\nhttps://example.com/live.m3u8",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                },
                                minLines = 5,
                                maxLines = 8,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface,
                                    focusedBorderColor = FocusHighlight,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (uiState.importError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.importError,
                                    color = StatusError,
                                    fontSize = 12.sp
                                )
                            }

                            if (uiState.importSuccessCount != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "成功安全导入 ${uiState.importSuccessCount} 个频道及播放源！",
                                    color = StatusSuccess,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onCloseImportDialog,
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                                ) {
                                    Text("取消", color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = onConfirmImport,
                                    enabled = !uiState.isImporting,
                                    colors = ButtonDefaults.buttonColors(containerColor = FocusHighlight)
                                ) {
                                    Text(
                                        text = if (uiState.isImporting) "正在校验导入..." else "确认导入",
                                        color = Color.Black,
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
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TvFocusableBox(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isFocused) FocusHighlight else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FocusHighlight,
                    checkedTrackColor = DarkSurfaceElevated
                )
            )
        }
    }
}
