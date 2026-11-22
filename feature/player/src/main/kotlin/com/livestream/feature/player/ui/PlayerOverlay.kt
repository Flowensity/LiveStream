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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
 * 现代 One UI 规范播放器状态与控制浮层 (HUD)：
 * 1. 悬浮高对比度毛玻璃缓冲指示器
 * 2. 清晰友好的播放异常诊断与一键换线/重试卡片
 * 3. 数字键选台悬浮提示
 * 4. 底部紧凑双行信息栏（支持一键换线、播放设置及实时节目进度）
 */
@Composable
fun PlayerOverlay(
    uiState: PlayerUiState,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onOpenEpgSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    osdFocusedIndex: Int = -1,
    aspectRatio: VideoAspectRatio = uiState.aspectRatio,
    onCycleAspectRatio: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    isExitDialogVisible: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 换台与缓冲平滑过渡加载指示器
        AnimatedVisibility(
            visible = uiState.isBuffering && uiState.status != PlaybackStatus.ERROR,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val statusText = when (uiState.status) {
                PlaybackStatus.SWITCHING -> "正在切换频道..."
                PlaybackStatus.RESOLVING -> "正在连接直播线路..."
                PlaybackStatus.PREPARING -> "正在准备画面..."
                else -> "正在缓冲画面..."
            }
            StreamLoadingIndicator(message = statusText)
        }

        // 2. 播放异常提示卡片 (One UI Error Dialog Card)
        if (uiState.status == PlaybackStatus.ERROR && uiState.error != null) {
            val errCardShape = OneUiShapes.dialog
            val retryFocusRequester = remember { FocusRequester() }
            LaunchedEffect(uiState.status, uiState.error, isExitDialogVisible) {
                if (!isExitDialogVisible) {
                    try {
                        retryFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .widthIn(min = 280.dp, max = 340.dp)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), errCardShape)
                    .background(
                        color = OneUiTheme.colors.panelBackground,
                        shape = errCardShape
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(OneUiTheme.colors.dangerSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            color = OneUiTheme.colors.danger,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "播放遇到问题",
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = uiState.error.message,
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    if (uiState.activeSource != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "当前线路：${uiState.activeSource.name}",
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                Text(
                                    text = "立即重试",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
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
                                unfocusedBorderColor = OneUiTheme.colors.borderSubtle,
                                modifier = Modifier.weight(1f)
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "切换线路 (${uiState.activeSourceIndex + 1}/${uiState.totalSources})",
                                        color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
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
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.quickChannelNumber,
                        color = OneUiTheme.colors.accent,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "即将切换频道...",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // 4. 底部紧凑浮动信息栏 (ChannelBarView HUD)
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
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // 第 1 行：左区 [台号 + 频道名] + 右区 [换线 (多线路时) + 播放设置]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            val numStr = "%02d".format(channel.channelNumber)
                            Box(
                                modifier = Modifier
                                    .size(width = OneUiDimensions.badgeChannelNumberWidth, height = 24.dp)
                                    .clip(RoundedCornerShape(7.dp))
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = channel.name,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.totalSources > 1) {
                                TvFocusableBox(
                                    onClick = onSwitchSource,
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.5.dp,
                                    unfocusedBorderColor = OneUiTheme.colors.borderSubtle
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "换线 ${uiState.activeSourceIndex + 1}/${uiState.totalSources}",
                                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (onOpenEpgSchedule != null) {
                                val isMenuFocused = osdFocusedIndex >= 0
                                TvFocusableBox(
                                    onClick = onOpenEpgSchedule,
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = if (isMenuFocused) OneUiTheme.colors.accentSoft else OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.5.dp,
                                    unfocusedBorderColor = OneUiTheme.colors.borderSubtle
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "播放设置",
                                            color = if (isFocused || isMenuFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isFocused || isMenuFocused) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 第 2 行：正在播出与稍后播出节目单，或无 EPG 时的信号线路状态信息
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
                                    fontSize = 12.sp,
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
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(if (currentProg != null) 0.42f else 1f)
                                )
                            }
                        }

                        // 当前节目实时播放进度条 (3dp 胶囊进度条)
                        if (currentProg != null && currentProg.endTimestamp > currentProg.startTimestamp) {
                            val now = System.currentTimeMillis()
                            val totalDuration = (currentProg.endTimestamp - currentProg.startTimestamp).coerceAtLeast(1L)
                            val progress = ((now - currentProg.startTimestamp).toFloat() / totalDuration.toFloat()).coerceIn(0.03f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp)
                                    .height(3.dp)
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
                    } else {
                        // 无 EPG 数据时展示当前直播状态与信号线路信息，保持卡片视觉层次丰满
                        val sourceName = uiState.activeSource?.name ?: "默认线路"
                        val activeLatency = uiState.activeSource?.id?.let { uiState.sourceLatenciesMs[it] }
                        val latencySuffix = if (activeLatency != null && activeLatency > 0L) " · ${activeLatency}ms" else ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "● 正在直播 · $sourceName$latencySuffix",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (uiState.isEpgLoading) "正在同步节目单..." else "画面比例：${aspectRatio.label}",
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
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
