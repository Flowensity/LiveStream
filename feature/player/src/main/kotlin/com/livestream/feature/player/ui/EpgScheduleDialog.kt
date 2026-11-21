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
 * 综合「播放菜单」右侧抽屉面板 (PlaybackMenuDrawerView)：
 * 集成：
 * 1. 信号源 / 线路快捷切换与延迟展示
 * 2. 画面比例切换 (原始比例 / 16:9 标准 / 4:3 经典 / 裁剪全屏)
 * 3. 今日完整 EPG 节目单排期与正在播出定位
 *
 * 手机横屏模式优化：
 * 采用左右严格对称的双栏布局，左侧统一为「播放控制」卡片，右侧统一为「今日节目单」卡片，
 * 双方拥有高度一致的顶部标题栏与满高统一卡片容器，彻底消除左右高度不一致与单侧突兀偏高的问题。
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
    // 宽屏横屏下启用对称双栏；若处于分屏多任务等极窄横屏 (sw < 520dp)，平滑回退单列
    val canUseDualColumn = isLandscape && (configuration.screenWidthDp >= 520)

    val now = System.currentTimeMillis()
    val listState = rememberLazyListState()
    val firstFocusRequester = remember { FocusRequester() }
    val todayStr = remember { SimpleDateFormat("MM-dd", Locale.CHINA).format(Date()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }

    val currentIndex = remember(programs, now) {
        programs.indexOfFirst { it.startTimestamp <= now && it.endTimestamp > now }
    }

    // 自动滚动节目单至当前正在播出的节目，并在 TV 端将初始焦点落在首项控制卡片上
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
        // 半透明背景遮罩（点击可关闭）
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
            val drawerShape = RoundedCornerShape(20.dp)
            val cardShape = OneUiShapes.cardMedium
            val safeTotalSources = totalSources.coerceAtLeast(1)
            val currentSourceNum = (activeSourceIndex + 1).coerceIn(1, safeTotalSources)
            val activeSource = availableSources.getOrNull(activeSourceIndex)
            val activeLatency = activeSource?.id?.let { sourceLatenciesMs[it] }

            // 横屏下适度加宽至 380~440dp (不超过屏幕 58%)，竖屏/TV 保持 264dp 紧凑标准
            val drawerWidth = if (canUseDualColumn) {
                minOf(440.dp, maxOf(380.dp, (configuration.screenWidthDp * 0.58f).dp))
            } else {
                OneUiDimensions.panelPlaylistWidth
            }
            val drawerPadding = if (isLandscape) {
                PaddingValues(start = 8.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
            } else {
                PaddingValues(start = 8.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(drawerPadding)
                    .width(drawerWidth)
                    .clip(drawerShape)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
                    .background(OneUiTheme.colors.panelBackground)
                    .padding(vertical = 12.dp, horizontal = 10.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部主标题栏：播放菜单 + 频道名 + 关闭胶囊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "播放菜单",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channel?.name ?: "当前频道"} · 线路、画面与节目单",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.sp,
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (canUseDualColumn) {
                        // 横屏双栏对称排版：两栏拥有严格对齐的顶部小标头与高度完全一致的卡片容器
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. 左栏：播放控制
                            Column(
                                modifier = Modifier
                                    .width(190.dp)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = "播放控制",
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
                                    // 信号源切换区块
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

                                    // 纤细分割线
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(OneUiTheme.colors.borderSubtle.copy(alpha = 0.5f))
                                    )

                                    // 画面比例区块
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
                                            fontSize = 10.5.sp,
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
                                                val isCurrent = prog.startTimestamp <= now && prog.endTimestamp > now
                                                val timeRange = "${timeFormat.format(Date(prog.startTimestamp))} - ${timeFormat.format(Date(prog.endTimestamp))}"
                                                val itemShape = RoundedCornerShape(10.dp)

                                                val itemBg = if (isCurrent) {
                                                    OneUiTheme.colors.accentSoft
                                                } else {
                                                    OneUiTheme.colors.cardSecondary
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
                                                            else BorderStroke(1.dp, OneUiTheme.colors.borderSubtle.copy(alpha = 0.5f)),
                                                            itemShape
                                                        )
                                                ) { isFocused ->
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 7.dp)
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
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "正在播出",
                                                                        color = Color.White,
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                            }
                                                            Text(
                                                                text = prog.title,
                                                                color = if (isCurrent || isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                                                fontSize = 12.5.sp,
                                                                fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                        }

                                                        Text(
                                                            text = timeRange,
                                                            color = if (isCurrent || isFocused) OneUiTheme.colors.accent.copy(alpha = 0.85f) else OneUiTheme.colors.textSecondary,
                                                            fontSize = 10.5.sp,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        )
                                                    }
                                                }
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

/**
 * 横屏双栏内嵌：信号源切换子区块
 */
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
        append(activeSource?.name ?: "默认主线路")
        if (activeLatency != null && activeLatency > 0L) {
            append(" · 延迟 ${activeLatency}ms")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "信号源切换",
                color = OneUiTheme.colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$currentSourceNum/$safeTotalSources",
                color = OneUiTheme.colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = sourceSubtitle,
            color = OneUiTheme.colors.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
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
                    text = if (safeTotalSources > 1) "切换下一线路" else "重新连接线路",
                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                    fontSize = 11.sp,
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

/**
 * 横屏双栏内嵌：画面比例切换子区块
 */
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = aspectRatio.label,
                color = OneUiTheme.colors.accent,
                fontSize = 10.sp,
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

/**
 * 竖屏与 TV 独立卡片：信号源与线路切换卡片
 */
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
        append(activeSource?.name ?: "默认主线路")
        if (activeLatency != null && activeLatency > 0L) {
            append(" · 延迟 ${activeLatency}ms")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
            .background(OneUiTheme.colors.cardBackground, cardShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "信号源切换 ($currentSourceNum/$safeTotalSources)",
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sourceSubtitle,
                    color = OneUiTheme.colors.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
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
                    text = if (safeTotalSources > 1) "切换下一线路" else "重新连接线路",
                    color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
            .padding(top = 6.dp),
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
                else if (source.healthStatus == SourceHealthStatus.UNAVAILABLE) append("异常")
                else if (isSelected) append("当前")
            }
            TvFocusableBox(
                onClick = { onSelectSource(index) },
                shape = RoundedCornerShape(8.dp),
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
                        .padding(horizontal = 8.dp, vertical = 5.dp),
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
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    if (metaText.isNotEmpty()) {
                        Text(
                            text = metaText,
                            color = statusColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 竖屏与 TV 独立卡片：画面比例卡片
 */
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
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
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
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 竖屏与 TV 列表：今日节目单列表
 */
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
                    fontSize = 10.5.sp,
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
                    val isCurrent = prog.startTimestamp <= now && prog.endTimestamp > now
                    val timeRange = "${timeFormat.format(Date(prog.startTimestamp))} - ${timeFormat.format(Date(prog.endTimestamp))}"
                    val itemShape = RoundedCornerShape(12.dp)

                    val itemBg = if (isCurrent) {
                        OneUiTheme.colors.accentSoft
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
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = prog.title,
                                    color = if (isCurrent || isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            Text(
                                text = timeRange,
                                color = if (isCurrent || isFocused) OneUiTheme.colors.accent.copy(alpha = 0.85f) else OneUiTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
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
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isLoading) "正在获取最新节目排期..." else "暂无该频道节目单",
            color = OneUiTheme.colors.textSecondary,
            fontSize = 12.5.sp
        )
    }
}
