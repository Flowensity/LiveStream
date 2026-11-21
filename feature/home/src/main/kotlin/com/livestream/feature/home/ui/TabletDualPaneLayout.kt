package com.livestream.feature.home.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.livestream.core.ui.theme.AppBackground
import com.livestream.core.ui.theme.AppSurface
import com.livestream.core.ui.theme.AppTextPrimary
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.core.ui.util.SystemBarsUtil
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.CategoryTabRow
import com.livestream.feature.channel.ui.ChannelDrawer
import com.livestream.feature.channel.ui.ChannelEmptyState
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
 * 平板电脑大屏分栏响应式布局（Tablet Dual-Pane）：
 * 左栏 (360dp)：常驻频道导航、搜索、分类筛选与快捷导入/设置入口（含系统状态栏安全边距）
 * 右栏 (自适应宽)：全尺寸播放区域、播放状态控制浮层与右上角全屏切换
 */
@Composable
fun TabletDualPaneLayout(
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
    onOpenDrawer: () -> Unit = {},
    onCloseDrawer: () -> Unit = {},
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

    // 平板全屏时沉浸式隐藏系统状态栏与导航栏
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        SystemBarsUtil.setImmersiveFullscreen(window, isFullscreen)
        onDispose {
            SystemBarsUtil.setImmersiveFullscreen(window, false)
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
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val categories = channelUiState.categories
                        val currentCategoryIndex = categories.indexOfFirst { it.id == channelUiState.selectedCategory?.id }
                        val currentCategoryTitle = channelUiState.selectedCategory?.name ?: "全部频道"

                        // 顶部标题与分类快速切换 (‹ 分类名 ›) + 导入/设置
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        color = AppTextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
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
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        if (categories.size > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                            CategoryTabRow(
                                categories = categories,
                                selectedCategory = channelUiState.selectedCategory,
                                onCategorySelected = onCategorySelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 统一药丸搜索框 (OneUiSearchBar)
                        OneUiSearchBar(
                            query = channelUiState.searchQuery,
                            onQueryChange = onSearchQueryChanged,
                            placeholder = "搜索频道名称或台号...",
                            height = 40.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 频道列表或空状态
                        if (channelUiState.channels.isEmpty()) {
                            ChannelEmptyState(
                                searchQuery = channelUiState.searchQuery,
                                selectedCategoryId = channelUiState.selectedCategory?.id,
                                onClearSearch = { onSearchQueryChanged("") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
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

                // 分栏垂直细分割线
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(OneUiTheme.colors.borderSubtle)
                )
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

                // 右上角全屏切换与快捷操作栏（统一胶囊视觉语言，避免与底部 OSD 发生重叠）
                if (playerUiState.isOsdVisible &&
                    !channelUiState.isDrawerOpen &&
                    !settingsUiState.isSettingsOpen &&
                    !playerUiState.isEpgSheetVisible &&
                    playerUiState.quickChannelNumber.isEmpty()
                ) {
                    val pillShape = OneUiShapes.pill
                    val btnBg = Color(0xD912151E)
                    val btnBorder = BorderStroke(1.dp, Color(0x33FFFFFF))

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .border(btnBorder, pillShape)
                                .background(btnBg, pillShape)
                                .clip(pillShape)
                                .clickable {
                                    isFullscreen = !isFullscreen
                                    if (isFullscreen && activity?.resources?.configuration?.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else if (!isFullscreen) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (isFullscreen) "退出全屏" else "⤢ 全屏观看",
                                color = if (isFullscreen) OneUiTheme.colors.accent else Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isFullscreen) {
                            Box(
                                modifier = Modifier
                                    .border(btnBorder, pillShape)
                                    .background(btnBg, pillShape)
                                    .clip(pillShape)
                                    .clickable { onOpenDrawer() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "频道列表",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .border(btnBorder, pillShape)
                                    .background(btnBg, pillShape)
                                    .clip(pillShape)
                                    .clickable { onOpenSettings() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "设置",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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
            onCloseDrawer = onCloseDrawer,
            onOpenSettings = onOpenSettings
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
            onRemoveUnavailableCustomChannels = onRemoveUnavailableCustomChannels
        )

        // 5. 综合播放设置与今日节目单抽屉
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
