package com.livestream.feature.channel.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkSurface
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.StatusSuccess
import com.livestream.core.ui.theme.StatusWarning
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary

@Composable
fun ChannelListItem(
    channel: Channel,
    isActive: Boolean,
    onChannelClick: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    programTitle: String? = null
) {
    TvFocusableBox(
        onClick = { onChannelClick(channel) },
        unfocusedBackgroundColor = if (isActive) DarkSurfaceElevated else DarkSurface,
        focusedBackgroundColor = DarkSurfaceElevated,
        focusedBorderColor = FocusHighlight,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 活跃播放指示器
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(StatusSuccess, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 频道序号
                Text(
                    text = "%02d".format(channel.channelNumber),
                    color = if (isFocused || isActive) FocusHighlight else TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 频道名称与当前节目
                Column {
                    Text(
                        text = channel.name,
                        color = if (isActive) FocusHighlight else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    if (!programTitle.isNullOrBlank()) {
                        Text(
                            text = programTitle,
                            color = if (isActive) FocusHighlight.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // 收藏状态按钮（星标）
            Box(
                modifier = Modifier
                    .clickable { onFavoriteToggle(channel) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channel.isFavorite) "★" else "☆",
                    color = if (channel.isFavorite) StatusWarning else TextMuted,
                    fontSize = 18.sp
                )
            }
        }
    }
}
