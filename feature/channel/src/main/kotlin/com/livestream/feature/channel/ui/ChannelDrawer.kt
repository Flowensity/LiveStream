package com.livestream.feature.channel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.channel.ChannelUiState
import kotlinx.coroutines.delay

/**
 * TeleflowTv 规范频道面板 (PlaylistView, 316dp 浮动卡片抽屉)
 */
@Composable
fun ChannelDrawer(
    uiState: ChannelUiState,
    onCategorySelected: (ChannelCategory) -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            delay(150)
            try {
                activeItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = uiState.isDrawerOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        val drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(OneUiDimensions.panelPlaylistWidth)
                .clip(drawerShape)
                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
                .background(OneUiTheme.colors.panelBackground)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. One UI 顶部大标题与分组快速切换器 (‹ 分类名 ›)
                val categories = uiState.categories
                val currentCategoryIndex = categories.indexOfFirst { it.id == uiState.selectedCategory?.id }
                val currentCategoryTitle = uiState.selectedCategory?.name ?: "频道列表"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左切换按钮 ‹
                    if (categories.size > 1) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    // 中间分类大标题
                    Text(
                        text = currentCategoryTitle,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // 右切换按钮 ›
                    if (categories.size > 1) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }

                // 2. 分类药丸横向滚动栏 (Pill Chips)
                CategoryTabRow(
                    categories = categories,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = onCategorySelected,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. 搜索框 (TeleflowTv Quick Channel Search Bar, 34dp 高度)
                val searchShape = RoundedCornerShape(17.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .height(34.dp)
                        .clip(searchShape)
                        .background(OneUiTheme.colors.cardSecondary)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            textStyle = TextStyle(
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(OneUiTheme.colors.accent),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索频道...",
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (uiState.searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(OneUiShapes.pill)
                                    .background(OneUiTheme.colors.surfacePill)
                                    .clickable { onSearchQueryChanged("") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. 频道列表卡片容器 (TeleflowTv flChannelCard)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .clip(OneUiShapes.cardMedium)
                        .background(OneUiTheme.colors.cardBackground)
                ) {
                    if (uiState.channels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) "未搜寻到匹配频道" else "该分类下暂无频道",
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "请切换分类或清除搜索条件",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.channels,
                                key = { _, ch -> ch.id }
                            ) { index, channel ->
                                val isActive = channel.id == uiState.activeChannelId
                                val isFirstOrActive = isActive || (uiState.activeChannelId == null && index == 0)
                                val itemModifier = if (isFirstOrActive) {
                                    Modifier.focusRequester(activeItemFocusRequester)
                                } else {
                                    Modifier
                                }
                                ChannelListItem(
                                    channel = channel,
                                    isActive = isActive,
                                    onChannelClick = onChannelSelected,
                                    onFavoriteToggle = onFavoriteToggle,
                                    modifier = itemModifier
                                )
                            }
                        }
                    }
                }

                // 5. 底部操作提示与关闭入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "左右键换分类 · 点击换台",
                        color = OneUiTheme.colors.textMuted,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(OneUiShapes.pill)
                            .background(OneUiTheme.colors.surfacePill)
                            .clickable { onCloseDrawer() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "关闭",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
