package com.livestream.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.theme.OneUiTheme

/**
 * 播放器中央悬浮加载指示器：
 * 采用深色高对比度半透明毛玻璃胶囊底座，确保在浅色/深色主题及高亮视频画面上均清晰醒目。
 */
@Composable
fun StreamLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = "正在加载画面..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dots")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(540, delayMillis = 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(540, delayMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    val containerShape = RoundedCornerShape(20.dp)
    val accent = OneUiTheme.colors.accent

    Box(
        modifier = modifier
            .border(BorderStroke(1.dp, Color(0x26FFFFFF)), containerShape)
            .background(Color(0xD912151E), containerShape)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Dot(scale = dot1Scale, color = accent)
                Spacer(modifier = Modifier.width(8.dp))
                Dot(scale = dot2Scale, color = accent)
                Spacer(modifier = Modifier.width(8.dp))
                Dot(scale = dot3Scale, color = accent)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = Color(0xFFF3F4F8),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Dot(
    scale: Float,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .scale(scale)
            .alpha((scale - 0.35f).coerceIn(0.45f, 1f))
            .background(color, CircleShape)
    )
}
