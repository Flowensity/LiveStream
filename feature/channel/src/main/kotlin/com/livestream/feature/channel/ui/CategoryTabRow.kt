package com.livestream.feature.channel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.ChannelCategory
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.DarkSurface
import com.livestream.core.ui.theme.DarkSurfaceElevated
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary

@Composable
fun CategoryTabRow(
    categories: List<ChannelCategory>,
    selectedCategory: ChannelCategory?,
    onCategorySelected: (ChannelCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category.id == selectedCategory?.id
            TvFocusableBox(
                onClick = { onCategorySelected(category) },
                unfocusedBackgroundColor = if (isSelected) DarkSurfaceElevated else DarkSurface,
                focusedBackgroundColor = DarkSurfaceElevated,
                focusedBorderColor = FocusHighlight
            ) { isFocused ->
                val textColor = when {
                    isFocused -> FocusHighlight
                    isSelected -> TextPrimary
                    else -> TextMuted
                }
                Text(
                    text = category.name,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
