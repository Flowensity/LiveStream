package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import com.livestream.core.model.PlaybackStatus
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
    osdFocusedIndex: Int = -1 // 0: 线路聚焦, 1: 节目单聚焦, -1: 无遥控强制聚焦
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 中间加载与切台状态 (Waiting Indicator)
        if (uiState.isBuffering && uiState.status != PlaybackStatus.ERROR) {
            val statusText = when (uiState.status) {
                PlaybackStatus.SWITCHING -> "正在换台..."
                PlaybackStatus.RESOLVING -> "正在解析直播信号..."
                PlaybackStatus.PREPARING -> "正在初始化解码器..."
                else -> "正在缓冲数据..."
            }
            StreamLoadingIndicator(
                message = statusText,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 2. 播放异常提示卡片 (One UI Error Dialog Card)
        if (uiState.status == PlaybackStatus.ERROR && uiState.error != null) {
            val errCardShape = OneUiShapes.dialog
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
                        Button(
                            onClick = onRetry,
                            shape = OneUiShapes.pill,
                            colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重试", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (uiState.totalSources > 1) {
                            OutlinedButton(
                                onClick = onSwitchSource,
                                shape = OneUiShapes.pill,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OneUiTheme.colors.textPrimary),
                                border = BorderStroke(1.dp, OneUiTheme.colors.borderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "换线 (${uiState.activeSourceIndex + 1}/${uiState.totalSources})",
                                    fontSize = 13.sp
                                )
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
            val totalSrc = uiState.totalSources.coerceAtLeast(1)
            val currentIdx = (uiState.activeSourceIndex + 1).coerceIn(1, totalSrc)

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
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // 第 1 行：频道名称 + 并排显示的 [线路] 与 [节目单] 胶囊按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 频道编号与标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val numStr = "%02d".format(channel.channelNumber)
                            Box(
                                modifier = Modifier
                                    .size(width = 34.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OneUiTheme.colors.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = numStr,
                                    color = OneUiTheme.colors.accent,
                                    fontSize = 13.sp,
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

                        // 并排快捷操作区：[线路] 与 [节目单]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 线路切换胶囊 (支持触控与遥控器高亮)
                            val isSourceFocused = osdFocusedIndex == 0
                            TvFocusableBox(
                                onClick = onSwitchSource,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = if (isSourceFocused) OneUiTheme.colors.accentSoft else OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "线路 $currentIdx/$totalSrc",
                                        color = if (isFocused || isSourceFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isFocused || isSourceFocused) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }

                            // 节目单胶囊 (全应用唯一定位入口)
                            if (onOpenEpgSchedule != null) {
                                val isEpgFocused = osdFocusedIndex == 1
                                TvFocusableBox(
                                    onClick = onOpenEpgSchedule,
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = if (isEpgFocused) OneUiTheme.colors.accentSoft else OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.5.dp
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "节目单",
                                            color = if (isFocused || isEpgFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isFocused || isEpgFocused) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 第 2 行：正在播出与下期预告节目信息 (单行流式排版，长标题自适应截断)
                    val currentProg = uiState.currentProgram
                    val nextProg = uiState.nextProgram
                    if (currentProg != null || nextProg != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentProg != null) {
                                val startTime = formatTime(currentProg.startTimestamp)
                                val endTime = formatTime(currentProg.endTimestamp)
                                Text(
                                    text = "▶ 正在播出  $startTime-$endTime  ${currentProg.title}",
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (nextProg != null) {
                                if (currentProg != null) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                val nextTime = formatTime(nextProg.startTimestamp)
                                Text(
                                    text = "稍后播出  $nextTime  ${nextProg.title}",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "--:--"
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
