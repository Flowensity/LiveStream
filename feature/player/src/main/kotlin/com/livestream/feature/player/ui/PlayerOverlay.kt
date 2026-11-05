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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.ui.component.StreamLoadingIndicator
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.player.PlayerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Samsung One UI 风格播放器控制浮层 (OSD)
 */
@Composable
fun PlayerOverlay(
    uiState: PlayerUiState,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onOpenEpgSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 中间加载与切台状态
        if (uiState.isBuffering && uiState.status != PlaybackStatus.ERROR) {
            val statusText = when (uiState.status) {
                PlaybackStatus.SWITCHING -> "正在切换频道..."
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
            val errCardShape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.divider), errCardShape)
                    .background(
                        color = OneUiTheme.colors.cardBackground.copy(alpha = 0.95f),
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
                        color = OneUiTheme.colors.statusError,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.error.message,
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重试", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        if (uiState.totalSources > 1) {
                            OutlinedButton(
                                onClick = onSwitchSource,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("换线 (${uiState.activeSourceIndex + 1}/${uiState.totalSources})")
                            }
                        }
                    }
                }
            }
        }

        // 3. 数字键换台悬浮窗
        if (uiState.quickChannelNumber.isNotEmpty()) {
            val quickShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .border(BorderStroke(1.dp, Color(0x33FFFFFF)), quickShape)
                    .background(Color(0xDD1A1B20), quickShape)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.quickChannelNumber,
                        color = OneUiTheme.colors.accent,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "正在换台...",
                        color = OneUiTheme.colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 4. OSD 顶部与底部面板（带淡入淡出动画）
        AnimatedVisibility(
            visible = uiState.isOsdVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部频道信息渐变栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val channelNum = uiState.activeChannel?.channelNumber?.let { "%02d".format(it) } ?: "--"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OneUiTheme.colors.accent.copy(alpha = 0.25f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = channelNum,
                                        color = OneUiTheme.colors.accent,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = uiState.activeChannel?.name ?: "未选频道",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (uiState.totalSources > 0) {
                                    val lineIndex = uiState.activeSourceIndex + 1
                                    val lineBadge = "线路 $lineIndex/${uiState.totalSources} [${uiState.activeSource?.protocol ?: "AUTO"}]"
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = lineBadge,
                                        color = Color(0xFFC5C8D4),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0x33FFFFFF))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // EPG 节目单展示
                            if (uiState.activeChannel != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val currentProg = uiState.currentProgram
                                    if (currentProg != null) {
                                        val timeStr = "${formatTime(currentProg.startTimestamp)} - ${formatTime(currentProg.endTimestamp)}"
                                        Text(
                                            text = "播出: ${currentProg.title} ($timeStr)",
                                            color = OneUiTheme.colors.accent,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    val nextProg = uiState.nextProgram
                                    if (nextProg != null) {
                                        Text(
                                            text = "下期: ${nextProg.title} (${formatTime(nextProg.startTimestamp)})",
                                            color = Color(0xFFA0A4B2),
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (onOpenEpgSchedule != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(OneUiTheme.colors.accent.copy(alpha = 0.25f))
                                                .clickable { onOpenEpgSchedule() }
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "📅 节目单",
                                                color = OneUiTheme.colors.accent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部性能指标统计渐变栏
                val metrics = uiState.metrics
                val statItems = mutableListOf<String>()
                if (metrics.videoWidth > 0 && metrics.videoHeight > 0) {
                    statItems.add("分辨率: ${metrics.videoWidth}x${metrics.videoHeight}")
                }
                if (metrics.frameRate > 0) {
                    statItems.add("帧率: %.1f fps".format(metrics.frameRate))
                }
                if (metrics.bitrateBps > 0) {
                    statItems.add("码率: %.1f Mbps".format(metrics.bitrateBps / 1_000_000.0))
                }
                if (metrics.bufferedDurationMs > 0) {
                    statItems.add("缓冲: %.1fs".format(metrics.bufferedDurationMs / 1000.0))
                }
                if (metrics.switchDurationMs > 0) {
                    statItems.add("切台: ${metrics.switchDurationMs}ms")
                }

                if (statItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = statItems.joinToString("  •  "),
                            color = Color(0xFF9EA2B0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
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
