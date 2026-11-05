package com.livestream.feature.home.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.ChannelListItem
import com.livestream.feature.player.PlayerUiState
import com.livestream.feature.player.ui.EpgScheduleDialog
import com.livestream.feature.player.ui.LiveVideoSurface
import com.livestream.feature.player.ui.PlayerOverlay
import com.livestream.feature.player.ui.WebPlaybackContainer
import com.livestream.feature.settings.SettingsUiState
import com.livestream.feature.settings.ui.SettingsContent
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
    onToggleBootStartup: (Boolean) -> Unit = {},
    onOpenSourceManagement: () -> Unit = {},
    onCloseSourceManagement: () -> Unit = {},
    onDeleteCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onDeleteSingleCustomChannel: (String) -> Unit = {},
    onClearAllCustomSources: () -> Unit = {},
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isSettingsViewActive by remember { mutableStateOf(false) }

    BackHandler(enabled = isSettingsViewActive) {
        isSettingsViewActive = false
        onCloseSettings()
    }

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

                // 右上角全屏胶囊药丸按钮 (点击画面唤出 OSD 出现，无任何图标)
                if (playerUiState.isOsdVisible) {
                    val fsPillShape = OneUiShapes.pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), fsPillShape)
                            .background(Color(0xCC101114), fsPillShape)
                            .clickable {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
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

            // 2. 下半部交互区 (Interaction Area)：紧凑 One UI 排版
            if (isSettingsViewActive) {
                // 设置展示区（展示在节目所在的这一块区域）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // 顶部导航栏：返回节目列表与标题
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(OneUiShapes.pill)
                                    .background(OneUiTheme.colors.cardSecondary)
                                    .clickable {
                                        isSettingsViewActive = false
                                        onCloseSettings()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "‹ 节目列表",
                                    color = OneUiTheme.colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "系统与设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val settingsScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(settingsScrollState)
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                    ) {
                        SettingsContent(
                            uiState = settingsUiState,
                            onOpenImportDialog = onOpenImportDialog,
                            onOpenSourceManagement = onOpenSourceManagement,
                            onThemeModeSelected = onThemeModeSelected,
                            onToggleHardwareDecode = onToggleHardwareDecode,
                            onToggleSurfaceView = onToggleSurfaceView,
                            onToggleBootStartup = onToggleBootStartup
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // 原有频道列表交互区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // One UI 标题与分类快速切换器 (‹ 分类名 ›) + 设置入口
                    val categories = channelUiState.categories
                    val currentCategoryIndex = categories.indexOfFirst { it.id == channelUiState.selectedCategory?.id }
                    val currentCategoryTitle = channelUiState.selectedCategory?.name ?: "全部频道"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (categories.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(OneUiShapes.pill)
                                        .background(OneUiTheme.colors.surfacePill)
                                        .clickable {
                                            val prevIdx = if (currentCategoryIndex <= 0) categories.size - 1 else currentCategoryIndex - 1
                                            onCategorySelected(categories[prevIdx])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "‹",
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = currentCategoryTitle,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "共 ${channelUiState.channels.size} 个频道",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            if (categories.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(OneUiShapes.pill)
                                        .background(OneUiTheme.colors.surfacePill)
                                        .clickable {
                                            val nextIdx = if (currentCategoryIndex >= categories.size - 1) 0 else currentCategoryIndex + 1
                                            onCategorySelected(categories[nextIdx])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "›",
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 设置快捷胶囊
                        Box(
                            modifier = Modifier
                                .clip(OneUiShapes.pill)
                                .background(OneUiTheme.colors.cardSecondary)
                                .clickable {
                                    isSettingsViewActive = true
                                    onOpenSettings()
                                }
                                .padding(horizontal = 14.dp, vertical = 5.dp),
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // 紧凑药丸搜索框 (Pill Search Box)
                    val searchShape = OneUiShapes.pill
                    OutlinedTextField(
                        value = channelUiState.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = {
                            Text(
                                text = "搜索频道...",
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 12.sp
                            )
                        },
                        trailingIcon = {
                            if (channelUiState.searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(OneUiShapes.pill)
                                        .clickable { onSearchQueryChanged("") }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "清空",
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 11.sp,
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // 频道列表 (直通到底部导航区域，充实紧凑)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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

        // 设置弹窗 (竖屏模式下 isSettingsOpen 置为 false，避免在右侧展开侧边抽屉，但保留导入对话框与自定义源管理对话框)
        SettingsDialog(
            uiState = settingsUiState.copy(isSettingsOpen = false),
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
            onClearAllCustomSources = onClearAllCustomSources
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
