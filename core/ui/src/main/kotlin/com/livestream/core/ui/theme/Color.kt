package com.livestream.core.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Samsung One UI 7/8 Design Tokens (Light & Dark Theme)
// =========================================================================

// --- 浅色主题 (Light Mode - Modern One UI) ---
val OneUiLightBackground = Color(0xFFF4F6FA)          // 页面背景（柔和云灰，长时间观看不刺眼）
val OneUiLightPanel = Color(0xEBFFFFFF)               // 浮动面板背景 (92% 透光毛玻璃白)
val OneUiLightCard = Color(0xFFFFFFFF)                // 主卡片背景 (纯净高对比白)
val OneUiLightCardSecondary = Color(0xFFEEF1F7)       // 次级/辅助卡片背景与输入框底色
val OneUiLightCardElevated = Color(0xFFE6EAF3)        // 悬浮/交互按压卡片背景
val OneUiLightPill = Color(0xFFE5E9F2)                // 胶囊控件背景
val OneUiLightTextPrimary = Color(0xFF17191F)         // 主文字 (深邃墨黑)
val OneUiLightTextSecondary = Color(0xFF565B66)       // 次级文字 (中性冷灰，高可读性)
val OneUiLightTextMuted = Color(0xFF838894)           // 弱化占位文字
val OneUiLightDivider = Color(0xFFE4E7EF)             // 极细分割线
val OneUiLightBorderSubtle = Color(0x1617191F)        // 面板与卡片极细边框描边
val OneUiLightAccent = Color(0xFF0A7AFF)              // 现代亮丽品牌蓝
val OneUiLightAccentSoft = Color(0x1F0A7AFF)          // 选中态柔和蓝底衬
val OneUiLightDanger = Color(0xFFE03127)              // 危险警示红
val OneUiLightDangerSoft = Color(0x1FE03127)          // 危险柔和底色

// --- 深色主题 (Dark Mode - Modern One UI) ---
val OneUiDarkBackground = Color(0xFF0F1115)           // 页面背景 (深邃曜石黑)
val OneUiDarkPanel = Color(0xD9151821)                // 浮动面板背景 (85% 半透明深空毛玻璃)
val OneUiDarkCard = Color(0xCC1D202A)                 // 主卡片背景 (80% 半透明卡片层)
val OneUiDarkCardSecondary = Color(0xCC262A36)        // 次级/辅助卡片背景与输入框底色
val OneUiDarkCardElevated = Color(0xD92F3442)         // 悬浮/交互卡片背景
val OneUiDarkPill = Color(0xCC2D313F)                 // 胶囊控件背景
val OneUiDarkTextPrimary = Color(0xFFF3F4F8)          // 主文字 (柔和暖白，暗光不刺眼)
val OneUiDarkTextSecondary = Color(0xFFA4A8B5)        // 次级文字 (舒适银灰)
val OneUiDarkTextMuted = Color(0xFF757986)            // 弱化占位文字
val OneUiDarkDivider = Color(0xFF2B2E39)              // 极细分割线
val OneUiDarkBorderSubtle = Color(0x22FFFFFF)         // 面板与卡片极细高光描边
val OneUiDarkAccent = Color(0xFF3D9BFF)               // 适配深色模式的高清亮蓝
val OneUiDarkAccentSoft = Color(0x2E3D9BFF)           // 选中态柔和蓝底衬
val OneUiDarkDanger = Color(0xFFFF5A4F)               // 危险警示红
val OneUiDarkDangerSoft = Color(0x29FF5A4F)           // 危险柔和底色

// --- 状态语义色彩 (Semantic Status Colors) ---
val OneUiStatusSuccess = Color(0xFF22A858)            // 成功绿
val OneUiStatusWarning = Color(0xFFF59E0B)            // 警示橙
val OneUiStatusError = Color(0xFFE03127)              // 错误红

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
