package com.livestream.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.AppBackground
import com.livestream.core.ui.theme.AppTextPrimary
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.ChannelDrawer
import com.livestream.feature.player.PlayerUiState
import com.livestream.feature.player.ui.EpgScheduleDialog
import com.livestream.feature.player.ui.LiveVideoSurface
import com.livestream.feature.player.ui.PlayerOverlay
import com.livestream.feature.player.ui.WebPlaybackContainer
import com.livestream.feature.settings.SettingsUiState
import com.livestream.feature.settings.ui.SettingsDialog
import com.livestream.playback.api.web.WebPlaybackController

/**
 * Android TV 专属全屏与遥控器交互布局：
 * 1. 默认沉浸式全屏播放
 * 2. D-pad 方向键上下换台、左右切线/OSD、OK 键唤出频道菜单、MENU 打开设置
 * 3. 遥控器数字键直接选台防抖
 * 4. 焦点记忆与远距离高对比度识别
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TvLayout(
    playerUiState: PlayerUiState,
    channelUiState: ChannelUiState,
    settingsUiState: SettingsUiState,
    onSurfaceAttached: (android.view.SurfaceView) -> Unit,
    onSurfaceDetached: () -> Unit,
    onToggleOsd: () -> Unit,
    onShowOsd: () -> Unit,
    onHideOsd: () -> Unit,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onSwitchNextChannel: () -> Unit,
    onSwitchPreviousChannel: () -> Unit,
    onNumberKeyInput: (Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onCategorySelected: (ChannelCategory) -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleHardwareDecode: (Boolean) -> Unit,
    onToggleSurfaceView: (Boolean) -> Unit,
    onOpenImportDialog: () -> Unit,
    onCloseImportDialog: () -> Unit,
    onImportTextChanged: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onToggleEpgSheet: () -> Unit = {},
    onHideEpgSheet: () -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val focusRequester = remember { FocusRequester() }
    var osdFocusIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(settingsUiState.isSettingsOpen, channelUiState.isDrawerOpen, playerUiState.isEpgSheetVisible) {
        if (!settingsUiState.isSettingsOpen && !channelUiState.isDrawerOpen && !playerUiState.isEpgSheetVisible) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(playerUiState.isOsdVisible) {
        if (playerUiState.isOsdVisible) {
            osdFocusIndex = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                // 侧边栏展开时，右键或返回键便捷退出
                if (channelUiState.isDrawerOpen) {
                    if (keyEvent.key == Key.DirectionRight || keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                        onCloseDrawer()
                        focusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                // 设置展开时交由弹窗，若内嵌导入弹窗打开则先关闭导入
                if (settingsUiState.isSettingsOpen) {
                    if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                        if (settingsUiState.isImportDialogOpen) {
                            onCloseImportDialog()
                        } else {
                            onCloseSettings()
                        }
                        focusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                if (playerUiState.isEpgSheetVisible) {
                    if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                        onHideEpgSheet()
                        focusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                // OSD 悬浮条显示时：左右键在 [线路] 与 [节目单] 并排按钮之间切换选择
                if (playerUiState.isOsdVisible) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (osdFocusIndex == 1) {
                                osdFocusIndex = 0
                            } else {
                                onSwitchSource()
                            }
                            return@onKeyEvent true
                        }
                        Key.DirectionRight -> {
                            osdFocusIndex = 1
                            return@onKeyEvent true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (osdFocusIndex == 0) {
                                onSwitchSource()
                            } else {
                                onToggleEpgSheet()
                            }
                            return@onKeyEvent true
                        }
                        Key.DirectionUp, Key.PageUp -> {
                            onSwitchPreviousChannel()
                            onShowOsd()
                            return@onKeyEvent true
                        }
                        Key.DirectionDown, Key.PageDown -> {
                            onSwitchNextChannel()
                            onShowOsd()
                            return@onKeyEvent true
                        }
                        Key.Menu -> {
                            onToggleSettings()
                            return@onKeyEvent true
                        }
                        Key.Back, Key.Escape -> {
                            onHideOsd()
                            return@onKeyEvent true
                        }
                        else -> {}
                    }
                }

                when (keyEvent.key) {
                    Key.DirectionUp, Key.PageUp -> {
                        onSwitchPreviousChannel()
                        onShowOsd()
                        true
                    }
                    Key.DirectionDown, Key.PageDown -> {
                        onSwitchNextChannel()
                        onShowOsd()
                        true
                    }
                    Key.DirectionLeft -> {
                        onShowOsd()
                        osdFocusIndex = 0
                        onSwitchSource()
                        true
                    }
                    Key.DirectionRight -> {
                        onShowOsd()
                        osdFocusIndex = 1
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        onOpenDrawer()
                        true
                    }
                    Key.Menu -> {
                        onToggleSettings()
                        true
                    }
                    Key.Zero, Key.NumPad0 -> { onNumberKeyInput(0); true }
                    Key.One, Key.NumPad1 -> { onNumberKeyInput(1); true }
                    Key.Two, Key.NumPad2 -> { onNumberKeyInput(2); true }
                    Key.Three, Key.NumPad3 -> { onNumberKeyInput(3); true }
                    Key.Four, Key.NumPad4 -> { onNumberKeyInput(4); true }
                    Key.Five, Key.NumPad5 -> { onNumberKeyInput(5); true }
                    Key.Six, Key.NumPad6 -> { onNumberKeyInput(6); true }
                    Key.Seven, Key.NumPad7 -> { onNumberKeyInput(7); true }
                    Key.Eight, Key.NumPad8 -> { onNumberKeyInput(8); true }
                    Key.Nine, Key.NumPad9 -> { onNumberKeyInput(9); true }
                    else -> false
                }
            }
    ) {
        // 1. 底层：硬件渲染图层或官方合规网页容器
        val webUrl = playerUiState.officialWebUrl
        if (playerUiState.isOfficialWeb && !webUrl.isNullOrBlank()) {
            WebPlaybackContainer(
                controller = webPlaybackController,
                url = webUrl,
                modifier = Modifier.fillMaxSize(),
                onContainerClick = {
                    if (channelUiState.isDrawerOpen) {
                        onCloseDrawer()
                    } else if (settingsUiState.isSettingsOpen) {
                        onCloseSettings()
                    } else {
                        onToggleOsd()
                    }
                }
            )
        } else {
            LiveVideoSurface(
                onSurfaceAttached = onSurfaceAttached,
                onSurfaceDetached = onSurfaceDetached,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (channelUiState.isDrawerOpen) {
                            onCloseDrawer()
                        } else if (settingsUiState.isSettingsOpen) {
                            onCloseSettings()
                        } else {
                            onToggleOsd()
                        }
                    }
            )
        }

        // 2. 中层：OSD 状态、指标与错误卡片 (带并排线路与节目单按钮)
        PlayerOverlay(
            uiState = playerUiState,
            onRetry = onRetry,
            onSwitchSource = onSwitchSource,
            onOpenEpgSchedule = onToggleEpgSheet,
            osdFocusedIndex = if (playerUiState.isOsdVisible) osdFocusIndex else -1
        )

        // 3. 顶层侧边栏：频道分类与列表抽屉
        ChannelDrawer(
            uiState = channelUiState,
            onCategorySelected = onCategorySelected,
            onChannelSelected = onChannelSelected,
            onFavoriteToggle = onFavoriteToggle,
            onSearchQueryChanged = onSearchQueryChanged,
            onCloseDrawer = onCloseDrawer
        )

        // 4. 模态浮层：系统与播放设置弹窗
        SettingsDialog(
            uiState = settingsUiState,
            onCloseSettings = onCloseSettings,
            onToggleHardwareDecode = onToggleHardwareDecode,
            onToggleSurfaceView = onToggleSurfaceView,
            onOpenImportDialog = onOpenImportDialog,
            onCloseImportDialog = onCloseImportDialog,
            onImportTextChanged = onImportTextChanged,
            onConfirmImport = onConfirmImport,
            onThemeModeSelected = onThemeModeSelected
        )

        // 5. 模态浮层：真实 EPG 节目单完整排期弹窗
        EpgScheduleDialog(
            visible = playerUiState.isEpgSheetVisible,
            channel = playerUiState.activeChannel,
            programs = playerUiState.dayPrograms,
            onDismiss = onHideEpgSheet
        )
    }
}
