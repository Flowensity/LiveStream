package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.ui.component.StreamLoadingIndicator
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.StatusError
import com.livestream.core.ui.theme.StatusWarning
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.core.ui.theme.TextSecondary
import com.livestream.feature.player.PlayerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                PlaybackStatus.PREPARING -> "正在初始化硬件解码器..."
                else -> "正在缓冲数据..."
            }
            StreamLoadingIndicator(
                message = statusText,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 2. 播放异常提示卡片
        if (uiState.status == PlaybackStatus.ERROR && uiState.error != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = DarkSurfaceElevated.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(320.dp)
                ) {
                    Text(
                        text = "播放异常",
                        color = StatusError,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.error.message,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = FocusHighlight),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重试", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        if (uiState.totalSources > 1) {
                            OutlinedButton(
                                onClick = onSwitchSource,
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(DarkSurfaceElevated.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.quickChannelNumber,
                        color = FocusHighlight,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "正在换台...",
                        color = TextMuted,
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
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val channelNum = uiState.activeChannel?.channelNumber?.let { "%02d".format(it) } ?: "--"
                                Text(
                                    text = channelNum,
                                    color = FocusHighlight,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = uiState.activeChannel?.name ?: "未选频道",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (uiState.totalSources > 0) {
                                    val lineIndex = uiState.activeSourceIndex + 1
                                    val lineBadge = "线路 $lineIndex/${uiState.totalSources} [${uiState.activeSource?.protocol ?: "AUTO"}]"
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = lineBadge,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .background(DarkSurfaceElevated, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // EPG 节目单展示
                            if (uiState.activeChannel != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    uiState.currentProgram?.let { prog ->
                                        val timeStr = "${formatTime(prog.startTimestamp)} - ${formatTime(prog.endTimestamp)}"
                                        Text(
                                            text = "播出: ${prog.title} ($timeStr)",
                                            color = FocusHighlight,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    uiState.nextProgram?.let { prog ->
                                        Text(
                                            text = "下期: ${prog.title} (${formatTime(prog.startTimestamp)})",
                                            color = TextMuted,
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (onOpenEpgSchedule != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(DarkSurfaceElevated, RoundedCornerShape(4.dp))
                                                .clickable { onOpenEpgSchedule() }
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "📅 节目单",
                                                color = FocusHighlight,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部性能指标统计渐变栏（仅在能真实获取到指标时展示，未获取到的项绝不显示占位符）
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
                            .padding(horizontal = 28.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = statItems.joinToString(" | "),
                            color = TextMuted,
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
