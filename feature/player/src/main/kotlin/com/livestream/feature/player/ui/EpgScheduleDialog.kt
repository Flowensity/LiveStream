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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.model.LiveSource
import com.livestream.core.model.VideoAspectRatio
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.device.DeviceType
import com.livestream.core.ui.device.rememberDeviceType
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 综合「播放菜单」右侧抽屉面板 (PlaybackMenuDrawerView)：
 * 集成：
 * 1. 信号源 / 线路快捷切换与延迟展示
 * 2. 画面比例切换 (原始比例 / 16:9 标准 / 4:3 经典 / 裁剪全屏)
 * 3. 今日完整 EPG 节目单排期与正在播出定位
 */
@Composable
fun EpgScheduleDialog(
    visible: Boolean,
    channel: Channel?,
    programs: List<EpgProgram>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    activeSourceIndex: Int = 0,
    totalSources: Int = 1,
    availableSources: List<LiveSource> = emptyList(),
    sourceLatenciesMs: Map<String, Long> = emptyMap(),
    onSwitchSource: () -> Unit = {}
) {
    if (!visible) return

    val isTv = rememberDeviceType() == DeviceType.TV
    val now = System.currentTimeMillis()
    val listState = rememberLazyListState()
    val firstFocusRequester = remember { FocusRequester() }
    val todayStr = remember { SimpleDateFormat("MM-dd", Locale.CHINA).format(Date()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }

    val currentIndex = remember(programs, now) {
        programs.indexOfFirst { it.startTimestamp <= now && it.endTimestamp > now }
    }

    // 自动滚动节目单至当前正在播出的节目，并在 TV 端将初始焦点落在首项控制卡片上
    LaunchedEffect(visible, programs, isTv) {
        if (programs.isNotEmpty()) {
            val targetIdx = if (currentIndex >= 0) currentIndex else 0
            listState.scrollToItem(maxOf(0, targetIdx - 1))
        }
        if (isTv) {
            delay(120)
            try {
                firstFocusRequester.requestFocus()
            } catch (_: Exception) {}
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
            val cardShape = OneUiShapes.cardMedium
            val safeTotalSources = totalSources.coerceAtLeast(1)
            val currentSourceNum = (activeSourceIndex + 1).coerceIn(1, safeTotalSources)
            val activeSource = availableSources.getOrNull(activeSourceIndex)
            val activeLatency = activeSource?.id?.let { sourceLatenciesMs[it] }

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
                    // 1. 顶部标题栏：播放菜单 + 频道名 + 关闭胶囊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "播放菜单",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channel?.name ?: "当前频道"} · 线路、画面与节目单",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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

                    // 2. 区块一：信号源与线路切换卡片
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
                            .background(OneUiTheme.colors.cardBackground, cardShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "信号源切换 ($currentSourceNum/$safeTotalSources)",
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val sourceSubtitle = buildString {
                                    append(activeSource?.name ?: "默认主线路")
                                    if (activeLatency != null && activeLatency > 0L) {
                                        append(" · 延迟 ${activeLatency}ms")
                                    }
                                }
                                Text(
                                    text = sourceSubtitle,
                                    color = OneUiTheme.colors.accent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }

                            TvFocusableBox(
                                onClick = onSwitchSource,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp,
                                modifier = Modifier.focusRequester(firstFocusRequester)
                            ) { isFocused ->
                                Text(
                                    text = if (safeTotalSources > 1) "切换下一线路" else "重新连接线路",
                                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. 区块二：画面比例卡片（由设置页迁移至此）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
                            .background(OneUiTheme.colors.cardBackground, cardShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "画面比例",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = aspectRatio.label,
                                color = OneUiTheme.colors.accent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            VideoAspectRatio.entries.forEach { ratio ->
                                val isSelected = aspectRatio == ratio
                                TvFocusableBox(
                                    onClick = { onAspectRatioSelected(ratio) },
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.5.dp,
                                    modifier = Modifier.weight(1f)
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ratio.label.substringBefore(" "),
                                            color = if (isSelected) Color.White else if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. 区块三：今日节目单列表
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日节目单 ($todayStr)",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (programs.isNotEmpty()) {
                            Text(
                                text = "共 ${programs.size} 档节目",
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (programs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
                                .background(OneUiTheme.colors.cardBackground, cardShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "正在获取最新节目排期...",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            itemsIndexed(programs, key = { _, it -> it.id }) { _, prog ->
                                val isCurrent = prog.startTimestamp <= now && prog.endTimestamp > now
                                val timeRange = "${timeFormat.format(Date(prog.startTimestamp))} - ${timeFormat.format(Date(prog.endTimestamp))}"
                                val itemShape = RoundedCornerShape(12.dp)

                                val itemBg = if (isCurrent) {
                                    OneUiTheme.colors.accentSoft
                                } else {
                                    OneUiTheme.colors.cardBackground
                                }

                                TvFocusableBox(
                                    onClick = {},
                                    shape = itemShape,
                                    scaleOnFocus = 1.0f,
                                    unfocusedBackgroundColor = itemBg,
                                    focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.5.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            if (isCurrent) BorderStroke(1.5.dp, OneUiTheme.colors.accent)
                                            else BorderStroke(1.dp, OneUiTheme.colors.borderSubtle),
                                            itemShape
                                        )
                                ) { isFocused ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(OneUiTheme.colors.accent)
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "正在播出",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = prog.title,
                                                color = if (isCurrent || isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }

                                        Text(
                                            text = timeRange,
                                            color = if (isCurrent || isFocused) OneUiTheme.colors.accent.copy(alpha = 0.85f) else OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
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
