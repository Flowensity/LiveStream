package com.livestream.core.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Samsung One UI 7/8 Design Tokens (Light & Dark Theme)
// =========================================================================

// --- 浅色主题 (Light Mode) ---
val OneUiLightBackground = Color(0xFFF5F6FA)          // 页面背景
val OneUiLightCard = Color(0xFFFFFFFF)                // 主卡片背景
val OneUiLightCardSecondary = Color(0xFFEEF1F7)       // 次级/辅助卡片背景与输入框底色
val OneUiLightCardElevated = Color(0xFFF0F3F9)        // 悬浮/交互卡片背景
val OneUiLightTextPrimary = Color(0xFF191B20)         // 主文字 (高对比墨黑)
val OneUiLightTextSecondary = Color(0xFF74777F)       // 次级文字 (柔和冷灰)
val OneUiLightTextMuted = Color(0xFF9EA1A8)           // 弱化占位文字
val OneUiLightDivider = Color(0xFFE3E5EA)             // 极细分割线 / 边框
val OneUiLightAccent = Color(0xFF2C64E8)              // 三星标志性柔和蓝 (One UI Signature Blue)
val OneUiLightAccentSoft = Color(0xFFE8EFFF)          // 强调色浅底衬

// --- 深色主题 (Dark Mode) ---
val OneUiDarkBackground = Color(0xFF101114)           // 页面背景 (深炭黑，非生硬纯黑)
val OneUiDarkCard = Color(0xFF1C1D22)                 // 主卡片背景
val OneUiDarkCardSecondary = Color(0xFF25262C)        // 次级/辅助卡片背景
val OneUiDarkCardElevated = Color(0xFF2D2E36)         // 悬浮/交互卡片背景
val OneUiDarkTextPrimary = Color(0xFFF1F1F5)          // 主文字 (舒适暖白)
val OneUiDarkTextSecondary = Color(0xFFA7A9B2)        // 次级文字 (柔和灰)
val OneUiDarkTextMuted = Color(0xFF6B6D75)            // 弱化占位文字
val OneUiDarkDivider = Color(0xFF34353B)              // 极细分割线
val OneUiDarkAccent = Color(0xFF4C85FA)               // 适配深色模式的柔和高亮蓝
val OneUiDarkAccentSoft = Color(0xFF1E283D)           // 强调色深底衬

// --- 状态语义色彩 (Semantic Status Colors) ---
val OneUiStatusSuccess = Color(0xFF129E65)            // 成功绿
val OneUiStatusWarning = Color(0xFFF59E0B)            // 警示橙
val OneUiStatusError = Color(0xFFE53935)              // 错误红

// --- 兼容层（保持旧接口可无缝映射到 One UI）---
val DarkBackground = OneUiDarkBackground
val DarkSurface = OneUiDarkCard
val DarkSurfaceElevated = OneUiDarkCardSecondary

val BrandPrimary = OneUiLightAccent
val BrandSecondary = Color(0xFF00C9A7)
val FocusHighlight = OneUiLightAccent

val TextPrimary = OneUiDarkTextPrimary
val TextSecondary = OneUiDarkTextSecondary
val TextMuted = OneUiDarkTextMuted

val StatusError = OneUiStatusError
val StatusWarning = OneUiStatusWarning
val StatusSuccess = OneUiStatusSuccess

val LightBackground = OneUiLightBackground
val LightSurface = OneUiLightCard
val LightSurfaceElevated = OneUiLightCardSecondary

val TextPrimaryLight = OneUiLightTextPrimary
val TextSecondaryLight = OneUiLightTextSecondary
val TextMutedLight = OneUiLightTextMuted
