package com.livestream.feature.player.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceHealthStatus
import com.livestream.core.model.VideoAspectRatio
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.device.DeviceType
import com.livestream.core.ui.device.rememberDeviceType
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 综合「播放设置」右侧抽屉面板：
 * 集成：
 * 1. 线路快捷切换与实时响应延迟展示
 * 2. 画面比例切换 (原始比例 / 16:9 标准 / 4:3 经典 / 裁剪全屏)
 * 3. 今日完整 EPG 节目单与正在播出进度定位
 */
@Composable
fun EpgScheduleDialog(
    visible: Boolean,
    channel: Channel?,
    programs: List<EpgProgram>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isEpgLoading: Boolean = false,
    aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit = {},
    activeSourceIndex: Int = 0,
    totalSources: Int = 1,
    availableSources: List<LiveSource> = emptyList(),
    sourceLatenciesMs: Map<String, Long> = emptyMap(),
    onSwitchSource: () -> Unit = {},
    onSelectSource: (Int) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val deviceType = rememberDeviceType()
    val isTv = deviceType == DeviceType.TV
    val isLandscape = !isTv && (
        deviceType == DeviceType.PHONE_LANDSCAPE ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
    val canUseDualColumn = isLandscape && (configuration.screenWidthDp >= 520)

    val now = System.currentTimeMillis()
    val listState = rememberLazyListState()
    val firstFocusRequester = remember { FocusRequester() }
    val todayStr = remember { SimpleDateFormat("MM-dd", Locale.CHINA).format(Date()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }

    val currentIndex = remember(programs, now) {
        programs.indexOfFirst { it.startTimestamp <= now && it.endTimestamp > now }
    }

    LaunchedEffect(visible, programs, isTv) {
        if (!visible) return@LaunchedEffect
        if (programs.isNotEmpty()) {
            val targetIdx = if (currentIndex >= 0) currentIndex else 0
            listState.scrollToItem(maxOf(0, targetIdx - 1))
        }
        if (isTv) {
            delay(120)
            try {
                firstFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val drawerShape = OneUiShapes.panel
            val cardShape = OneUiShapes.cardMedium
            val safeTotalSources = totalSources.coerceAtLeast(1)
            val currentSourceNum = (activeSourceIndex + 1).coerceIn(1, safeTotalSources)
            val activeSource = availableSources.getOrNull(activeSourceIndex)
            val activeLatency = activeSource?.id?.let { sourceLatenciesMs[it] }

            val drawerWidth = if (canUseDualColumn) {
                minOf(452.dp, maxOf(390.dp, (configuration.screenWidthDp * 0.58f).dp))
            } else {
                304.dp
            }
            val drawerPadding = if (isLandscape) {
                PaddingValues(start = 8.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
            } else {
                PaddingValues(start = 8.dp, top = 14.dp, bottom = 14.dp, end = 14.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(drawerPadding)
                    .width(drawerWidth)
                    .clip(drawerShape)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
                    .background(OneUiTheme.colors.panelBackground)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部主标题栏：播放设置 + 频道名 + 关闭胶囊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "播放设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channel?.name ?: "当前频道"} · 线路、画面与节目单",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        TvFocusableBox(
                            onClick = onDismiss,
                            shape = OneUiShapes.pill,
                            unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                            focusedBackgroundColor = OneUiTheme.colors.accent,
                            focusedBorderColor = OneUiTheme.colors.accent,
                            focusedBorderWidth = 1.5.dp
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

                    Spacer(modifier = Modifier.height(8.dp))

                    if (canUseDualColumn) {
                        // 横屏双栏对称排版
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. 左栏：线路与画面
                            Column(
                                modifier = Modifier
                                    .width(196.dp)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = "线路与画面",
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
                                        .background(OneUiTheme.colors.cardBackground, cardShape)
                                        .padding(horizontal = 10.dp, vertical = 10.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SourceSwitchSection(
                                        currentSourceNum = currentSourceNum,
                                        safeTotalSources = safeTotalSources,
                                        activeSource = activeSource,
                                        activeLatency = activeLatency,
                                        availableSources = availableSources,
                                        activeSourceIndex = activeSourceIndex,
                                        sourceLatenciesMs = sourceLatenciesMs,
                                        onSwitchSource = onSwitchSource,
                                        onSelectSource = onSelectSource,
                                        focusRequester = if (isTv) firstFocusRequester else null
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(OneUiTheme.colors.divider)
                                    )

                                    AspectRatioSection(
                                        aspectRatio = aspectRatio,
                                        onAspectRatioSelected = onAspectRatioSelected
                                    )
                                }
                            }

                            // 2. 右栏：今日节目单
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "今日节目单 ($todayStr)",
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (programs.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "共 ${programs.size} 档",
                                            color = OneUiTheme.colors.textMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
                                        .background(OneUiTheme.colors.cardBackground, cardShape)
                                ) {
                                    if (programs.isEmpty()) {
                                        EpgEmptyState(
                                            isLoading = isEpgLoading,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            itemsIndexed(programs, key = { _, it -> it.id }) { _, prog ->
                                                EpgProgramItemRow(
                                                    prog = prog,
                                                    now = now,
                                                    timeFormat = timeFormat,
                                                    compact = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 竖屏与 TV 端经典垂直单列堆叠
                        SourceSwitchCard(
                            currentSourceNum = currentSourceNum,
                            safeTotalSources = safeTotalSources,
                            activeSource = activeSource,
                            activeLatency = activeLatency,
                            availableSources = availableSources,
                            activeSourceIndex = activeSourceIndex,
                            sourceLatenciesMs = sourceLatenciesMs,
                            onSwitchSource = onSwitchSource,
                            onSelectSource = onSelectSource,
                            cardShape = cardShape,
                            focusRequester = if (isTv) firstFocusRequester else null
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        AspectRatioCard(
                            aspectRatio = aspectRatio,
                            onAspectRatioSelected = onAspectRatioSelected,
                            cardShape = cardShape
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        EpgProgramList(
                            programs = programs,
                            listState = listState,
                            now = now,
                            todayStr = todayStr,
                            timeFormat = timeFormat,
                            cardShape = cardShape,
                            isLoading = isEpgLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSwitchSection(
    currentSourceNum: Int,
    safeTotalSources: Int,
    activeSource: LiveSource?,
    activeLatency: Long?,
    availableSources: List<LiveSource>,
    activeSourceIndex: Int,
    sourceLatenciesMs: Map<String, Long>,
    onSwitchSource: () -> Unit,
    onSelectSource: (Int) -> Unit,
    focusRequester: FocusRequester?
) {
    val sourceSubtitle = buildString {
        append(activeSource?.name ?: "默认线路")
        if (activeLatency != null && activeLatency > 0L) {
            append(" · ${activeLatency}ms")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "线路切换",
                color = OneUiTheme.colors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$currentSourceNum/$safeTotalSources",
                color = OneUiTheme.colors.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = sourceSubtitle,
            color = OneUiTheme.colors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        TvFocusableBox(
            onClick = onSwitchSource,
            shape = OneUiShapes.pill,
            unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
            focusedBackgroundColor = OneUiTheme.colors.accent,
            focusedBorderColor = OneUiTheme.colors.accent,
            focusedBorderWidth = 1.5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (safeTotalSources > 1) "切换下一条线路" else "重连当前线路",
                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        SelectableSourceList(
            availableSources = availableSources,
            activeSourceIndex = activeSourceIndex,
            sourceLatenciesMs = sourceLatenciesMs,
            onSelectSource = onSelectSource
        )
    }
}

@Composable
private fun AspectRatioSection(
    aspectRatio: VideoAspectRatio,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "画面比例",
                color = OneUiTheme.colors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = aspectRatio.label,
                color = OneUiTheme.colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            VideoAspectRatio.entries.chunked(2).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowEntries.forEach { ratio ->
                        AspectRatioButton(
                            ratio = ratio,
                            isSelected = aspectRatio == ratio,
                            onSelect = { onAspectRatioSelected(ratio) },
                            modifier = Modifier.weight(1f),
                            shortLabel = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSwitchCard(
    currentSourceNum: Int,
    safeTotalSources: Int,
    activeSource: LiveSource?,
    activeLatency: Long?,
    availableSources: List<LiveSource>,
    activeSourceIndex: Int,
    sourceLatenciesMs: Map<String, Long>,
    onSwitchSource: () -> Unit,
    onSelectSource: (Int) -> Unit,
    cardShape: RoundedCornerShape,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val sourceSubtitle = buildString {
        append(activeSource?.name ?: "默认线路")
        if (activeLatency != null && activeLatency > 0L) {
            append(" · 延迟 ${activeLatency}ms")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
            .background(OneUiTheme.colors.cardBackground, cardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "线路切换 ($currentSourceNum/$safeTotalSources)",
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sourceSubtitle,
                    color = OneUiTheme.colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            TvFocusableBox(
                onClick = onSwitchSource,
                shape = OneUiShapes.pill,
                unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                focusedBackgroundColor = OneUiTheme.colors.accent,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp,
                modifier = Modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            ) { isFocused ->
                Text(
                    text = if (safeTotalSources > 1) "切换下一线路" else "重连当前线路",
                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        SelectableSourceList(
            availableSources = availableSources,
            activeSourceIndex = activeSourceIndex,
            sourceLatenciesMs = sourceLatenciesMs,
            onSelectSource = onSelectSource
        )
    }
}

@Composable
private fun SelectableSourceList(
    availableSources: List<LiveSource>,
    activeSourceIndex: Int,
    sourceLatenciesMs: Map<String, Long>,
    onSelectSource: (Int) -> Unit
) {
    if (availableSources.size <= 1) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        availableSources.forEachIndexed { index, source ->
            val isSelected = index == activeSourceIndex
            val latencyMs = sourceLatenciesMs[source.id]
            val statusColor = when (source.healthStatus) {
                SourceHealthStatus.UNAVAILABLE -> OneUiTheme.colors.danger
                SourceHealthStatus.DEGRADED -> OneUiTheme.colors.statusWarning
                else -> if (isSelected) Color.White else OneUiTheme.colors.textSecondary
            }
            val metaText = buildString {
                if (latencyMs != null && latencyMs > 0L) append("${latencyMs}ms")
                else if (source.healthStatus == SourceHealthStatus.UNAVAILABLE) append("不可用")
                else if (isSelected) append("当前使用")
            }
            TvFocusableBox(
                onClick = { onSelectSource(index) },
                shape = RoundedCornerShape(9.dp),
                scaleOnFocus = 1.01f,
                unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.cardSecondary,
                focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${source.name}",
                        color = when {
                            isSelected -> Color.White
                            isFocused -> OneUiTheme.colors.accent
                            else -> OneUiTheme.colors.textPrimary
                        },
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    if (metaText.isNotEmpty()) {
                        Text(
                            text = metaText,
                            color = statusColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioCard(
    aspectRatio: VideoAspectRatio,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit,
    cardShape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
            .background(OneUiTheme.colors.cardBackground, cardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "画面比例",
                color = OneUiTheme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = aspectRatio.label,
                color = OneUiTheme.colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VideoAspectRatio.entries.forEach { ratio ->
                AspectRatioButton(
                    ratio = ratio,
                    isSelected = aspectRatio == ratio,
                    onSelect = { onAspectRatioSelected(ratio) },
                    modifier = Modifier.weight(1f),
                    shortLabel = true
                )
            }
        }
    }
}

@Composable
private fun AspectRatioButton(
    ratio: VideoAspectRatio,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    shortLabel: Boolean = true
) {
    TvFocusableBox(
        onClick = onSelect,
        shape = OneUiShapes.pill,
        unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.surfacePill,
        focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
        focusedBorderColor = OneUiTheme.colors.accent,
        focusedBorderWidth = 1.5.dp,
        modifier = modifier
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (shortLabel) ratio.label.substringBefore(" ") else ratio.label,
                color = if (isSelected) Color.White else if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpgProgramList(
    programs: List<EpgProgram>,
    listState: LazyListState,
    now: Long,
    todayStr: String,
    timeFormat: SimpleDateFormat,
    cardShape: RoundedCornerShape,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日节目单 ($todayStr)",
                color = OneUiTheme.colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (programs.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "共 ${programs.size} 档",
                    color = OneUiTheme.colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (programs.isEmpty()) {
            EpgEmptyState(
                isLoading = isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
                    .background(OneUiTheme.colors.cardBackground, cardShape)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                itemsIndexed(programs, key = { _, it -> it.id }) { _, prog ->
                    EpgProgramItemRow(
                        prog = prog,
                        now = now,
                        timeFormat = timeFormat,
                        compact = false
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgProgramItemRow(
    prog: EpgProgram,
    now: Long,
    timeFormat: SimpleDateFormat,
    compact: Boolean
) {
    val isCurrent = prog.startTimestamp <= now && prog.endTimestamp > now
    val isPast = prog.endTimestamp <= now
    val timeRange = "${timeFormat.format(Date(prog.startTimestamp))} - ${timeFormat.format(Date(prog.endTimestamp))}"
    val itemShape = RoundedCornerShape(if (compact) 10.dp else 12.dp)

    val itemBg = if (isCurrent) {
        OneUiTheme.colors.accentSoft
    } else if (compact) {
        OneUiTheme.colors.cardSecondary
    } else {
        OneUiTheme.colors.cardBackground
    }

    TvFocusableBox(
        onClick = {},
        shape = itemShape,
        scaleOnFocus = 1.0f,
        unfocusedBackgroundColor = itemBg,
        focusedBackgroundColor = OneUiTheme.colors.accentSoft,
        focusedBorderColor = OneUiTheme.colors.accent,
        focusedBorderWidth = 1.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (isCurrent) BorderStroke(1.5.dp, OneUiTheme.colors.accent)
                else BorderStroke(1.dp, OneUiTheme.colors.borderSubtle),
                itemShape
            )
    ) { isFocused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OneUiTheme.colors.accent)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "正在播出",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = prog.title,
                    color = when {
                        isCurrent || isFocused -> OneUiTheme.colors.accent
                        isPast -> OneUiTheme.colors.textMuted
                        else -> OneUiTheme.colors.textPrimary
                    },
                    fontSize = if (compact) 12.5.sp else 13.sp,
                    fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeRange,
                    color = when {
                        isCurrent || isFocused -> OneUiTheme.colors.accent.copy(alpha = 0.85f)
                        isPast -> OneUiTheme.colors.textMuted
                        else -> OneUiTheme.colors.textSecondary
                    },
                    fontSize = 11.sp
                )
                if (isPast) {
                    Text(
                        text = "已结束",
                        color = OneUiTheme.colors.textMuted,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgEmptyState(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isLoading) "正在同步今日节目单..." else "暂无今日节目单",
                color = OneUiTheme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (!isLoading) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "获取到频道节目数据后将在此自动显示",
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
