package com.livestream.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme

/**
 * 统一的 One UI 药丸搜索框组件：
 * 适用于手机竖屏、平板左栏与侧边频道抽屉，提供精致的矢量放大镜图标、焦点描边动画与一键清空按钮。
 */
@Composable
fun OneUiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索频道名称或台号...",
    height: Dp = 40.dp,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = OneUiShapes.pill
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.borderSubtle,
        animationSpec = tween(150),
        label = "search_border"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textMuted,
        animationSpec = tween(150),
        label = "search_icon"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .background(OneUiTheme.colors.cardSecondary, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 精致几何矢量搜索图标
            Canvas(modifier = Modifier.size(15.dp)) {
                val strokeWidth = 1.8.dp.toPx()
                val radius = size.minDimension * 0.36f
                val center = Offset(size.width * 0.42f, size.height * 0.42f)
                drawCircle(
                    color = iconColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = iconColor,
                    start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
                    end = Offset(size.width * 0.90f, size.height * 0.90f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(OneUiTheme.colors.accent),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusChange?.invoke(it.isFocused)
                    },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 12.5.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(OneUiShapes.pill)
                        .background(OneUiTheme.colors.surfacePill)
                        .clickable { onQueryChange("") }
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "清空",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
