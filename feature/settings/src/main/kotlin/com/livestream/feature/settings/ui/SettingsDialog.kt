package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.VideoAspectRatio
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.device.DeviceType
import com.livestream.core.ui.device.rememberDeviceType
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState
import kotlinx.coroutines.delay

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
    onOpenSourceManagement: () -> Unit = {},
    onCloseSourceManagement: () -> Unit = {},
    onDeleteCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onDeleteSingleCustomChannel: (String) -> Unit = {},
    onClearAllCustomSources: () -> Unit = {},
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    onRefreshCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onRefreshAllCustomSources: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!uiState.isSettingsOpen && !uiState.isImportDialogOpen && !uiState.isSourceManagementOpen) return

    val isTv = rememberDeviceType() == DeviceType.TV
    val firstFocusRequester = remember { FocusRequester() }
    val importCancelFocusRequester = remember { FocusRequester() }
    val sourceCloseFocusRequester = remember { FocusRequester() }
    var isQrEnlarged by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSettingsOpen, uiState.isImportDialogOpen) {
        if (!uiState.isSettingsOpen && !uiState.isImportDialogOpen) {
            isQrEnlarged = false
        }
    }

    LaunchedEffect(isTv, isQrEnlarged, uiState.isSettingsOpen, uiState.isImportDialogOpen, uiState.isSourceManagementOpen) {
        if (!isTv || isQrEnlarged) return@LaunchedEffect
        if (uiState.isSourceManagementOpen) {
            delay(120)
            try {
                sourceCloseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (uiState.isImportDialogOpen) {
            delay(120)
            try {
                importCancelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (uiState.isSettingsOpen) {
            delay(120)
            try {
                firstFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 半透明背景遮罩（点击可关闭当前浮层）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (uiState.isSourceManagementOpen) {
                        onCloseSourceManagement()
                    } else if (uiState.isImportDialogOpen) {
                        onCloseImportDialog()
                    } else {
                        onCloseSettings()
                    }
                }
        )

        // 1. 右侧设置抽屉面板
        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsDrawerPanel(
                uiState = uiState,
                firstFocusRequester = firstFocusRequester,
                onCloseSettings = onCloseSettings,
                onOpenImportDialog = onOpenImportDialog,
                onOpenSourceManagement = onOpenSourceManagement,
                onThemeModeSelected = onThemeModeSelected,
                onToggleHardwareDecode = onToggleHardwareDecode,
                onToggleSurfaceView = onToggleSurfaceView,
                onToggleBootStartup = onToggleBootStartup,
                onAspectRatioSelected = onAspectRatioSelected,
                onEnlargeQrCode = { isQrEnlarged = true }
            )
        }

        // 2. 内嵌导入 M3U 弹窗
        if (uiState.isImportDialogOpen) {
            M3uImportDialog(
                uiState = uiState,
                importCancelFocusRequester = importCancelFocusRequester,
                onCloseImportDialog = onCloseImportDialog,
                onImportTextChanged = onImportTextChanged,
                onConfirmImport = onConfirmImport,
                onEnlargeQrCode = { isQrEnlarged = true }
            )
        }

        // 3. 二维码全屏居中超大放大弹窗 (支持电视遥控器一键关闭)
        if (isQrEnlarged && uiState.qrBitmap != null) {
            QrCodeEnlargedOverlay(
                qrBitmap = uiState.qrBitmap,
                localServerUrl = uiState.localServerUrl,
                onDismiss = { isQrEnlarged = false }
            )
        }

        // 4. 自定义源与节目管理弹窗
        if (uiState.isSourceManagementOpen) {
            SourceManagementDialog(
                uiState = uiState,
                sourceCloseFocusRequester = sourceCloseFocusRequester,
                onCloseSourceManagement = onCloseSourceManagement,
                onDeleteCustomPlaylistSource = onDeleteCustomPlaylistSource,
                onDeleteSingleCustomChannel = onDeleteSingleCustomChannel,
                onClearAllCustomSources = onClearAllCustomSources,
                onRefreshCustomPlaylistSource = onRefreshCustomPlaylistSource,
                onRefreshAllCustomSources = onRefreshAllCustomSources
            )
        }
    }
}

@Composable
private fun SettingsDrawerPanel(
    uiState: SettingsUiState,
    firstFocusRequester: FocusRequester,
    onCloseSettings: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onOpenSourceManagement: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onToggleHardwareDecode: (Boolean) -> Unit,
    onToggleSurfaceView: (Boolean) -> Unit,
    onToggleBootStartup: (Boolean) -> Unit,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    onEnlargeQrCode: () -> Unit = {}
) {
    val drawerShape = RoundedCornerShape(20.dp)
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 8.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)
            .width(OneUiDimensions.panelPlaylistWidth)
            .clip(drawerShape)
            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
            .background(OneUiTheme.colors.panelBackground)
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部大标题与关闭胶囊
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

            // 设置内容滚动区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                SettingsContent(
                    uiState = uiState,
                    onOpenImportDialog = onOpenImportDialog,
                    onOpenSourceManagement = onOpenSourceManagement,
                    onThemeModeSelected = onThemeModeSelected,
                    onToggleHardwareDecode = onToggleHardwareDecode,
                    onToggleSurfaceView = onToggleSurfaceView,
                    onToggleBootStartup = onToggleBootStartup,
                    firstFocusRequester = firstFocusRequester,
                    onAspectRatioSelected = onAspectRatioSelected,
                    onEnlargeQrCode = onEnlargeQrCode
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
