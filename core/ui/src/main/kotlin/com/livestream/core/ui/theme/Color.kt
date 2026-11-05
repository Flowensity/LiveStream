package com.livestream.core.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Samsung One UI 7/8 Design Tokens (Light & Dark Theme)
// =========================================================================

// --- 浅色主题 (Light Mode - TeleflowTv / One UI) ---
val OneUiLightBackground = Color(0xFFF5F6FA)          // 页面背景
val OneUiLightPanel = Color(0xD8F5F6FA)               // 浮动面板背景 (85% 半透明毛玻璃)
val OneUiLightCard = Color(0xF2FFFFFF)                // 主卡片背景 (95% 柔和底色)
val OneUiLightCardSecondary = Color(0xE8EEF1F7)       // 次级/辅助卡片背景与输入框底色
val OneUiLightCardElevated = Color(0xECF0F3F9)        // 悬浮/交互卡片背景
val OneUiLightPill = Color(0xE0E6EAF2)                // 胶囊控件背景
val OneUiLightTextPrimary = Color(0xFF191B20)         // 主文字 (高对比墨黑)
val OneUiLightTextSecondary = Color(0xFF5C606A)       // 次级文字 (舒适冷灰，远距离高可读性)
val OneUiLightTextMuted = Color(0xFF74777F)           // 弱化占位文字
val OneUiLightDivider = Color(0xFFE3E5EA)             // 极细分割线
val OneUiLightBorderSubtle = Color(0x1A191B20)        // 面板与卡片极细边框描边
val OneUiLightAccent = Color(0xFF0381FE)              // 三星官方标志性 Samsung Blue
val OneUiLightAccentSoft = Color(0x240381FE)          // 选中态柔和蓝底衬
val OneUiLightDanger = Color(0xFFDB332A)              // 危险警示红
val OneUiLightDangerSoft = Color(0x22DB332A)          // 危险柔和底色

// --- 深色主题 (Dark Mode - TeleflowTv / One UI) ---
val OneUiDarkBackground = Color(0xFF101114)           // 页面背景 (深炭黑)
val OneUiDarkPanel = Color(0xCC12141A)                // 浮动面板背景 (80% 半透明透光毛玻璃)
val OneUiDarkCard = Color(0xB81C1D24)                 // 主卡片背景 (72% 半透明)
val OneUiDarkCardSecondary = Color(0xB8252630)        // 次级/辅助卡片背景
val OneUiDarkCardElevated = Color(0xC02D2E3A)         // 悬浮/交互卡片背景
val OneUiDarkPill = Color(0xB02F313D)                 // 胶囊控件背景
val OneUiDarkTextPrimary = Color(0xFFF1F1F5)          // 主文字 (舒适暖白)
val OneUiDarkTextSecondary = Color(0xFFA7A9B2)        // 次级文字 (柔和灰)
val OneUiDarkTextMuted = Color(0xFF7A7D87)            // 弱化占位文字
val OneUiDarkDivider = Color(0xFF34353B)              // 极细分割线
val OneUiDarkBorderSubtle = Color(0x26FFFFFF)         // 面板与卡片极细边框描边
val OneUiDarkAccent = Color(0xFF3898FF)               // 适配深色模式的三星亮蓝
val OneUiDarkAccentSoft = Color(0x333898FF)           // 选中态柔和蓝底衬
val OneUiDarkDanger = Color(0xFFFF5449)               // 危险警示红
val OneUiDarkDangerSoft = Color(0x2EFF5449)           // 危险柔和底色

// --- 状态语义色彩 (Semantic Status Colors) ---
val OneUiStatusSuccess = Color(0xFF1E9E52)            // 成功绿
val OneUiStatusWarning = Color(0xFFF59E0B)            // 警示橙
val OneUiStatusError = Color(0xFFDB332A)              // 错误红

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
