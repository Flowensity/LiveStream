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
import androidx.compose.runtime.mutableLongStateOf
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
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.VideoAspectRatio
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
    onSurfaceAttached: (android.view.View) -> Unit,
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
    onToggleBootStartup: (Boolean) -> Unit = {},
    onOpenSourceManagement: () -> Unit = {},
    onCloseSourceManagement: () -> Unit = {},
    onDeleteCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onDeleteSingleCustomChannel: (String) -> Unit = {},
    onClearAllCustomSources: () -> Unit = {},
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
    onRefreshCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onRefreshAllCustomSources: () -> Unit = {},
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val focusRequester = remember { FocusRequester() }
    var osdFocusIndex by remember { mutableIntStateOf(0) }
    var lastChannelSwitchTime by remember { mutableLongStateOf(0L) }

    val isErrorVisible = playerUiState.status == PlaybackStatus.ERROR && playerUiState.error != null

    LaunchedEffect(
        settingsUiState.isSettingsOpen,
        settingsUiState.isImportDialogOpen,
        settingsUiState.isSourceManagementOpen,
        channelUiState.isDrawerOpen,
        playerUiState.isEpgSheetVisible,
        isErrorVisible
    ) {
        if (!settingsUiState.isSettingsOpen &&
            !settingsUiState.isImportDialogOpen &&
            !settingsUiState.isSourceManagementOpen &&
            !channelUiState.isDrawerOpen &&
            !playerUiState.isEpgSheetVisible &&
            !isErrorVisible
        ) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
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

                // 侧边栏展开时：左右方向键切换分类（搜索框输入非空时保留给光标移动），返回键/退出键关闭侧边栏
                if (channelUiState.isDrawerOpen) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (channelUiState.searchQuery.isNotEmpty()) return@onKeyEvent false
                            val categories = channelUiState.categories
                            if (categories.isNotEmpty()) {
                                val currIdx = categories.indexOfFirst { it.id == channelUiState.selectedCategory?.id }
                                val prevIdx = if (currIdx <= 0) categories.size - 1 else currIdx - 1
                                onCategorySelected(categories[prevIdx])
                            }
                            return@onKeyEvent true
                        }
                        Key.DirectionRight -> {
                            if (channelUiState.searchQuery.isNotEmpty()) return@onKeyEvent false
                            val categories = channelUiState.categories
                            if (categories.isNotEmpty()) {
                                val currIdx = categories.indexOfFirst { it.id == channelUiState.selectedCategory?.id }
                                val nextIdx = (currIdx + 1) % categories.size
                                onCategorySelected(categories[nextIdx])
                            }
                            return@onKeyEvent true
                        }
                        Key.Back, Key.Escape -> {
                            onCloseDrawer()
                            focusRequester.requestFocus()
                            return@onKeyEvent true
                        }
                        else -> return@onKeyEvent false
                    }
                }

                // 设置或导入/源管理展开时拦截返回键，其余按键返回 false 交由 Compose FocusOwner 在弹窗内移动焦点
                if (settingsUiState.isSettingsOpen || settingsUiState.isImportDialogOpen || settingsUiState.isSourceManagementOpen) {
                    when (keyEvent.key) {
                        Key.Back, Key.Escape -> {
                            if (settingsUiState.isSourceManagementOpen) {
                                onCloseSourceManagement()
                            } else if (settingsUiState.isImportDialogOpen) {
                                onCloseImportDialog()
                            } else {
                                onCloseSettings()
                            }
                            focusRequester.requestFocus()
                            return@onKeyEvent true
                        }
                        Key.Menu -> {
                            if (!settingsUiState.isImportDialogOpen && !settingsUiState.isSourceManagementOpen) {
                                onCloseSettings()
                                focusRequester.requestFocus()
                            }
                            return@onKeyEvent true
                        }
                        else -> return@onKeyEvent false
                    }
                }

                // 节目单展开时拦截返回键，其余方向键交由节目单内部焦点树处理
                if (playerUiState.isEpgSheetVisible) {
                    if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                        onHideEpgSheet()
                        focusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                // 播放错误卡片展示时：优先允许重试/换线操作及上下切台，避免按 OK 键误打开左侧抽屉
                if (isErrorVisible) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            onRetry()
                            return@onKeyEvent true
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            return@onKeyEvent false
                        }
                        else -> {}
                    }
                }

                // 遥控器上下键切台节流保护：防止长按上/下键时硬件产生密集高频事件 (40ms/次) 导致应用崩溃
                if (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown ||
                    keyEvent.key == Key.PageUp || keyEvent.key == Key.PageDown
                ) {
                    val isRepeat = keyEvent.nativeKeyEvent.repeatCount > 0
                    val now = android.os.SystemClock.uptimeMillis()
                    if (isRepeat) {
                        // 长按连击节流保护：限制为每 250ms 最多响应一次换台
                        if (now - lastChannelSwitchTime < 250L) {
                            return@onKeyEvent true
                        }
                    } else {
                        // 单击防抖保护：至少间隔 100ms
                        if (now - lastChannelSwitchTime < 100L) {
                            return@onKeyEvent true
                        }
                    }
                    lastChannelSwitchTime = now
                }

                // OSD 悬浮条显示时：右键或确认键直接打开 [播放菜单]，左键快捷换线
                if (playerUiState.isOsdVisible) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onSwitchSource()
                            return@onKeyEvent true
                        }
                        Key.DirectionRight -> {
                            osdFocusIndex = 0
                            onToggleEpgSheet()
                            return@onKeyEvent true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onToggleEpgSheet()
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
                        osdFocusIndex = 0
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
                aspectRatio = settingsUiState.aspectRatio,
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
                isSurfaceViewPreferred = settingsUiState.isSurfaceViewPreferred,
                aspectRatio = settingsUiState.aspectRatio,
                videoWidth = playerUiState.metrics.videoWidth,
                videoHeight = playerUiState.metrics.videoHeight,
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

        // 2. 中层：OSD 状态、指标与错误卡片 (带并排线路、画面比例、节目单与手机遥控按钮)
        PlayerOverlay(
            uiState = playerUiState,
            onRetry = onRetry,
            onSwitchSource = onSwitchSource,
            onOpenEpgSchedule = onToggleEpgSheet,
            osdFocusedIndex = if (playerUiState.isOsdVisible) osdFocusIndex else -1,
            aspectRatio = settingsUiState.aspectRatio,
            onCycleAspectRatio = onCycleAspectRatio,
            onOpenSettings = onOpenSettings
        )

        // 3. 顶层侧边栏：频道分类与列表抽屉
        ChannelDrawer(
            uiState = channelUiState,
            onCategorySelected = onCategorySelected,
            onChannelSelected = onChannelSelected,
            onFavoriteToggle = onFavoriteToggle,
            onSearchQueryChanged = onSearchQueryChanged,
            onCloseDrawer = onCloseDrawer,
            onOpenSettings = onOpenSettings
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
            onThemeModeSelected = onThemeModeSelected,
            onToggleBootStartup = onToggleBootStartup,
            onOpenSourceManagement = onOpenSourceManagement,
            onCloseSourceManagement = onCloseSourceManagement,
            onDeleteCustomPlaylistSource = onDeleteCustomPlaylistSource,
            onDeleteSingleCustomChannel = onDeleteSingleCustomChannel,
            onClearAllCustomSources = onClearAllCustomSources,
            onAspectRatioSelected = onAspectRatioSelected,
            onRefreshCustomPlaylistSource = onRefreshCustomPlaylistSource,
            onRefreshAllCustomSources = onRefreshAllCustomSources
        )

        // 5. 模态浮层：综合播放菜单（集成信号源切换、画面比例与今日节目单）
        EpgScheduleDialog(
            visible = playerUiState.isEpgSheetVisible,
            channel = playerUiState.activeChannel,
            programs = playerUiState.dayPrograms,
            onDismiss = onHideEpgSheet,
            aspectRatio = settingsUiState.aspectRatio,
            onAspectRatioSelected = onAspectRatioSelected,
            activeSourceIndex = playerUiState.activeSourceIndex,
            totalSources = playerUiState.totalSources,
            availableSources = playerUiState.availableSources,
            sourceLatenciesMs = playerUiState.sourceLatenciesMs,
            onSwitchSource = onSwitchSource
        )
    }
}
