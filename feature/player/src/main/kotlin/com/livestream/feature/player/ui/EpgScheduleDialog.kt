package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Samsung One UI 风格节目单排期弹窗 (One UI Bottom Sheet / Modal Card)：
 * 1. 采用 One UI 标志性大圆角 (28.dp) 与顶部拖拽条 (Drag Handle)
 * 2. 正在播出的节目项以专属浅蓝底衬、高亮边框和“正在播出”胶囊徽章突出呈现
 * 3. 自动定位滑动至当前正在播出的节目，极大优化单手查阅效率
 */
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
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            val sheetShape = RoundedCornerShape(26.dp)

            Box(
                modifier = Modifier
                    .width(440.dp)
                    .fillMaxHeight(0.85f)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.divider), sheetShape)
                    .background(OneUiTheme.colors.background, sheetShape)
                    .padding(22.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部拖拽指示条 (One UI Signature Drag Handle)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 4.dp)
                            .background(OneUiTheme.colors.divider, CircleShape)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 顶部标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${channel?.name ?: "当前频道"} 节目单",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                            Text(
                                text = "今日排期 ($todayStr)",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        TvFocusableBox(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(50),
                            unfocusedBackgroundColor = OneUiTheme.colors.cardSecondary,
                            focusedBackgroundColor = OneUiTheme.colors.cardSecondary
                        ) { isFocused ->
                            Text(
                                text = "关闭",
                                color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (programs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "正在获取最新节目排期...",
                                color = OneUiTheme.colors.textSecondary,
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(programs, key = { it.id }) { prog ->
                                val isCurrent = prog.startTimestamp <= now && prog.endTimestamp > now
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
                                val timeRange = "${timeFormat.format(Date(prog.startTimestamp))} - ${timeFormat.format(Date(prog.endTimestamp))}"
                                val itemShape = RoundedCornerShape(14.dp)

                                val borderStroke = if (isCurrent) {
                                    BorderStroke(1.5.dp, OneUiTheme.colors.accent)
                                } else {
                                    BorderStroke(1.dp, OneUiTheme.colors.divider)
                                }

                                val itemBg = if (isCurrent) {
                                    OneUiTheme.colors.accentSoft
                                } else {
                                    OneUiTheme.colors.cardBackground
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(borderStroke, itemShape)
                                        .background(itemBg, itemShape)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isCurrent) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(OneUiTheme.colors.accent)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "正在播出",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(
                                                    text = prog.title,
                                                    color = if (isCurrent) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }

                                            Text(
                                                text = timeRange,
                                                color = if (isCurrent) OneUiTheme.colors.accent.copy(alpha = 0.8f) else OneUiTheme.colors.textSecondary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 4.dp)
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
