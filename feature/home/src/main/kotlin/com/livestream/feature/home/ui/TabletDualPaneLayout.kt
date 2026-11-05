package com.livestream.feature.home.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.theme.AppBackground
import com.livestream.core.ui.theme.AppSurface
import com.livestream.core.ui.theme.AppSurfaceElevated
import com.livestream.core.ui.theme.AppTextMuted
import com.livestream.core.ui.theme.AppTextPrimary
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.CategoryTabRow
import com.livestream.feature.channel.ui.ChannelDrawer
import com.livestream.feature.channel.ui.ChannelListItem
import com.livestream.feature.player.PlayerUiState
import com.livestream.feature.player.ui.EpgScheduleDialog
import com.livestream.feature.player.ui.LiveVideoSurface
import com.livestream.feature.player.ui.PlayerOverlay
import com.livestream.feature.player.ui.WebPlaybackContainer
import com.livestream.feature.settings.SettingsUiState
import com.livestream.feature.settings.ui.SettingsDialog
import com.livestream.playback.api.web.WebPlaybackController

/**
 * 平板电脑分栏响应式布局（Tablet Dual-Pane）：
 * 左栏 (360dp)：常驻频道导航、搜索、分类筛选与快捷设置入口
 * 右栏 (自适应宽)：全尺寸播放区域、技术统计浮层与全屏切换
 */
@Composable
fun TabletDualPaneLayout(
    playerUiState: PlayerUiState,
    channelUiState: ChannelUiState,
    settingsUiState: SettingsUiState,
    onSurfaceAttached: (android.view.SurfaceView) -> Unit,
    onSurfaceDetached: () -> Unit,
    onToggleOsd: () -> Unit,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
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
    onOpenDrawer: () -> Unit = {},
    onCloseDrawer: () -> Unit = {},
    onToggleEpgSheet: () -> Unit = {},
    onHideEpgSheet: () -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isFullscreen by remember { mutableStateOf(false) }

    // 平板全屏模式下拦截系统返回键优先退出全屏
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    // 全屏时沉浸式隐藏系统状态栏与导航栏
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 左栏：常驻频道导航与管理面板（全屏时隐藏）
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .background(AppSurface)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部标题与设置
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "频道导航",
                                color = AppTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (playerUiState.activeChannel != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(AppSurfaceElevated, RoundedCornerShape(6.dp))
                                            .clickable { onToggleEpgSheet() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "📅 节目单",
                                            color = FocusHighlight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .background(AppSurfaceElevated, RoundedCornerShape(6.dp))
                                        .clickable { onOpenSettings() }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(text = "⚙ 设置", color = AppTextPrimary, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 搜索框
                        OutlinedTextField(
                            value = channelUiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("搜索频道...", color = AppTextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppSurfaceElevated,
                                unfocusedContainerColor = AppBackground,
                                focusedBorderColor = FocusHighlight,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = AppTextPrimary,
                                unfocusedTextColor = AppTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 分类导航标签栏
                        CategoryTabRow(
                            categories = channelUiState.categories,
                            selectedCategory = channelUiState.selectedCategory,
                            onCategorySelected = onCategorySelected,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 频道列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = channelUiState.channels,
                                key = { it.id }
                            ) { channel ->
                                ChannelListItem(
                                    channel = channel,
                                    isActive = channel.id == channelUiState.activeChannelId,
                                    onChannelClick = onChannelSelected,
                                    onFavoriteToggle = onFavoriteToggle
                                )
                            }
                        }
                    }
                }
            }

            // 2. 右栏：全尺寸播放器区域或官方网页容器
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                val webUrl = playerUiState.officialWebUrl
                if (playerUiState.isOfficialWeb && !webUrl.isNullOrBlank()) {
                    WebPlaybackContainer(
                        controller = webPlaybackController,
                        url = webUrl,
                        modifier = Modifier.fillMaxSize(),
                        onContainerClick = onToggleOsd
                    )
                } else {
                    LiveVideoSurface(
                        onSurfaceAttached = onSurfaceAttached,
                        onSurfaceDetached = onSurfaceDetached,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleOsd
                            )
                    )
                }

                PlayerOverlay(
                    uiState = playerUiState,
                    onRetry = onRetry,
                    onSwitchSource = onSwitchSource,
                    onOpenEpgSchedule = onToggleEpgSheet
                )

                // 平板全屏时的顶部快捷操作条（退出全屏、节目单、频道抽屉、设置）
                if (isFullscreen && playerUiState.isOsdVisible && !channelUiState.isDrawerOpen && !settingsUiState.isSettingsOpen) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(AppSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                .clickable { isFullscreen = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "✕ 退出全屏", color = FocusHighlight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        if (playerUiState.activeChannel != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .background(AppSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                    .clickable { onToggleEpgSheet() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(text = "📅 节目单", color = FocusHighlight, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .background(AppSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                .clickable { onOpenDrawer() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "频道列表", color = AppTextPrimary, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .background(AppSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                .clickable { onOpenSettings() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "⚙ 设置", color = AppTextPrimary, fontSize = 13.sp)
                        }
                    }
                }

                // 平板右下角全屏 / 退出全屏控制按钮
                if (playerUiState.isOsdVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(18.dp)
                            .background(AppSurfaceElevated.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .clickable {
                                isFullscreen = !isFullscreen
                                if (isFullscreen && activity?.resources?.configuration?.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isFullscreen) "⛶ 退出全屏" else "⛶ 平板全屏",
                            color = FocusHighlight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. 全屏侧边抽屉栏
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

        // 4. 设置弹窗
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

        // 5. EPG 节目排期弹窗
        EpgScheduleDialog(
            visible = playerUiState.isEpgSheetVisible,
            channel = playerUiState.activeChannel,
            programs = playerUiState.dayPrograms,
            onDismiss = onHideEpgSheet
        )
    }
}

