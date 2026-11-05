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
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.DarkSurface
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.feature.channel.ChannelUiState
import com.livestream.feature.channel.ui.CategoryTabRow
import com.livestream.feature.channel.ui.ChannelListItem
import com.livestream.feature.player.PlayerUiState
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
    modifier: Modifier = Modifier,
    webPlaybackController: WebPlaybackController? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
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
                    modifier = Modifier.fillMaxSize()
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
                onSwitchSource = onSwitchSource
            )

            // 右下角进入横屏全屏按钮
            if (playerUiState.isOsdVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(DarkSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .clickable {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⛶ 全屏",
                        color = FocusHighlight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 右上角设置入口按钮
            if (playerUiState.isOsdVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(DarkSurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚙ 设置",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. 下半部：频道与分类导航流
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp)
        ) {
            // 搜索框
            OutlinedTextField(
                value = channelUiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("搜索频道...", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurfaceElevated,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = FocusHighlight,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
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
        onConfirmImport = onConfirmImport
    )
}
