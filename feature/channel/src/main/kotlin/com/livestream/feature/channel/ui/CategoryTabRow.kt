package com.livestream.feature.channel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ChannelCategory
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme

/**
 * Samsung One UI / TeleflowTv 规范流式药丸分类标签栏 (Pill Chips)
 * 具备均一高度与舒适点击/遥控热区，规避过窄或不统一的胶囊视觉
 */
@Composable
fun CategoryTabRow(
    categories: List<ChannelCategory>,
    selectedCategory: ChannelCategory?,
    onCategorySelected: (ChannelCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pillShape = OneUiShapes.pill

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category.id == selectedCategory?.id

            TvFocusableBox(
                onClick = { onCategorySelected(category) },
                shape = pillShape,
                unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.surfacePill,
                focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp
            ) { isFocused ->
                val targetTextColor = when {
                    isSelected -> Color.White
                    isFocused -> OneUiTheme.colors.accent
                    else -> OneUiTheme.colors.textPrimary
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .padding(horizontal = 16.dp),
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
