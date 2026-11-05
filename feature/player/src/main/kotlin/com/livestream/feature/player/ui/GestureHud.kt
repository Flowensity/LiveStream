package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.theme.OneUiTheme

sealed interface GestureHudState {
    data object Hidden : GestureHudState
    data class Volume(val percentage: Int) : GestureHudState
    data class Brightness(val percentage: Int) : GestureHudState
    data class QuickSwitch(val title: String) : GestureHudState
}

/**
 * Samsung One UI 风格手势悬浮反馈胶囊 (One UI Pill HUD)
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
        val pillShape = RoundedCornerShape(50)

        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, Color(0x22FFFFFF)), pillShape)
                .background(Color(0xDD1A1B20), pillShape)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            when (state) {
                is GestureHudState.Volume -> {
                    Text(
                        text = "音量 ${state.percentage}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                is GestureHudState.Brightness -> {
                    Text(
                        text = "亮度 ${state.percentage}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
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
