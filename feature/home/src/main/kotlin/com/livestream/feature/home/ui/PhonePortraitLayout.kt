package com.livestream.feature.home.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.AppBackground
import com.livestream.core.ui.theme.AppSurface
import com.livestream.core.ui.theme.AppSurfaceElevated
import com.livestream.core.ui.theme.AppTextMuted
import com.livestream.core.ui.theme.AppTextPrimary
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.CategoryTabRow
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
 * 手机竖屏专用布局：
 * 上半区：16:9 播放窗口 + 浮层控制 + 全屏旋转按钮
 * 下半区：频道分类导航条 + 搜索框 + 频道列表
 */
@Composable
fun PhonePortraitLayout(
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
    onToggleEpgSheet: () -> Unit = {},
    onHideEpgSheet: () -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // 1. 上半部：16:9 原生播放窗口或官方网页容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
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

            // 右下角进入横屏全屏按钮（点击画面即可唤出 OSD 看到本按钮）
            if (playerUiState.isOsdVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .background(AppSurfaceElevated.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .clickable {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⛶ 全屏",
                        color = FocusHighlight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. 下半部：频道与分类导航流
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            // 顶部常驻操作工具栏：包含标题、节目单入口与设置入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "频道列表 (${channelUiState.channels.size})",
                    color = AppTextPrimary,
                    fontSize = 16.sp,
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
                                .padding(horizontal = 10.dp, vertical = 6.dp)
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
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚙ 设置",
                            color = AppTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            // 搜索框
            OutlinedTextField(
                value = channelUiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("搜索频道...", color = AppTextMuted, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppSurfaceElevated,
                    unfocusedContainerColor = AppSurface,
                    focusedBorderColor = FocusHighlight,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = AppTextPrimary,
                    unfocusedTextColor = AppTextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 横向滑动分类栏
            CategoryTabRow(
                categories = channelUiState.categories,
                selectedCategory = channelUiState.selectedCategory,
                onCategorySelected = onCategorySelected,
                modifier = Modifier.fillMaxWidth()
            )

            // 频道列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
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

    // 设置弹窗
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

        // EPG 节目单排期弹窗
        EpgScheduleDialog(
            visible = playerUiState.isEpgSheetVisible,
            channel = playerUiState.activeChannel,
            programs = playerUiState.dayPrograms,
            onDismiss = onHideEpgSheet
        )
    }
}
