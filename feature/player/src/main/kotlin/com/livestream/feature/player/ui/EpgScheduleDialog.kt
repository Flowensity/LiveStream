package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规范节目单右侧抽屉面板 (EpgScheduleDrawerView, 居右对齐，与频道列表 ChannelDrawer 风格高度统一)：
 * 1. 居右对齐滑入展示，左侧内向圆角 (24.dp)
 * 2. 正在播出的节目项以专属浅蓝底衬、高亮边框和规范圆润胶囊突出呈现
 * 3. 自动定位滑动至当前正在播出的节目
 */
@Composable
fun EpgScheduleDialog(
    visible: Boolean,
    channel: Channel?,
    programs: List<EpgProgram>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val now = System.currentTimeMillis()
    val listState = rememberLazyListState()

    // 自动定位到当前正在播出的节目项
    LaunchedEffect(programs) {
        val currentIndex = programs.indexOfFirst { it.startTimestamp <= now && it.endTimestamp > now }
        if (currentIndex >= 0) {
            listState.scrollToItem(maxOf(0, currentIndex - 1))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 半透明背景遮罩（点击可关闭）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val drawerShape = RoundedCornerShape(20.dp)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 8.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)
                    .width(OneUiDimensions.panelPlaylistWidth)
                    .clip(drawerShape)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
                    .background(OneUiTheme.colors.panelBackground)
                    .padding(vertical = 12.dp, horizontal = 10.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部标题栏与关闭胶囊按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${channel?.name ?: "当前频道"} 节目单",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                            Text(
                                text = "今日排期 ($todayStr)",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        TvFocusableBox(
                            onClick = onDismiss,
                            shape = OneUiShapes.pill,
                            unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                            focusedBackgroundColor = OneUiTheme.colors.accent,
                            focusedBorderColor = OneUiTheme.colors.accent,
                            focusedBorderWidth = 1.5.dp
                        ) { isFocused ->
                            Text(
                                text = "关闭",
                                color = if (isFocused) Color.White else OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                                fontSize = 13.sp
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
                                val itemShape = RoundedCornerShape(12.dp)

                                val borderStroke = if (isCurrent) {
                                    BorderStroke(1.5.dp, OneUiTheme.colors.accent)
                                } else {
                                    BorderStroke(1.dp, OneUiTheme.colors.borderSubtle)
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
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isCurrent) {
                                                // 规范标准尺寸徽章胶囊（与外部列表序号胶囊尺寸完全统一呼应）
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(OneUiTheme.colors.accent)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "正在播出",
                                                        color = Color.White,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = prog.title,
                                                color = if (isCurrent) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }

                                        Text(
                                            text = timeRange,
                                            color = if (isCurrent) OneUiTheme.colors.accent.copy(alpha = 0.85f) else OneUiTheme.colors.textSecondary,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(top = 3.dp)
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
