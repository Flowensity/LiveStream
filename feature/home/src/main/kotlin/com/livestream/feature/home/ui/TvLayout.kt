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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.ChannelDrawer
import com.livestream.feature.player.PlayerUiState
import com.livestream.feature.player.ui.LiveVideoSurface
import com.livestream.feature.player.ui.PlayerOverlay
import com.livestream.feature.settings.SettingsUiState
import com.livestream.feature.settings.ui.SettingsDialog

/**
 * Android TV 专属全屏与遥控器交互布局：
 * 1. 默认沉浸式全屏播放
 * 2. D-pad 方向键上下换台、左右切线/OSD、OK 键唤出频道菜单、MENU 打开设置
 * 3. 遥控器数字键直接选台防抖
 * 4. 焦点记忆与远距离高对比度识别
 */
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
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                // 侧边栏或设置展开时，优先将焦点交给子组件
                if (settingsUiState.isSettingsOpen || channelUiState.isDrawerOpen) {
                    return@onKeyEvent false
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
                        onSwitchSource()
                        true
                    }
                    Key.DirectionRight -> {
                        onShowOsd()
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
        // 1. 底层：硬件渲染图层
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

        // 2. 中层：OSD 状态、指标与错误卡片
        PlayerOverlay(
            uiState = playerUiState,
            onRetry = onRetry,
            onSwitchSource = onSwitchSource
        )

        // 快捷入口操作条
        if (playerUiState.isOsdVisible && !channelUiState.isDrawerOpen && !settingsUiState.isSettingsOpen) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvFocusableBox(
                    onClick = onOpenDrawer
                ) { isFocused ->
                    Text(
                        text = "频道菜单",
                        color = if (isFocused) FocusHighlight else TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Box(modifier = Modifier.padding(start = 10.dp)) {
                    TvFocusableBox(
                        onClick = onOpenSettings
                    ) { isFocused ->
                        Text(
                            text = "设置",
                            color = if (isFocused) FocusHighlight else TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

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
            onConfirmImport = onConfirmImport
        )
    }
}
