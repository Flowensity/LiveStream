package com.livestream.feature.channel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * TeleflowTv 规范频道列表行 (item_channel, 48dp 标准行高)
 */
@Composable
fun ChannelListItem(
    channel: Channel,
    isActive: Boolean,
    onChannelClick: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    programTitle: String? = null
) {
    val rowShape = RoundedCornerShape(14.dp)

    TvFocusableBox(
        onClick = { onChannelClick(channel) },
        shape = rowShape,
        unfocusedBackgroundColor = if (isActive) OneUiTheme.colors.accentSoft else Color.Transparent,
        focusedBackgroundColor = OneUiTheme.colors.accentSoft,
        focusedBorderColor = OneUiTheme.colors.accent,
        focusedBorderWidth = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(OneUiDimensions.rowHeightChannel)
    ) { isFocused ->
        val borderModifier = if (isActive && !isFocused) {
            Modifier.border(BorderStroke(1.dp, OneUiTheme.colors.accent.copy(alpha = 0.4f)), rowShape)
        } else {
            Modifier
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(borderModifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 1. 频道序号 (34dp 宽度，TeleflowTv 规范)
                Box(
                    modifier = Modifier.width(OneUiDimensions.badgeChannelNumberWidth),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "%02d".format(channel.channelNumber),
                        color = when {
                            isFocused -> OneUiTheme.colors.accent
                            isActive -> OneUiTheme.colors.accent
                            else -> OneUiTheme.colors.textSecondary
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 2. 频道名称
                Text(
                    text = channel.name,
                    color = when {
                        isFocused -> OneUiTheme.colors.accent
                        isActive -> OneUiTheme.colors.accent
                        else -> OneUiTheme.colors.textPrimary
                    },
                    fontSize = 15.sp,
                    fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. 收藏状态胶囊 (无图标，简洁文本)
            val favPillShape = OneUiShapes.pill
            Box(
                modifier = Modifier
                    .clip(favPillShape)
                    .background(
                        if (channel.isFavorite) OneUiTheme.colors.accentSoft
                        else OneUiTheme.colors.surfacePill
                    )
                    .clickable { onFavoriteToggle(channel) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channel.isFavorite) "已收藏" else "收藏",
                    color = if (channel.isFavorite) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
