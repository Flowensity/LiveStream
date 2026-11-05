package com.livestream.feature.home.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.feature.channel.ChannelViewModel
import com.livestream.feature.channel.ui.ChannelDrawer
import com.livestream.feature.home.HomeViewModel
import com.livestream.feature.player.PlayerViewModel
import com.livestream.feature.player.ui.LiveVideoSurface
import com.livestream.feature.player.ui.PlayerOverlay
import com.livestream.feature.settings.SettingsViewModel
import com.livestream.feature.settings.ui.SettingsDialog

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    channelViewModel: ChannelViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val playerUiState by playerViewModel.uiState.collectAsState()
    val channelUiState by channelViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val focusRequester = remember { FocusRequester() }

    // 启动自动播放
    LaunchedEffect(Unit) {
        homeViewModel.initStartupPlayback()
        focusRequester.requestFocus()
    }

    // 返回键拦截与层级消费
    BackHandler(
        enabled = settingsUiState.isSettingsOpen ||
            channelUiState.isDrawerOpen ||
            playerUiState.isOsdVisible
    ) {
        when {
            settingsUiState.isImportDialogOpen -> settingsViewModel.closeImportDialog()
            settingsUiState.isSettingsOpen -> settingsViewModel.closeSettings()
            channelUiState.isDrawerOpen -> channelViewModel.closeDrawer()
            playerUiState.isOsdVisible -> playerViewModel.hideOsd()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                // 当设置弹窗或频道侧边栏展开时，优先交给子组件处理焦点与按键
                if (settingsUiState.isSettingsOpen || channelUiState.isDrawerOpen) {
                    return@onKeyEvent false
                }

                when (keyEvent.key) {
                    Key.DirectionUp, Key.PageUp -> {
                        homeViewModel.switchPreviousChannel()
                        playerViewModel.showOsd()
                        true
                    }
                    Key.DirectionDown, Key.PageDown -> {
                        homeViewModel.switchNextChannel()
                        playerViewModel.showOsd()
                        true
                    }
                    Key.DirectionLeft -> {
                        playerViewModel.switchNextSource()
                        true
                    }
                    Key.DirectionRight -> {
                        playerViewModel.showOsd()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        channelViewModel.openDrawer()
                        true
                    }
                    Key.Menu -> {
                        settingsViewModel.toggleSettings()
                        true
                    }
                    Key.Zero, Key.NumPad0 -> { playerViewModel.onNumberKeyInput(0); true }
                    Key.One, Key.NumPad1 -> { playerViewModel.onNumberKeyInput(1); true }
                    Key.Two, Key.NumPad2 -> { playerViewModel.onNumberKeyInput(2); true }
                    Key.Three, Key.NumPad3 -> { playerViewModel.onNumberKeyInput(3); true }
                    Key.Four, Key.NumPad4 -> { playerViewModel.onNumberKeyInput(4); true }
                    Key.Five, Key.NumPad5 -> { playerViewModel.onNumberKeyInput(5); true }
                    Key.Six, Key.NumPad6 -> { playerViewModel.onNumberKeyInput(6); true }
                    Key.Seven, Key.NumPad7 -> { playerViewModel.onNumberKeyInput(7); true }
                    Key.Eight, Key.NumPad8 -> { playerViewModel.onNumberKeyInput(8); true }
                    Key.Nine, Key.NumPad9 -> { playerViewModel.onNumberKeyInput(9); true }
                    else -> false
                }
            }
    ) {
        // 1. 底层：原生视频硬件渲染表面
        LiveVideoSurface(
            onSurfaceAttached = { surfaceView ->
                playerViewModel.attachSurface(surfaceView)
            },
            onSurfaceDetached = {
                playerViewModel.detachSurface()
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (channelUiState.isDrawerOpen) {
                        channelViewModel.closeDrawer()
                    } else if (settingsUiState.isSettingsOpen) {
                        settingsViewModel.closeSettings()
                    } else {
                        playerViewModel.toggleOsd()
                    }
                }
        )

        // 2. 中层：OSD 状态、码率指标、缓冲与错误面板
        PlayerOverlay(
            uiState = playerUiState,
            onRetry = { playerViewModel.retry() },
            onSwitchSource = { playerViewModel.switchNextSource() }
        )

        // 快捷入口浮层（右上角频道菜单与设置快捷按钮）
        if (playerUiState.isOsdVisible && !channelUiState.isDrawerOpen && !settingsUiState.isSettingsOpen) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvFocusableBox(
                    onClick = { channelViewModel.openDrawer() }
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
                        onClick = { settingsViewModel.openSettings() }
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
            onCategorySelected = { category -> channelViewModel.selectCategory(category) },
            onChannelSelected = { channel ->
                homeViewModel.playChannel(channel)
                channelViewModel.selectChannel(channel)
            },
            onFavoriteToggle = { channel -> channelViewModel.toggleFavorite(channel) },
            onSearchQueryChanged = { query -> channelViewModel.onSearchQueryChanged(query) },
            onCloseDrawer = { channelViewModel.closeDrawer() }
        )

        // 4. 模态浮层：系统与播放设置弹窗
        SettingsDialog(
            uiState = settingsUiState,
            onCloseSettings = { settingsViewModel.closeSettings() },
            onToggleHardwareDecode = { settingsViewModel.setHardwareDecodePreferred(it) },
            onToggleSurfaceView = { settingsViewModel.setSurfaceViewPreferred(it) },
            onOpenImportDialog = { settingsViewModel.openImportDialog() },
            onCloseImportDialog = { settingsViewModel.closeImportDialog() },
            onImportTextChanged = { settingsViewModel.onImportTextChanged(it) },
            onConfirmImport = { settingsViewModel.importM3uContent() }
        )
    }
}
