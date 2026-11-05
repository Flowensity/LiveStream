package com.livestream.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.model.Channel
import com.livestream.core.model.CustomPlaylistSource
import com.livestream.core.model.ThemeMode
import com.livestream.core.ui.component.TvFocusableBox
import com.livestream.core.ui.theme.OneUiDimensions
import com.livestream.core.ui.theme.OneUiShapes
import com.livestream.core.ui.theme.OneUiTheme
import com.livestream.feature.settings.SettingsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规范系统与偏好设置右侧抽屉面板 (SettingsDrawerView, 居右对齐，与频道列表 ChannelDrawer 风格高度统一)
 */
@Composable
fun SettingsDialog(
    uiState: SettingsUiState,
    onCloseSettings: () -> Unit,
    onToggleHardwareDecode: (Boolean) -> Unit,
    onToggleSurfaceView: (Boolean) -> Unit,
    onOpenImportDialog: () -> Unit,
    onCloseImportDialog: () -> Unit,
    onImportTextChanged: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onToggleBootStartup: (Boolean) -> Unit = {},
    onOpenSourceManagement: () -> Unit = {},
    onCloseSourceManagement: () -> Unit = {},
    onDeleteCustomPlaylistSource: (CustomPlaylistSource) -> Unit = {},
    onDeleteSingleCustomChannel: (String) -> Unit = {},
    onClearAllCustomSources: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!uiState.isSettingsOpen && !uiState.isImportDialogOpen && !uiState.isSourceManagementOpen) return

    val firstFocusRequester = remember { FocusRequester() }
    val importCancelFocusRequester = remember { FocusRequester() }
    var isQrEnlarged by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSettingsOpen, uiState.isImportDialogOpen, uiState.isSourceManagementOpen) {
        if (uiState.isImportDialogOpen) {
            kotlinx.coroutines.delay(150)
            try {
                importCancelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (uiState.isSettingsOpen) {
            firstFocusRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 半透明背景遮罩（点击可关闭当前浮层）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (uiState.isSourceManagementOpen) {
                        onCloseSourceManagement()
                    } else if (uiState.isImportDialogOpen) {
                        onCloseImportDialog()
                    } else {
                        onCloseSettings()
                    }
                }
        )

        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val drawerShape = RoundedCornerShape(20.dp)
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 8.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)
                    .width(OneUiDimensions.panelPlaylistWidth)
                    .clip(drawerShape)
                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), drawerShape)
                    .background(OneUiTheme.colors.panelBackground)
                    .padding(vertical = 12.dp, horizontal = 10.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部大标题与关闭胶囊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统与设置",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "播放与偏好配置",
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        TvFocusableBox(
                            onClick = onCloseSettings,
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 设置内容滚动区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        SettingsContent(
                            uiState = uiState,
                            onOpenImportDialog = onOpenImportDialog,
                            onOpenSourceManagement = onOpenSourceManagement,
                            onThemeModeSelected = onThemeModeSelected,
                            onToggleHardwareDecode = onToggleHardwareDecode,
                            onToggleSurfaceView = onToggleSurfaceView,
                            onToggleBootStartup = onToggleBootStartup,
                            firstFocusRequester = firstFocusRequester
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 内嵌导入 M3U 弹窗 (宽度约 260~270dp，较原先减半)
        if (uiState.isImportDialogOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                val importDialogShape = OneUiShapes.panel

                Box(
                    modifier = Modifier
                        .width(268.dp)
                        .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), importDialogShape)
                        .background(OneUiTheme.colors.panelBackground, importDialogShape)
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "导入 M3U 列表",
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TvFocusableBox(
                                onClick = onCloseImportDialog,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp
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

                        // 1. Android TV 局域网扫码推送区域（带放大按钮）
                        if (uiState.qrBitmap != null && uiState.localServerUrl != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), OneUiShapes.cardMedium)
                                    .background(OneUiTheme.colors.cardBackground, OneUiShapes.cardMedium)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isQrEnlarged = true }
                                ) {
                                    Image(
                                        bitmap = uiState.qrBitmap.asImageBitmap(),
                                        contentDescription = "局域网推送二维码",
                                        modifier = Modifier
                                            .size(62.dp)
                                            .background(Color.White)
                                            .padding(2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "局域网扫码推流",
                                        color = OneUiTheme.colors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = uiState.localServerUrl,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 二维码放大按钮 (TvFocusableBox)
                                    TvFocusableBox(
                                        onClick = { isQrEnlarged = true },
                                        shape = OneUiShapes.pill,
                                        unfocusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                        focusedBackgroundColor = OneUiTheme.colors.accent,
                                        focusedBorderColor = OneUiTheme.colors.accent,
                                        focusedBorderWidth = 1.dp
                                    ) { isFocused ->
                                        Text(
                                            text = "放大二维码",
                                            color = if (isFocused) Color.White else OneUiTheme.colors.accent,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 2. M3U 订阅链接或内容直接输入框
                        Text(
                            text = "或粘贴链接 / M3U 文本：",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        val inputShape = OneUiShapes.cardMedium
                        BasicTextField(
                            value = uiState.importText,
                            onValueChange = onImportTextChanged,
                            textStyle = TextStyle(
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 11.sp
                            ),
                            cursorBrush = SolidColor(OneUiTheme.colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), inputShape)
                                .background(OneUiTheme.colors.cardBackground, inputShape)
                                .padding(8.dp),
                            decorationBox = { innerTextField ->
                                if (uiState.importText.isEmpty()) {
                                    Text(
                                        text = "支持 http(s):// 地址、GitHub 订阅或 M3U 文本...",
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (uiState.importError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.importError,
                                color = OneUiTheme.colors.danger,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (uiState.importSuccessCount != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "成功导入 ${uiState.importSuccessCount} 个频道！",
                                color = OneUiTheme.colors.statusSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvFocusableBox(
                                onClick = onCloseImportDialog,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp,
                                modifier = Modifier.focusRequester(importCancelFocusRequester)
                            ) { isFocused ->
                                Text(
                                    text = "取消",
                                    color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TvFocusableBox(
                                onClick = { if (!uiState.isImporting) onConfirmImport() },
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = if (uiState.isImporting) OneUiTheme.colors.accent.copy(alpha = 0.5f) else OneUiTheme.colors.accent,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = Color.White,
                                focusedBorderWidth = 2.dp
                            ) { _ ->
                                Text(
                                    text = if (uiState.isImporting) "导入中..." else "确认导入",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 二维码全屏居中超大放大弹窗 (支持电视遥控器一键关闭)
        if (isQrEnlarged && uiState.qrBitmap != null) {
            val qrCloseFocusRequester = remember { FocusRequester() }
            LaunchedEffect(isQrEnlarged) {
                if (isQrEnlarged) {
                    kotlinx.coroutines.delay(100)
                    try {
                        qrCloseFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { isQrEnlarged = false }
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Back ||
                             keyEvent.key == Key.Escape ||
                             keyEvent.key == Key.DirectionCenter ||
                             keyEvent.key == Key.Enter)
                        ) {
                            isQrEnlarged = false
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "手机扫码推流至电视",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = uiState.qrBitmap.asImageBitmap(),
                            contentDescription = "放大二维码",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.localServerUrl ?: "",
                        color = OneUiTheme.colors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TvFocusableBox(
                        onClick = { isQrEnlarged = false },
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = Color(0x33FFFFFF),
                        focusedBackgroundColor = OneUiTheme.colors.accent,
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.dp,
                        modifier = Modifier.focusRequester(qrCloseFocusRequester)
                    ) { _ ->
                        Text(
                            text = "关闭放大",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 4. 自定义源与节目管理弹窗
        if (uiState.isSourceManagementOpen) {
            val sourceDialogShape = OneUiShapes.panel
            Box(
                modifier = Modifier
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
                            TvFocusableBox(
                                onClick = onCloseSourceManagement,
                                shape = OneUiShapes.pill,
                                unfocusedBackgroundColor = OneUiTheme.colors.surfacePill,
                                focusedBackgroundColor = OneUiTheme.colors.accent,
                                focusedBorderColor = OneUiTheme.colors.accent,
                                focusedBorderWidth = 1.5.dp
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
                                                Text(
                                                    text = "${src.channelCount} 个节目",
                                                    color = OneUiTheme.colors.textSecondary,
                                                    fontSize = 9.5.sp
                                                )
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
    }
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onOpenImportDialog: () -> Unit,
    onOpenSourceManagement: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onToggleHardwareDecode: (Boolean) -> Unit,
    onToggleSurfaceView: (Boolean) -> Unit,
    onToggleBootStartup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 频道源管理卡片
        SettingsSectionTitle("频道源管理")
        OneUiGroupCard {
            TvFocusableBox(
                onClick = onOpenImportDialog,
                shape = OneUiShapes.cardMedium,
                unfocusedBackgroundColor = Color.Transparent,
                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "导入 M3U 播放列表",
                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "局域网扫码推送 / 网络链接",
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(OneUiShapes.pill)
                            .background(OneUiTheme.colors.accentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "导入",
                            color = OneUiTheme.colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OneUiCardDivider()

            TvFocusableBox(
                onClick = onOpenSourceManagement,
                shape = OneUiShapes.cardMedium,
                unfocusedBackgroundColor = Color.Transparent,
                focusedBackgroundColor = OneUiTheme.colors.accentSoft,
                focusedBorderColor = OneUiTheme.colors.accent,
                focusedBorderWidth = 1.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "自定义源管理",
                            color = if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.customChannels.isNotEmpty()) {
                                "已导入 ${uiState.customChannels.size} 个节目 (${uiState.customSources.size} 个源)"
                            } else {
                                "管理或删除自定义源与节目"
                            },
                            color = if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(OneUiShapes.pill)
                            .background(
                                if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accentSoft
                                else OneUiTheme.colors.surfacePill
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.customChannels.isNotEmpty()) "管理" else "查看",
                            color = if (uiState.customChannels.isNotEmpty()) OneUiTheme.colors.accent else OneUiTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 界面主题模式卡片
        SettingsSectionTitle("界面主题")
        OneUiGroupCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val modes = listOf(
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                    ThemeMode.SYSTEM to "跟随系统"
                )
                modes.forEach { (mode, label) ->
                    val isSelected = uiState.themeMode == mode
                    TvFocusableBox(
                        onClick = { onThemeModeSelected(mode) },
                        shape = OneUiShapes.pill,
                        unfocusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.surfacePill,
                        focusedBackgroundColor = if (isSelected) OneUiTheme.colors.accent else OneUiTheme.colors.accentSoft,
                        focusedBorderColor = OneUiTheme.colors.accent,
                        focusedBorderWidth = 1.5.dp,
                        modifier = Modifier.weight(1f)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else if (isFocused) OneUiTheme.colors.accent else OneUiTheme.colors.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 画面与解码卡片
        SettingsSectionTitle("画面与解码")
        OneUiGroupCard {
            val hwdModifier = if (firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier
            SettingToggleRow(
                title = "硬件加速解码",
                subtitle = "MediaCodec GPU 硬解，低功耗高帧率",
                checked = uiState.isHardwareDecodePreferred,
                onCheckedChange = onToggleHardwareDecode,
                modifier = hwdModifier
            )
            OneUiCardDivider()
            SettingToggleRow(
                title = "SurfaceView 优先",
                subtitle = "专用硬件视频图层，画面流畅",
                checked = uiState.isSurfaceViewPreferred,
                onCheckedChange = onToggleSurfaceView
            )
        }

        // 系统设置卡片（开机自启动）
        SettingsSectionTitle("系统设置")
        OneUiGroupCard {
            SettingToggleRow(
                title = "开机自启动",
                subtitle = "设备开机后自动拉起直播并播放",
                checked = uiState.isBootStartupEnabled,
                onCheckedChange = onToggleBootStartup
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = OneUiTheme.colors.textSecondary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun OneUiGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = OneUiShapes.cardMedium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, OneUiTheme.colors.borderSubtle), cardShape)
            .background(OneUiTheme.colors.cardBackground, cardShape)
    ) {
        content()
    }
}

@Composable
private fun OneUiCardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OneUiTheme.colors.divider)
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = OneUiTheme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = OneUiTheme.colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OneUiTheme.colors.accent,
                uncheckedThumbColor = OneUiTheme.colors.textMuted,
                uncheckedTrackColor = OneUiTheme.colors.surfacePill
            )
        )
    }
}
