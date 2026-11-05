package com.livestream.feature.channel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ChannelCategory
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiTheme

/**
 * Samsung One UI 风格流式药丸分类标签栏 (Pill Chips)：
 * 1. 采用 One UI 标志性全圆角胶囊药丸 (RoundedCornerShape(50))
 * 2. 选中态呈现三星专属柔和蓝与高对比文字
 * 3. 未选中态呈现精致卡片底色与极细微边界线
 */
@Composable
fun CategoryTabRow(
    categories: List<ChannelCategory>,
    selectedCategory: ChannelCategory?,
    onCategorySelected: (ChannelCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pillShape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category.id == selectedCategory?.id

            TvFocusableBox(
                onClick = { onCategorySelected(category) },
                shape = pillShape,
                unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.cardBackground,
                focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.cardSecondary,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp
            ) { isFocused ->
                val targetTextColor = when {
                    isSelected -> Color.White
                    isFocused -> OneUiTheme.colors.accent
                    else -> OneUiTheme.colors.textSecondary
                }

                val borderModifier = if (!isSelected && !isFocused) {
                    Modifier.border(BorderStroke(1.dp, OneUiTheme.colors.divider), pillShape)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .then(borderModifier)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.name,
                        color = targetTextColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
