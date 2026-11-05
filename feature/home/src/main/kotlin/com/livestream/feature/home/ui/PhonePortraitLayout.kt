package com.livestream.feature.home.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.theme.OneUiTheme
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
 * Samsung One UI 手机竖屏核心布局：
 * 1. 视区 (Viewing Area) 与交互区 (Interaction Area) 严密划分，注重舒适单手操作
 * 2. 播放窗采用圆角卡片包裹 (20.dp)
 * 3. 醒目的一体化大标题与信息层级
 * 4. One UI 标志性药丸全圆角搜索框 (Pill Search Bar) 与分类药丸筛选栏
 * 5. 底部保留舒适的 One UI 沉浸式多功能 Tab 导航栏
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
            .background(OneUiTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. 顶部视区 (Viewing Area)：16:9 播放窗口，带 One UI 优雅圆角下沿
            val playerCornerShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(playerCornerShape)
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

                // 右下角全屏胶囊药丸按钮 (点击画面唤出 OSD 出现，无任何图标)
                if (playerUiState.isOsdVisible) {
                    val fsPillShape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), fsPillShape)
                            .background(Color(0xCC101114), fsPillShape)
                            .clickable {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "全屏",
                            color = OneUiTheme.colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. 下半部交互区 (Interaction Area)：One UI 大标题、纯净搜索框、药丸分类与频道流
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // One UI 大标题与快捷胶囊栏 (Viewing Area Header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = channelUiState.selectedCategory?.name ?: "直播频道",
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "共 ${channelUiState.channels.size} 个频道 · 触控即播",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 设置胶囊
                        val settingsPillShape = RoundedCornerShape(50)
                        Box(
                            modifier = Modifier
                                .clip(settingsPillShape)
                                .background(OneUiTheme.colors.cardSecondary)
                                .clickable { onOpenSettings() }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // One UI 标志性药丸胶囊搜索框 (Pill Search Box - 无任何图标)
                val searchShape = RoundedCornerShape(24.dp)
                OutlinedTextField(
                    value = channelUiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = "搜索频道名称或编号...",
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        if (channelUiState.searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onSearchQueryChanged("") }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "清空",
                                    color = OneUiTheme.colors.textMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OneUiTheme.colors.cardSecondary,
                        unfocusedContainerColor = OneUiTheme.colors.cardSecondary,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = OneUiTheme.colors.textPrimary,
                        unfocusedTextColor = OneUiTheme.colors.textPrimary
                    ),
                    shape = searchShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 横向滑动分类药丸栏
                CategoryTabRow(
                    categories = channelUiState.categories,
                    selectedCategory = channelUiState.selectedCategory,
                    onCategorySelected = onCategorySelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 频道卡片列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

            // 3. One UI 底部导航栏 (Samsung 纯文字 Segmented Pill Tab Bar - 无任何图标)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, OneUiTheme.colors.divider))
                    .background(OneUiTheme.colors.cardBackground)
                    .navigationBarsPadding()
                    .padding(vertical = 10.dp, horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 1: 频道 (选中态)
                    val activePillShape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .clip(activePillShape)
                            .background(OneUiTheme.colors.accentSoft)
                            .padding(horizontal = 28.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "频道",
                            color = OneUiTheme.colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tab 2: 设置
                    val inactivePillShape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .clip(inactivePillShape)
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 28.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "设置",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
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
