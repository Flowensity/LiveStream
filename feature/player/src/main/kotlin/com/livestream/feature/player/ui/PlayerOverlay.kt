package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.VideoAspectRatio
import com.livestream.core.ui.component.StreamLoadingIndicator
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.player.PlayerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Samsung One UI / TeleflowTv 规范播放器控制浮层 (ChannelBarView HUD)
 * 极致紧凑双行排版：
 * 第 1 行：频道序号与名称 + 线路与节目单并排胶囊按钮
 * 第 2 行：正在播出与稍后节目简明预告
 */
@Composable
fun PlayerOverlay(
    uiState: PlayerUiState,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onOpenEpgSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    osdFocusedIndex: Int = -1, // 0: 换线聚焦, 1: 节目单聚焦, 2: 遥控/设置聚焦, -1: 无遥控强制聚焦
    aspectRatio: VideoAspectRatio = uiState.aspectRatio,
    onCycleAspectRatio: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 换台与缓冲平滑过渡加载指示器（删除居中频道预告卡片，仅保留轻量加载动效）
        AnimatedVisibility(
            visible = uiState.isBuffering && uiState.status != PlaybackStatus.ERROR,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val statusText = when (uiState.status) {
                PlaybackStatus.SWITCHING -> "正在换台..."
                PlaybackStatus.RESOLVING -> "正在解析直播信号..."
                PlaybackStatus.PREPARING -> "正在初始化解码器..."
                else -> "正在缓冲数据..."
            }
            StreamLoadingIndicator(message = statusText)
        }

        // 2. 播放异常提示卡片 (One UI Error Dialog Card)
        if (uiState.status == PlaybackStatus.ERROR && uiState.error != null) {
            val errCardShape = OneUiShapes.dialog
            val retryFocusRequester = remember { FocusRequester() }
            LaunchedEffect(uiState.status, uiState.error) {
                try {
                    retryFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), errCardShape)
                    .background(
                        color = OneUiTheme.colors.panelBackground,
                        shape = errCardShape
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(320.dp)
                ) {
                    Text(
                        text = "播放异常",
                        color = OneUiTheme.colors.danger,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.error.message,
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvFocusableBox(
                            onClick = onRetry,
                            shape = OneUiShapes.pill,
                            unfocusedBackgroundColor = OneUiTheme.colors.accent,
                            focusedBackgroundColor = OneUiTheme.colors.accent,
                            focusedBorderColor = Color.White,
                            focusedBorderWidth = 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(retryFocusRequester)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("重试", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        if (uiState.totalSources > 1) {
                            TvFocusableBox(
                                onClick = onSwitchSource,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 2.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.pill)
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "换线 (${uiState.activeSourceIndex + 1}/${uiState.totalSources})",
                                        color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 数字键换台悬浮窗
        if (uiState.quickChannelNumber.isNotEmpty()) {
            val quickShape = OneUiShapes.cardMedium
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), quickShape)
                    .background(OneUiTheme.colors.panelBackground, quickShape)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.quickChannelNumber,
                        color = OneUiTheme.colors.accent,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "正在换台...",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 4. 紧凑浮动 HUD (ChannelBarView - 压缩高度，删除冗余分辨率行)
        AnimatedVisibility(
            visible = uiState.isOsdVisible && uiState.activeChannel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val channel = uiState.activeChannel ?: return@AnimatedVisibility
            val panelShape = OneUiShapes.panel

            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = OneUiDimensions.hudChannelBarWidth)
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), panelShape)
                    .background(OneUiTheme.colors.panelBackground, panelShape)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 第 1 行：左区 [台号 + 频道名] + 右区 [播放菜单]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 左侧：纯粹的频道编号与标题（已移除线路与徽标显示）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            val numStr = "%02d".format(channel.channelNumber)
                            Box(
                                modifier = Modifier
                                    .size(width = 32.dp, height = 22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OneUiTheme.colors.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = numStr,
                                    color = OneUiTheme.colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channel.name,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 右侧单一聚合胶囊：仅保留 [播放菜单] 一个胶囊入口
                        if (onOpenEpgSchedule != null) {
                            val isMenuFocused = osdFocusedIndex >= 0
                            TvFocusableBox(
                                onClick = onOpenEpgSchedule,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = if (isMenuFocused) OneUiTheme.colors.accentSoft else OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "播放菜单",
                                        color = if (isFocused || isMenuFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isFocused || isMenuFocused) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // 第 2 行：正在播出与稍后播出按比例分配宽度（防止稍后播出过长挤压正在播出的内容）+ 进度条
                    val currentProg = uiState.currentProgram
                    val nextProg = uiState.nextProgram
                    if (currentProg != null || nextProg != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (currentProg != null) {
                                val startTime = formatTime(currentProg.startTimestamp)
                                val endTime = formatTime(currentProg.endTimestamp)
                                Text(
                                    text = "▶ 正在播出 $startTime-$endTime  ${currentProg.title}",
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(if (nextProg != null) 0.58f else 1f)
                                )
                            }
                            if (nextProg != null) {
                                val nextTime = formatTime(nextProg.startTimestamp)
                                Text(
                                    text = "稍后 $nextTime  ${nextProg.title}",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(if (currentProg != null) 0.42f else 1f)
                                )
                            }
                        }

                        // 当前节目实时播放进度条 (2.5dp 极细胶囊进度条)
                        if (currentProg != null && currentProg.endTimestamp > currentProg.startTimestamp) {
                            val now = System.currentTimeMillis()
                            val totalDuration = (currentProg.endTimestamp - currentProg.startTimestamp).coerceAtLeast(1L)
                            val progress = ((now - currentProg.startTimestamp).toFloat() / totalDuration.toFloat()).coerceIn(0.03f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp)
                                    .height(2.5.dp)
                                    .clip(OneUiShapes.pill)
                                    .background(OneUiTheme.colors.surfacePill)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .clip(OneUiShapes.pill)
                                        .background(OneUiTheme.colors.accent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TIME_FORMAT_HOLDER = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "--:--"
    val sdf = TIME_FORMAT_HOLDER.get() ?: SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
