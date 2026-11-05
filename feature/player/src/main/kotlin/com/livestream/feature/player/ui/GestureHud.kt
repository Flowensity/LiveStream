package com.livestream.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.TextPrimary

sealed interface GestureHudState {
    data object Hidden : GestureHudState
    data class Volume(val percentage: Int) : GestureHudState
    data class Brightness(val percentage: Int) : GestureHudState
    data class QuickSwitch(val title: String) : GestureHudState
}

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
        Box(
            modifier = Modifier
                .background(DarkSurfaceElevated.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            when (state) {
                is GestureHudState.Volume -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔊", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "音量 ${state.percentage}%",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is GestureHudState.Brightness -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "☀", fontSize = 18.sp, color = FocusHighlight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "亮度 ${state.percentage}%",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is GestureHudState.QuickSwitch -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.title,
                            color = FocusHighlight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is GestureHudState.Hidden -> Unit
            }
        }
    }
}
