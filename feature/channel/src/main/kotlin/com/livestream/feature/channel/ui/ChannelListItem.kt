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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiTheme

/**
 * Samsung One UI 风格频道卡片组件：
 * 1. 采用 One UI 标志性圆角卡片 (18.dp) 与极细柔和边缘分割
 * 2. 活跃播放频道呈现专属浅蓝高光底衬与焦点边框
 * 3. 序号采用精致的小圆角药丸胶囊微容器排版
 * 4. 优化单手触控与电视遥控器双模交互体验
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
    val cardShape = RoundedCornerShape(18.dp)

    TvFocusableBox(
        onClick = { onChannelClick(channel) },
        shape = cardShape,
        unfocusedBackgroundColor = if (isActive) OneUiTheme.colors.accentSoft else OneUiTheme.colors.cardBackground,
        focusedBackgroundColor = if (isActive) OneUiTheme.colors.accentSoft else OneUiTheme.colors.cardSecondary,
        focusedBorderColor = OneUiTheme.colors.accent,
        focusedBorderWidth = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        val borderModifier = if (!isActive && !isFocused) {
            Modifier.border(BorderStroke(1.dp, OneUiTheme.colors.divider), cardShape)
        } else if (isActive && !isFocused) {
            Modifier.border(BorderStroke(1.dp, OneUiTheme.colors.accent.copy(alpha = 0.5f)), cardShape)
        } else {
            Modifier
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(borderModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 1. 频道序号徽章 (One UI Squircle Badge)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isActive) OneUiTheme.colors.accent.copy(alpha = 0.15f)
                            else OneUiTheme.colors.cardSecondary
                        )
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(channel.channelNumber),
                        color = if (isActive || isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. 频道名称与当前节目信息
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(OneUiTheme.colors.statusSuccess, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = channel.name,
                            color = if (isActive) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    if (!programTitle.isNullOrBlank()) {
                        Text(
                            text = programTitle,
                            color = if (isActive) OneUiTheme.colors.accent.copy(alpha = 0.85f) else OneUiTheme.colors.textMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 3. 收藏按钮（星星图标）
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onFavoriteToggle(channel) }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channel.isFavorite) "★" else "☆",
                    color = if (channel.isFavorite) OneUiTheme.colors.statusWarning else OneUiTheme.colors.textMuted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
