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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

/**
 * 自定义源与自定义频道管理弹窗：
 * 采用宽敞响应式卡片布局、分行操作按钮栏与清空二次确认保护，彻底消除窄屏按钮挤压与误删风险。
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
    onProbeCustomSources: () -> Unit = {},
    onRemoveUnavailableCustomChannels: () -> Unit = {},
    sourceCloseFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val sourceDialogShape = OneUiShapes.dialog
    var confirmClearAll by remember { mutableStateOf(false) }
    val confirmClearFocusRequester = remember { FocusRequester() }

    LaunchedEffect(confirmClearAll) {
        if (confirmClearAll) {
            delay(80)
            try {
                confirmClearFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(min = 320.dp, max = 420.dp)
                .fillMaxWidth(0.92f)
                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), sourceDialogShape)
                .background(OneUiTheme.colors.panelBackground, sourceDialogShape)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自定义源管理",
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "管理已导入的播放列表与自定义频道",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.customChannels.isNotEmpty() || uiState.customSources.isNotEmpty()) {
                    // 1. 统计概览与操作栏卡片（上下分行排版，告别按钮挤压）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                            .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "共 ${uiState.customChannels.size} 个自定义频道",
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "来自 ${uiState.customSources.size} 个已导入的播放列表",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }

                        // 操作按钮栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.customChannels.isNotEmpty()) {
                                TvFocusableBox(
                                    onClick = onProbeCustomSources,
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = OneUiTheme.colors.accent,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.dp
                                ) { isFocused ->
                                    Text(
                                        text = if (uiState.isProbingSources) "正在检测..." else "检测线路",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (uiState.unavailableCustomChannelIds.isNotEmpty()) {
                                TvFocusableBox(
                                    onClick = onRemoveUnavailableCustomChannels,
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.dangerSoft,
                                    focusedBackgroundColor = OneUiTheme.colors.danger,
                                    focusedBorderColor = OneUiTheme.colors.danger,
                                    focusedBorderWidth = 1.dp
                                ) { isFocused ->
                                    Text(
                                        text = "清理失效 (${uiState.unavailableCustomChannelIds.size})",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
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
                                        text = if (uiState.refreshingSourceId != null) "正在刷新..." else "刷新全部",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (confirmClearAll) {
                                TvFocusableBox(
                                    onClick = { confirmClearAll = false },
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                    focusedBackgroundColor = OneUiTheme.colors.accent,
                                    focusedBorderColor = OneUiTheme.colors.accent,
                                    focusedBorderWidth = 1.dp
                                ) { isFocused ->
                                    Text(
                                        text = "取消",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                                TvFocusableBox(
                                    onClick = {
                                        confirmClearAll = false
                                        onClearAllCustomSources()
                                    },
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.danger,
                                    focusedBackgroundColor = OneUiTheme.colors.danger,
                                    focusedBorderColor = Color.White,
                                    focusedBorderWidth = 1.5.dp,
                                    modifier = Modifier.focusRequester(confirmClearFocusRequester)
                                ) { _ ->
                                    Text(
                                        text = "确认清空",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                TvFocusableBox(
                                    onClick = { confirmClearAll = true },
                                    shape = OneUiShapes.pill,
                                    unfocusedBackgroundColor = OneUiTheme.colors.dangerSoft,
                                    focusedBackgroundColor = OneUiTheme.colors.danger,
                                    focusedBorderColor = OneUiTheme.colors.danger,
                                    focusedBorderWidth = 1.dp
                                ) { isFocused ->
                                    Text(
                                        text = "清空全部",
                                        color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    val summary = uiState.sourceHealthSummary
                    if (!summary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = summary,
                            color = OneUiTheme.colors.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 已导入的播放列表
                    if (uiState.customSources.isNotEmpty()) {
                        Text(
                            text = "已导入的播放列表",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            uiState.customSources.forEach { src ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), RoundedCornerShape(10.dp))
                                        .background(OneUiTheme.colors.cardBackground, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = src.name,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val epgSuffix = if (src.epgUrl.isNotBlank()) " · 含节目单数据" else ""
                                        Text(
                                            text = "${src.channelCount} 个频道$epgSuffix",
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                                    text = if (isRefreshingThis) "刷新中..." else "刷新",
                                                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
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
                                                text = "移除列表",
                                                color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 3. 自定义频道清单滚动区
                    if (uiState.customChannels.isNotEmpty()) {
                        Text(
                            text = "自定义频道列表",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 172.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(uiState.customChannels, key = { it.id }) { ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), RoundedCornerShape(8.dp))
                                        .background(OneUiTheme.colors.cardBackground, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ch.name,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    if (ch.id in uiState.unavailableCustomChannelIds) {
                                        Text(
                                            text = "无法连接",
                                            color = OneUiTheme.colors.danger,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }
                                    TvFocusableBox(
                                        onClick = { onDeleteSingleCustomChannel(ch.id) },
                                        shape = OneUiShapes.pill,
                                        unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                        focusedBackgroundColor = OneUiTheme.colors.danger,
                                        focusedBorderColor = OneUiTheme.colors.danger,
                                        focusedBorderWidth = 1.dp
                                    ) { isFocused ->
                                        Text(
                                            text = "移除",
                                            color = if (isFocused) Color.White else OneUiTheme.colors.danger,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 空态提示
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "暂无自定义频道",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "可在「导入 M3U 播放列表」中通过订阅链接、文本或扫码添加频道",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
