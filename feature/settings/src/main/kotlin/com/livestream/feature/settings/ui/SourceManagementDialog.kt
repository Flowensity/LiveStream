package com.livestream.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState

/**
 * 自定义源与节目管理弹窗
 */
@Composable
internal fun SourceManagementDialog(
    uiState: SettingsUiState,
    onCloseSourceManagement: () -> Unit,
    onDeleteCustomPlaylistSource: (CustomPlaylistSource) -> Unit,
    onDeleteSingleCustomChannel: (String) -> Unit,
    onClearAllCustomSources: () -> Unit,
    onRefreshCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onRefreshAllCustomSources: () -> Unit = {},
    sourceCloseFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val sourceDialogShape = OneUiShapes.panel
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), sourceDialogShape)
                .background(OneUiTheme.colors.panelBackground, sourceDialogShape)
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自定义源管理",
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val closeModifier = if (sourceCloseFocusRequester != null) {
                        Modifier.focusRequester(sourceCloseFocusRequester)
                    } else {
                        Modifier
                    }
                    TvFocusableBox(
                        onClick = onCloseSourceManagement,
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        focusedBorderWidth = 1.5.dp,
                        modifier = closeModifier
                    ) { isFocused ->
                        Text(
                            text = "关闭",
                            color = if (isFocused) Color.White else OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.customChannels.isNotEmpty() || uiState.customSources.isNotEmpty()) {
                    // 统计与全部清空栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                            .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "共 ${uiState.customChannels.size} 个自定义节目",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.customSources.size} 个已导入源列表",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (uiState.customSources.any { it.url.startsWith("http") }) {
                                TvFocusableBox(
                                    onClick = onRefreshAllCustomSources,
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                    focusedBackgroundColor = OneUiTheme.colors.accent,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.dp
                                ) { isFocused ->
                                    Text(
                                        text = if (uiState.refreshingSourceId != null) "刷新中" else "刷新全部",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            TvFocusableBox(
                                onClick = onClearAllCustomSources,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.dangerSoft,
                                focusedBackgroundColor = OneUiTheme.colors.danger,
                                focusedBorderColor = OneUiTheme.colors.danger,
                                focusedBorderWidth = 1.dp
                            ) { isFocused ->
                                Text(
                                    text = "清空全部",
                                    color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 订阅源列表
                    if (uiState.customSources.isNotEmpty()) {
                        Text(
                            text = "已导入源列表：",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.customSources.forEach { src ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), RoundedCornerShape(8.dp))
                                        .background(OneUiTheme.colors.cardBackground, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text(
                                            text = src.name,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val epgSuffix = if (src.epgUrl.isNotBlank()) " · 含 XMLTV 节目单" else ""
                                        Text(
                                            text = "${src.channelCount} 个节目$epgSuffix",
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (src.url.startsWith("http")) {
                                            val isRefreshingThis = uiState.refreshingSourceId == src.id
                                            TvFocusableBox(
                                                onClick = { onRefreshCustomPlaylistSource(src) },
                                                shape = OneUiShapes.pill,
                                                unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                                focusedBorderColor = OneUiTheme.colors.accent,
                                                focusedBorderWidth = 1.dp
                                            ) { isFocused ->
                                                Text(
                                                    text = if (isRefreshingThis) "刷新中" else "刷新",
                                                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        TvFocusableBox(
                                            onClick = { onDeleteCustomPlaylistSource(src) },
                                            shape = OneUiShapes.pill,
                                            unfocusedBackgroundColor = OneUiTheme.colors.dangerSoft,
                                            focusedBackgroundColor = OneUiTheme.colors.danger,
                                            focusedBorderColor = OneUiTheme.colors.danger,
                                            focusedBorderWidth = 1.dp
                                        ) { isFocused ->
                                            Text(
                                                text = "删除源",
                                                color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // 节目清单滚动区
                    Text(
                        text = "自定义节目单：",
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(uiState.customChannels, key = { it.id }) { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), RoundedCornerShape(6.dp))
                                    .background(OneUiTheme.colors.cardBackground, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ch.name,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                                )
                                TvFocusableBox(
                                    onClick = { onDeleteSingleCustomChannel(ch.id) },
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = Color.Transparent,
                                    focusedBackgroundColor = OneUiTheme.colors.danger,
                                    focusedBorderColor = OneUiTheme.colors.danger,
                                    focusedBorderWidth = 1.dp
                                ) { isFocused ->
                                    Text(
                                        text = "删除",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 空态提示
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "暂无自定义源与节目",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "可通过扫码或输入链接导入 M3U 源",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 10.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
