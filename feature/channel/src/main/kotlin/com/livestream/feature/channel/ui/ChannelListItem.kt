package com.livestream.feature.channel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme

/**
 * 现代 One UI 频道列表行 (46dp 舒适行高)：
 * 配备标准台号胶囊、正在播放呼吸圆点指示器与星标收藏胶囊。
 */
@Composable
fun ChannelListItem(
    channel: Channel,
    isActive: Boolean,
    onChannelClick: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val rowShape = RoundedCornerShape(12.dp)

    TvFocusableBox(
        onClick = { onChannelClick(channel) },
        onLongClick = { onFavoriteToggle(channel) },
        shape = rowShape,
        unfocusedBackgroundColor = if (isActive) OneUiTheme.colors.accentSoft else Color.Transparent,
        focusedBackgroundColor = OneUiTheme.colors.accentSoft,
        focusedBorderColor = OneUiTheme.colors.accent,
        focusedBorderWidth = 2.dp,
        unfocusedBorderColor = if (isActive) OneUiTheme.colors.accent.copy(alpha = 0.55f) else null,
        unfocusedBorderWidth = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(OneUiDimensions.rowHeightChannel)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 1. 统一标准尺寸的台号胶囊容器 (36dp x 24dp)
                Box(
                    modifier = Modifier
                        .size(width = OneUiDimensions.badgeChannelNumberWidth, height = 24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isActive) OneUiTheme.colors.accent
                            else if (isFocused) OneUiTheme.colors.accentSoft
                            else OneUiTheme.colors.surfacePill
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(channel.channelNumber),
                        color = when {
                            isActive -> Color.White
                            isFocused -> OneUiTheme.colors.accent
                            else -> OneUiTheme.colors.textSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 2. 频道名称
                Text(
                    text = channel.name,
                    color = when {
                        isFocused || isActive -> OneUiTheme.colors.accent
                        else -> OneUiTheme.colors.textPrimary
                    },
                    fontSize = 14.sp,
                    fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // 正在播放状态指示点
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(OneUiTheme.colors.accent)
                    )
                }
            }

            // 3. 收藏状态指示与切换胶囊
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(OneUiShapes.pill)
                    .background(
                        if (channel.isFavorite) OneUiTheme.colors.accentSoft
                        else OneUiTheme.colors.surfacePill
                    )
                    .clickable { onFavoriteToggle(channel) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channel.isFavorite) "★ 已收藏" else "☆ 收藏",
                    color = if (channel.isFavorite) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (channel.isFavorite) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
