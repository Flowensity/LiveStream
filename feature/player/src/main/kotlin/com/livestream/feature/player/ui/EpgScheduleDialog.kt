package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.DarkSurface
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.StatusSuccess
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.core.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgScheduleDialog(
    visible: Boolean,
    channel: Channel?,
    programs: List<EpgProgram>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val now = System.currentTimeMillis()
        val listState = rememberLazyListState()

        // 自动定位到当前正在播出的节目项
        LaunchedEffect(programs) {
            val currentIndex = programs.indexOfFirst { it.startTimestamp <= now && it.endTimestamp > now }
            if (currentIndex >= 0) {
                listState.scrollToItem(maxOf(0, currentIndex - 1))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight(0.85f)
                    .background(DarkBackground, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${channel?.name ?: "当前频道"} 节目单",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                            Text(
                                text = "今日排期 ($todayStr)",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        TvFocusableBox(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(6.dp)
                        ) { isFocused ->
                            Text(
                                text = "关闭",
                                color = if (isFocused) FocusHighlight else TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (programs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "正在获取最新节目排期...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(programs, key = { it.id }) { prog ->
                                val isCurrent = prog.startTimestamp <= now && prog.endTimestamp > now
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
                                val timeRange = "${timeFormat.format(Date(prog.startTimestamp))} - ${timeFormat.format(Date(prog.endTimestamp))}"

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrent) DarkSurfaceElevated else DarkSurface,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isCurrent) {
                                                    Text(
                                                        text = "● 播出中",
                                                        color = StatusSuccess,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    )
                                                }
                                                Text(
                                                    text = timeRange,
                                                    color = if (isCurrent) FocusHighlight else TextMuted,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = prog.title,
                                                color = if (isCurrent) TextPrimary else TextSecondary,
                                                fontSize = 14.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
