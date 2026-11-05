package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.AppBackground
import com.livestream.core.ui.theme.AppSurface
import com.livestream.core.ui.theme.AppSurfaceElevated
import com.livestream.core.ui.theme.AppTextMuted
import com.livestream.core.ui.theme.AppTextPrimary
import com.livestream.core.ui.theme.AppTextSecondary
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.StatusError
import com.livestream.core.ui.theme.StatusSuccess
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
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .background(AppBackground, RoundedCornerShape(14.dp))
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
                            color = AppTextPrimary,
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
                                color = AppTextMuted,
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
                        onCheckedChange = onToggleHardwareDecode,
                        modifier = Modifier.focusRequester(firstFocusRequester)
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
                        color = AppTextSecondary,
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
                                unfocusedBackgroundColor = if (isSelected) AppSurfaceElevated else AppSurface,
                                focusedBackgroundColor = AppSurfaceElevated,
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
                                        color = if (isSelected || isFocused) FocusHighlight else AppTextMuted,
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
                        color = AppTextSecondary,
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
                                    color = if (isFocused) FocusHighlight else AppTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "支持标准 #EXTINF 格式，带内网 SSRF 自动过滤",
                                    color = AppTextMuted,
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
                            .width(580.dp)
                            .background(AppSurfaceElevated, RoundedCornerShape(14.dp))
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
                                    color = AppTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TvFocusableBox(
                                    onClick = onCloseImportDialog,
                                    shape = RoundedCornerShape(6.dp)
                                ) { isFocused ->
                                    Text(
                                        text = "关闭",
                                        color = if (isFocused) FocusHighlight else AppTextSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Android TV 局域网扫码推送区域
                            if (uiState.qrBitmap != null && uiState.localServerUrl != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppSurface, RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = uiState.qrBitmap.asImageBitmap(),
                                        contentDescription = "局域网推送二维码",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .padding(4.dp)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📱 手机扫码或局域网推送",
                                            color = FocusHighlight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "同局域网浏览器访问：",
                                            color = AppTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = uiState.localServerUrl,
                                            color = AppTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "手机粘贴 M3U 订阅 URL 或内容一键推送到电视",
                                            color = AppTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            Text(
                                text = "或手动输入 M3U 订阅 URL 或播放列表内容：",
                                color = AppTextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = uiState.importText,
                                onValueChange = onImportTextChanged,
                                placeholder = {
                                    Text(
                                        "https://example.com/playlist.m3u\n或直接粘贴 #EXTM3U 文本内容...",
                                        color = AppTextMuted,
                                        fontSize = 12.sp
                                    )
                                },
                                minLines = 3,
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = AppSurface,
                                    unfocusedContainerColor = AppSurface,
                                    focusedBorderColor = FocusHighlight,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = AppTextPrimary,
                                    unfocusedTextColor = AppTextPrimary
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
                                    text = "🎉 成功安全导入 ${uiState.importSuccessCount} 个频道及播放源！",
                                    color = StatusSuccess,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onCloseImportDialog,
                                    colors = ButtonDefaults.buttonColors(containerColor = AppSurface)
                                ) {
                                    Text("取消", color = AppTextSecondary)
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
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableBox(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
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
                    color = if (isFocused) FocusHighlight else AppTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = AppTextMuted,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FocusHighlight,
                    checkedTrackColor = AppSurfaceElevated
                )
            )
        }
    }
}
