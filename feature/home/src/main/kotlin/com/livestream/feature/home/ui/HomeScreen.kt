package com.livestream.feature.home.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.livestream.core.ui.device.DeviceType
import com.livestream.core.ui.device.rememberDeviceType
import com.livestream.core.ui.theme.AppBackground
import com.livestream.feature.channel.ChannelViewModel
import com.livestream.feature.home.HomeViewModel
import com.livestream.feature.player.PlayerViewModel
import com.livestream.feature.settings.SettingsViewModel

/**
 * LiveStream 多端自适应主界面容器：
 * 根据系统环境与屏幕形态自动分发至：
 * 1. TV 专属遥控器全屏界面 (TvLayout)
 * 2. 手机竖屏半屏列表界面 (PhonePortraitLayout)
 * 3. 手机横屏手势全屏界面 (PhoneLandscapeLayout)
 * 4. 平板大屏双栏分屏界面 (TabletDualPaneLayout)
 */
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

    val deviceType = rememberDeviceType()
    val activity = LocalContext.current as? Activity

    var showExitDialog by remember { mutableStateOf(false) }

    // 启动自动播放
    LaunchedEffect(Unit) {
        homeViewModel.initStartupPlayback()
    }

    // 系统返回键分级消费与二次退出确认弹窗
    BackHandler(enabled = true) {
        when {
            showExitDialog -> showExitDialog = false
            settingsUiState.isImportDialogOpen -> settingsViewModel.closeImportDialog()
            settingsUiState.isSettingsOpen -> settingsViewModel.closeSettings()
            channelUiState.isDrawerOpen -> channelViewModel.closeDrawer()
            playerUiState.isEpgSheetVisible -> playerViewModel.hideEpgSheet()
            playerUiState.isOsdVisible -> playerViewModel.hideOsd()
            else -> {
                showExitDialog = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        when (deviceType) {
        DeviceType.PHONE_PORTRAIT -> {
            PhonePortraitLayout(
                playerUiState = playerUiState,
                channelUiState = channelUiState,
                settingsUiState = settingsUiState,
                onSurfaceAttached = { playerViewModel.attachSurface(it) },
                onSurfaceDetached = { playerViewModel.detachSurface() },
                onToggleOsd = { playerViewModel.toggleOsd() },
                onRetry = { playerViewModel.retry() },
                onSwitchSource = { playerViewModel.switchNextSource() },
                onCategorySelected = { channelViewModel.selectCategory(it) },
                onChannelSelected = {
                    homeViewModel.playChannel(it)
                    channelViewModel.selectChannel(it)
                },
                onFavoriteToggle = { channelViewModel.toggleFavorite(it) },
                onSearchQueryChanged = { channelViewModel.onSearchQueryChanged(it) },
                onOpenSettings = { settingsViewModel.openSettings() },
                onCloseSettings = { settingsViewModel.closeSettings() },
                onToggleHardwareDecode = { settingsViewModel.setHardwareDecodePreferred(it) },
                onToggleSurfaceView = { settingsViewModel.setSurfaceViewPreferred(it) },
                onOpenImportDialog = { settingsViewModel.openImportDialog() },
                onCloseImportDialog = { settingsViewModel.closeImportDialog() },
                onImportTextChanged = { settingsViewModel.onImportTextChanged(it) },
                onConfirmImport = { settingsViewModel.importM3uContent() },
                onToggleEpgSheet = { playerViewModel.toggleEpgSheet() },
                onHideEpgSheet = { playerViewModel.hideEpgSheet() },
                onThemeModeSelected = { settingsViewModel.setThemeMode(it) },
                onToggleBootStartup = { settingsViewModel.setBootStartupEnabled(it) },
                onOpenSourceManagement = { settingsViewModel.openSourceManagement() },
                onCloseSourceManagement = { settingsViewModel.closeSourceManagement() },
                onDeleteCustomPlaylistSource = { settingsViewModel.deleteCustomPlaylistSource(it) },
                onDeleteSingleCustomChannel = { settingsViewModel.deleteSingleCustomChannel(it) },
                onClearAllCustomSources = { settingsViewModel.clearAllCustomSources() },
                webPlaybackController = playerViewModel.webPlaybackController,
                modifier = modifier
            )
        }
        DeviceType.PHONE_LANDSCAPE -> {
            PhoneLandscapeLayout(
                playerUiState = playerUiState,
                channelUiState = channelUiState,
                settingsUiState = settingsUiState,
                onSurfaceAttached = { playerViewModel.attachSurface(it) },
                onSurfaceDetached = { playerViewModel.detachSurface() },
                onToggleOsd = { playerViewModel.toggleOsd() },
                onRetry = { playerViewModel.retry() },
                onSwitchSource = { playerViewModel.switchNextSource() },
                onOpenDrawer = { channelViewModel.openDrawer() },
                onCloseDrawer = { channelViewModel.closeDrawer() },
                onCategorySelected = { channelViewModel.selectCategory(it) },
                onChannelSelected = {
                    homeViewModel.playChannel(it)
                    channelViewModel.selectChannel(it)
                },
                onFavoriteToggle = { channelViewModel.toggleFavorite(it) },
                onSearchQueryChanged = { channelViewModel.onSearchQueryChanged(it) },
                onOpenSettings = { settingsViewModel.openSettings() },
                onCloseSettings = { settingsViewModel.closeSettings() },
                onToggleHardwareDecode = { settingsViewModel.setHardwareDecodePreferred(it) },
                onToggleSurfaceView = { settingsViewModel.setSurfaceViewPreferred(it) },
                onOpenImportDialog = { settingsViewModel.openImportDialog() },
                onCloseImportDialog = { settingsViewModel.closeImportDialog() },
                onImportTextChanged = { settingsViewModel.onImportTextChanged(it) },
                onConfirmImport = { settingsViewModel.importM3uContent() },
                onToggleEpgSheet = { playerViewModel.toggleEpgSheet() },
                onHideEpgSheet = { playerViewModel.hideEpgSheet() },
                onThemeModeSelected = { settingsViewModel.setThemeMode(it) },
                onToggleBootStartup = { settingsViewModel.setBootStartupEnabled(it) },
                onOpenSourceManagement = { settingsViewModel.openSourceManagement() },
                onCloseSourceManagement = { settingsViewModel.closeSourceManagement() },
                onDeleteCustomPlaylistSource = { settingsViewModel.deleteCustomPlaylistSource(it) },
                onDeleteSingleCustomChannel = { settingsViewModel.deleteSingleCustomChannel(it) },
                onClearAllCustomSources = { settingsViewModel.clearAllCustomSources() },
                webPlaybackController = playerViewModel.webPlaybackController,
                modifier = modifier
            )
        }
        DeviceType.TABLET -> {
            TabletDualPaneLayout(
                playerUiState = playerUiState,
                channelUiState = channelUiState,
                settingsUiState = settingsUiState,
                onSurfaceAttached = { playerViewModel.attachSurface(it) },
                onSurfaceDetached = { playerViewModel.detachSurface() },
                onToggleOsd = { playerViewModel.toggleOsd() },
                onRetry = { playerViewModel.retry() },
                onSwitchSource = { playerViewModel.switchNextSource() },
                onCategorySelected = { channelViewModel.selectCategory(it) },
                onChannelSelected = {
                    homeViewModel.playChannel(it)
                    channelViewModel.selectChannel(it)
                },
                onFavoriteToggle = { channelViewModel.toggleFavorite(it) },
                onSearchQueryChanged = { channelViewModel.onSearchQueryChanged(it) },
                onOpenSettings = { settingsViewModel.openSettings() },
                onCloseSettings = { settingsViewModel.closeSettings() },
                onToggleHardwareDecode = { settingsViewModel.setHardwareDecodePreferred(it) },
                onToggleSurfaceView = { settingsViewModel.setSurfaceViewPreferred(it) },
                onOpenImportDialog = { settingsViewModel.openImportDialog() },
                onCloseImportDialog = { settingsViewModel.closeImportDialog() },
                onImportTextChanged = { settingsViewModel.onImportTextChanged(it) },
                onConfirmImport = { settingsViewModel.importM3uContent() },
                onOpenDrawer = { channelViewModel.openDrawer() },
                onCloseDrawer = { channelViewModel.closeDrawer() },
                onToggleEpgSheet = { playerViewModel.toggleEpgSheet() },
                onHideEpgSheet = { playerViewModel.hideEpgSheet() },
                onThemeModeSelected = { settingsViewModel.setThemeMode(it) },
                onToggleBootStartup = { settingsViewModel.setBootStartupEnabled(it) },
                onOpenSourceManagement = { settingsViewModel.openSourceManagement() },
                onCloseSourceManagement = { settingsViewModel.closeSourceManagement() },
                onDeleteCustomPlaylistSource = { settingsViewModel.deleteCustomPlaylistSource(it) },
                onDeleteSingleCustomChannel = { settingsViewModel.deleteSingleCustomChannel(it) },
                onClearAllCustomSources = { settingsViewModel.clearAllCustomSources() },
                webPlaybackController = playerViewModel.webPlaybackController,
                modifier = modifier
            )
        }
        DeviceType.TV -> {
            TvLayout(
                playerUiState = playerUiState,
                channelUiState = channelUiState,
                settingsUiState = settingsUiState,
                onSurfaceAttached = { playerViewModel.attachSurface(it) },
                onSurfaceDetached = { playerViewModel.detachSurface() },
                onToggleOsd = { playerViewModel.toggleOsd() },
                onShowOsd = { playerViewModel.showOsd() },
                onHideOsd = { playerViewModel.hideOsd() },
                onRetry = { playerViewModel.retry() },
                onSwitchSource = { playerViewModel.switchNextSource() },
                onSwitchNextChannel = {
                    homeViewModel.switchNextChannel()
                    playerViewModel.showOsd()
                },
                onSwitchPreviousChannel = {
                    homeViewModel.switchPreviousChannel()
                    playerViewModel.showOsd()
                },
                onNumberKeyInput = { playerViewModel.onNumberKeyInput(it) },
                onOpenDrawer = { channelViewModel.openDrawer() },
                onCloseDrawer = { channelViewModel.closeDrawer() },
                onCategorySelected = { channelViewModel.selectCategory(it) },
                onChannelSelected = {
                    homeViewModel.playChannel(it)
                    channelViewModel.selectChannel(it)
                },
                onFavoriteToggle = { channelViewModel.toggleFavorite(it) },
                onSearchQueryChanged = { channelViewModel.onSearchQueryChanged(it) },
                onOpenSettings = { settingsViewModel.openSettings() },
                onCloseSettings = { settingsViewModel.closeSettings() },
                onToggleSettings = { settingsViewModel.toggleSettings() },
                onToggleHardwareDecode = { settingsViewModel.setHardwareDecodePreferred(it) },
                onToggleSurfaceView = { settingsViewModel.setSurfaceViewPreferred(it) },
                onOpenImportDialog = { settingsViewModel.openImportDialog() },
                onCloseImportDialog = { settingsViewModel.closeImportDialog() },
                onImportTextChanged = { settingsViewModel.onImportTextChanged(it) },
                onConfirmImport = { settingsViewModel.importM3uContent() },
                onToggleEpgSheet = { playerViewModel.toggleEpgSheet() },
                onHideEpgSheet = { playerViewModel.hideEpgSheet() },
                onThemeModeSelected = { settingsViewModel.setThemeMode(it) },
                onToggleBootStartup = { settingsViewModel.setBootStartupEnabled(it) },
                onOpenSourceManagement = { settingsViewModel.openSourceManagement() },
                onCloseSourceManagement = { settingsViewModel.closeSourceManagement() },
                onDeleteCustomPlaylistSource = { settingsViewModel.deleteCustomPlaylistSource(it) },
                onDeleteSingleCustomChannel = { settingsViewModel.deleteSingleCustomChannel(it) },
                onClearAllCustomSources = { settingsViewModel.clearAllCustomSources() },
                webPlaybackController = playerViewModel.webPlaybackController,
                modifier = modifier
            )
        }
    }

        // 退出确认二次弹窗
        ExitConfirmDialog(
            visible = showExitDialog,
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }
}
