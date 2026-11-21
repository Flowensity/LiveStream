package com.livestream.feature.channel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.channel.ChannelUiState

/**
 * 统一的场景化频道列表空状态组件：
 * 根据「搜索无结果」、「我的收藏为空」、「最近观看为空」、「自定义源为空」提供针对性引导与一键恢复操作。
 */
@Composable
fun ChannelEmptyState(
    searchQuery: String,
    selectedCategoryId: String?,
    onClearSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isSearching = searchQuery.isNotBlank()
    val (badgeSymbol, title, subtitle) = when {
        isSearching -> Triple(
            "⌕",
            "未找到匹配的频道",
            "没有找到与“${searchQuery.trim()}”相关的频道，请尝试其他名称或台号"
        )
        selectedCategoryId == ChannelUiState.FAVORITE_CATEGORY.id -> Triple(
            "★",
            "暂无收藏频道",
            "点击频道右侧「收藏」按钮或长按频道，即可将常看频道加入此处"
        )
        selectedCategoryId == ChannelUiState.RECENT_CATEGORY.id -> Triple(
            "◷",
            "暂无观看记录",
            "您观看过的频道会自动记录在这里，方便随时快速回看"
        )
        selectedCategoryId == "custom" -> Triple(
            "+",
            "暂无自定义频道",
            "可在「设置 → 导入 M3U 播放列表」中添加自定义直播源"
        )
        else -> Triple(
            "📺",
            "当前分类暂无频道",
            "请切换至其他频道分类查看"
        )
    }

    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), CircleShape)
                    .background(OneUiTheme.colors.accentSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeSymbol,
                    color = OneUiTheme.colors.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                color = OneUiTheme.colors.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = subtitle,
                color = OneUiTheme.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )

            if (isSearching && onClearSearch != null) {
                Spacer(modifier = Modifier.height(14.dp))
                TvFocusableBox(
                    onClick = onClearSearch,
                    shape = OneUiShapes.pill,
                    unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                    focusedBackgroundColor = OneUiTheme.colors.accent,
                    focusedBorderColor = OneUiTheme.colors.accent,
                    focusedBorderWidth = 1.5.dp
                ) { isFocused ->
                    Text(
                        text = "清空搜索条件",
                        color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
