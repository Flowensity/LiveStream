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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme

sealed interface GestureHudState {
    data object Hidden : GestureHudState
    data class Volume(val percentage: Int) : GestureHudState
    data class Brightness(val percentage: Int) : GestureHudState
    data class QuickSwitch(val title: String) : GestureHudState
}

/**
 * 现代 One UI 风格手势悬浮反馈胶囊 (带实时可视化进度条)
 */
@Composable
fun GestureHud(
    state: GestureHudState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is GestureHudState.Hidden,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val pillShape = RoundedCornerShape(20.dp)

        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, Color(0x2EFFFFFF)), pillShape)
                .background(Color(0xE012151E), pillShape)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            when (state) {
                is GestureHudState.Volume -> {
                    GestureProgressContent(
                        label = "音量",
                        percentage = state.percentage
                    )
                }
                is GestureHudState.Brightness -> {
                    GestureProgressContent(
                        label = "亮度",
                        percentage = state.percentage
                    )
                }
                is GestureHudState.QuickSwitch -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.title,
                            color = OneUiTheme.colors.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is GestureHudState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun GestureProgressContent(
    label: String,
    percentage: Int
) {
    val safePercent = percentage.coerceIn(0, 100)
    val progress = safePercent / 100f

    Column(
        modifier = Modifier.width(148.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$safePercent%",
                color = OneUiTheme.colors.accent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(OneUiShapes.pill)
                .background(Color(0x33FFFFFF))
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
