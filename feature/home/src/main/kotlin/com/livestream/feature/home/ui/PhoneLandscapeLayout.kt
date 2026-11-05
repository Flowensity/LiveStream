package com.livestream.feature.home.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.ChannelDrawer
import com.livestream.feature.player.PlayerUiState
import com.livestream.feature.player.gesture.DeviceControlHelper
import com.livestream.feature.player.gesture.playerTouchGestures
import com.livestream.feature.player.ui.EpgScheduleDialog
import com.livestream.feature.player.ui.GestureHud
import com.livestream.feature.player.ui.GestureHudState
import com.livestream.feature.player.ui.LiveVideoSurface
import com.livestream.feature.player.ui.PlayerOverlay
import com.livestream.feature.player.ui.WebPlaybackContainer
import com.livestream.feature.settings.SettingsUiState
import com.livestream.feature.settings.ui.SettingsDialog
import com.livestream.playback.api.web.WebPlaybackController

/**
 * 手机横屏全屏模式：
 * 1. 沉浸式全屏播放
 * 2. 左屏滑动调节屏幕亮度，右屏滑动调节系统音量
 * 3. 顶部快速退出全屏按钮
 * 4. 侧边栏与设置抽屉无缝呼出
 */
@Composable
fun PhoneLandscapeLayout(
    playerUiState: PlayerUiState,
    channelUiState: ChannelUiState,
    settingsUiState: SettingsUiState,
    onSurfaceAttached: (android.view.SurfaceView) -> Unit,
    onSurfaceDetached: () -> Unit,
    onToggleOsd: () -> Unit,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onCategorySelected: (ChannelCategory) -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
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
    val context = LocalContext.current
    val activity = context as? Activity
    val controlHelper = remember { DeviceControlHelper(context) }

    var gestureHudState by remember { mutableStateOf<GestureHudState>(GestureHudState.Hidden) }
    var currentBrightness by remember { mutableFloatStateOf(0.7f) }

    val gestureModifier = Modifier.playerTouchGestures(
        onTap = {
            if (channelUiState.isDrawerOpen) {
                onCloseDrawer()
            } else if (settingsUiState.isSettingsOpen) {
                onCloseSettings()
            } else if (playerUiState.isEpgSheetVisible) {
                onHideEpgSheet()
            } else {
                onToggleOsd()
            }
        },
        onDoubleTap = {
            onSwitchSource()
        },
        onLeftVerticalDrag = { deltaY ->
            currentBrightness = controlHelper.adjustBrightnessByDelta(activity, deltaY, currentBrightness)
            val percent = (currentBrightness * 100).toInt()
            gestureHudState = GestureHudState.Brightness(percent)
        },
        onRightVerticalDrag = { deltaY ->
            val percent = controlHelper.adjustVolumeByDelta(deltaY)
            gestureHudState = GestureHudState.Volume(percent)
        },
        onDragEnd = {
            gestureHudState = GestureHudState.Hidden
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. 底层：视频表面或官方网页容器
        val webUrl = playerUiState.officialWebUrl
        if (playerUiState.isOfficialWeb && !webUrl.isNullOrBlank()) {
            WebPlaybackContainer(
                controller = webPlaybackController,
                url = webUrl,
                modifier = Modifier.fillMaxSize(),
                overlayModifier = gestureModifier
            )
        } else {
            LiveVideoSurface(
                onSurfaceAttached = onSurfaceAttached,
                onSurfaceDetached = onSurfaceDetached,
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
            )
        }

        // 2. 中层：OSD 状态面板
        PlayerOverlay(
            uiState = playerUiState,
            onRetry = onRetry,
            onSwitchSource = onSwitchSource,
            onOpenEpgSchedule = onToggleEpgSheet
        )

        // 3. 手势悬浮 HUD（音量/亮度调节提示）
        GestureHud(
            state = gestureHudState,
            modifier = Modifier.align(Alignment.Center)
        )

        // 4. 顶部操作快捷栏（全屏退出、频道菜单、设置）
        if (playerUiState.isOsdVisible && !channelUiState.isDrawerOpen && !settingsUiState.isSettingsOpen) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(DarkSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .clickable {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "✕ 退出全屏", color = FocusHighlight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (playerUiState.dayPrograms.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .background(DarkSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .clickable { onToggleEpgSheet() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "📅 节目单", color = FocusHighlight, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .background(DarkSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .clickable { onOpenDrawer() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "频道列表", color = TextPrimary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .background(DarkSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "⚙ 设置", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }

        // 5. 左侧频道抽屉
        ChannelDrawer(
            uiState = channelUiState,
            onCategorySelected = onCategorySelected,
            onChannelSelected = {
                onChannelSelected(it)
                onCloseDrawer()
            },
            onFavoriteToggle = onFavoriteToggle,
            onSearchQueryChanged = onSearchQueryChanged,
            onCloseDrawer = onCloseDrawer
        )

        // 6. 系统设置弹窗
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

        // 7. EPG 节目排期弹窗
        EpgScheduleDialog(
            visible = playerUiState.isEpgSheetVisible,
            channel = playerUiState.activeChannel,
            programs = playerUiState.dayPrograms,
            onDismiss = onHideEpgSheet
        )
    }
}
