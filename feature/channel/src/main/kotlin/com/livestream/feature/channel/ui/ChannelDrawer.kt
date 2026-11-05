package com.livestream.feature.channel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.channel.ChannelUiState
import kotlinx.coroutines.delay

/**
 * Samsung One UI 风格频道侧边抽屉面板：
 * 1. 采用 One UI 标志性大圆角边缘与极细微边界
 * 2. 药丸胶囊搜索输入框 (24.dp)
 * 3. 药丸流式分类切换
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
        val drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(290.dp)
                .clip(drawerShape)
                .border(BorderStroke(1.dp, OneUiTheme.colors.divider), drawerShape)
                .background(OneUiTheme.colors.background)
                .padding(vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 顶部标题与频道总数徽章
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "频道菜单",
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(OneUiTheme.colors.cardSecondary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "共 ${uiState.channels.size} 个",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. One UI 药丸胶囊搜索框 (无图标)
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("搜索频道...", color = OneUiTheme.colors.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OneUiTheme.colors.cardSecondary,
                        unfocusedContainerColor = OneUiTheme.colors.cardSecondary,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = OneUiTheme.colors.textPrimary,
                        unfocusedTextColor = OneUiTheme.colors.textPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. 分类药丸横向导航栏
                CategoryTabRow(
                    categories = uiState.categories,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = onCategorySelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. 频道卡片列表
                if (uiState.channels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "未搜寻到匹配频道" else "该分类下暂无频道",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
        }
    }
}
