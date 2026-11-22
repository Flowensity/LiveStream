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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.model.VideoAspectRatio
import com.livestream.core.ui.component.OneUiSearchBar
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.CategoryTabRow
import com.livestream.feature.channel.ui.ChannelEmptyState
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
 * 现代 One UI 手机竖屏核心布局：
 * 1. 顶部视区 (Viewing Area)：16:9 播放窗口，带圆角下沿与全屏快捷胶囊
 * 2. 下半部交互区 (Interaction Area)：大标题、一键导入源与设置快捷入口、分类药丸栏、统一搜索框与列表
 * 3. 完善的场景化空状态反馈与一键清空搜索
 */
@Composable
fun PhonePortraitLayout(
    playerUiState: PlayerUiState,
    channelUiState: ChannelUiState,
    settingsUiState: SettingsUiState,
    onSurfaceAttached: (android.view.View) -> Unit,
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
    onOpenSourceManagement: () -> Unit = {},
    onCloseSourceManagement: () -> Unit = {},
    onDeleteCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onDeleteSingleCustomChannel: (String) -> Unit = {},
    onClearAllCustomSources: () -> Unit = {},
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
    onRefreshCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onRefreshAllCustomSources: () -> Unit = {},
    onSelectSource: (Int) -> Unit = {},
    onProbeCustomSources: () -> Unit = {},
    onRemoveUnavailableCustomChannels: () -> Unit = {},
    onTogglePipMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isSettingsViewActive = settingsUiState.isSettingsOpen

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
            // 1. 顶部视区 (Viewing Area)：16:9 播放窗口
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
                        aspectRatio = settingsUiState.aspectRatio,
                        modifier = Modifier.fillMaxSize(),
                        onContainerClick = onToggleOsd
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
                                indication = null,
                                onClick = onToggleOsd
                            )
                    )
                }

                PlayerOverlay(
                    uiState = playerUiState,
                    onRetry = onRetry,
                    onSwitchSource = onSwitchSource,
                    onOpenEpgSchedule = onToggleEpgSheet,
                    aspectRatio = settingsUiState.aspectRatio,
                    onCycleAspectRatio = onCycleAspectRatio
                )

                // 右上角全屏胶囊按钮
                if (playerUiState.isOsdVisible && playerUiState.quickChannelNumber.isEmpty()) {
                    val fsPillShape = OneUiShapes.pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), fsPillShape)
                            .background(Color(0xD912151E), fsPillShape)
                            .clip(fsPillShape)
                            .clickable {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⤢ 全屏",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. 下半部交互区 (Interaction Area)
            if (isSettingsViewActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 顶部导航栏：返回频道列表与设置标题
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
                            TvFocusableBox(
                                onClick = onCloseSettings,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp
                            ) { isFocused ->
                                Text(
                                    text = "‹ 返回频道",
                                    color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.accent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

                    Spacer(modifier = Modifier.height(10.dp))

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
                            onTogglePipMode = onTogglePipMode,
                            isTv = false
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // 频道列表交互区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

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
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (categories.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                                        .clip(OneUiShapes.pill)
                                        .clickable {
                                            val prevIdx = if (currentCategoryIndex <= 0) categories.size - 1 else currentCategoryIndex - 1
                                            onCategorySelected(categories[prevIdx])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(OneUiShapes.pill)
                                            .background(OneUiTheme.colors.surfacePill),
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
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = currentCategoryTitle,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "共 ${channelUiState.channels.size} 个频道",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }

                            if (categories.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                                        .clip(OneUiShapes.pill)
                                        .clickable {
                                            val nextIdx = if (currentCategoryIndex >= categories.size - 1) 0 else currentCategoryIndex + 1
                                            onCategorySelected(categories[nextIdx])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(OneUiShapes.pill)
                                            .background(OneUiTheme.colors.surfacePill),
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
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 快捷导入与设置胶囊组（减少操作层级，重要功能一步直达）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TvFocusableBox(
                                onClick = onOpenImportDialog,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp
                            ) { isFocused ->
                                Text(
                                    text = "+ 导入源",
                                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            TvFocusableBox(
                                onClick = onOpenSettings,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp,
                                unfocusedBorderColor = OneUiTheme.colors.borderSubtle
                            ) { isFocused ->
                                Text(
                                    text = "设置",
                                    color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (categories.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CategoryTabRow(
                            categories = categories,
                            selectedCategory = channelUiState.selectedCategory,
                            onCategorySelected = onCategorySelected,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 统一药丸搜索框 (OneUiSearchBar)
                    OneUiSearchBar(
                        query = channelUiState.searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        placeholder = "搜索频道名称或台号...",
                        height = 40.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 频道列表或空状态
                    if (channelUiState.channels.isEmpty()) {
                        ChannelEmptyState(
                            searchQuery = channelUiState.searchQuery,
                            selectedCategoryId = channelUiState.selectedCategory?.id,
                            onClearSearch = { onSearchQueryChanged("") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .navigationBarsPadding()
                        )
                    } else {
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
        }

        // 设置内嵌弹窗（导入 M3U 播放列表与自定义源管理）
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
            onOpenSourceManagement = onOpenSourceManagement,
            onCloseSourceManagement = onCloseSourceManagement,
            onDeleteCustomPlaylistSource = onDeleteCustomPlaylistSource,
            onDeleteSingleCustomChannel = onDeleteSingleCustomChannel,
            onClearAllCustomSources = onClearAllCustomSources,
            onAspectRatioSelected = onAspectRatioSelected,
            onRefreshCustomPlaylistSource = onRefreshCustomPlaylistSource,
            onRefreshAllCustomSources = onRefreshAllCustomSources,
            onProbeCustomSources = onProbeCustomSources,
            onRemoveUnavailableCustomChannels = onRemoveUnavailableCustomChannels,
            onTogglePipMode = onTogglePipMode
        )

        // 综合播放设置与节目单抽屉
        EpgScheduleDialog(
            visible = playerUiState.isEpgSheetVisible,
            channel = playerUiState.activeChannel,
            programs = playerUiState.dayPrograms,
            isEpgLoading = playerUiState.isEpgLoading,
            onDismiss = onHideEpgSheet,
            aspectRatio = settingsUiState.aspectRatio,
            onAspectRatioSelected = onAspectRatioSelected,
            activeSourceIndex = playerUiState.activeSourceIndex,
            totalSources = playerUiState.totalSources,
            availableSources = playerUiState.availableSources,
            sourceLatenciesMs = playerUiState.sourceLatenciesMs,
            onSwitchSource = onSwitchSource,
            onSelectSource = onSelectSource
        )
    }
}
