package com.livestream.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp

// =========================================================================
// Samsung One UI Design Tokens & Theme Specification
// =========================================================================

data class OneUiColors(
    val background: Color,
    val panelBackground: Color,
    val cardBackground: Color,
    val cardSecondary: Color,
    val cardElevated: Color,
    val surfacePill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val borderSubtle: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean
)

val OneUiLightTokens = OneUiColors(
    background = OneUiLightBackground,
    panelBackground = OneUiLightPanel,
    cardBackground = OneUiLightCard,
    cardSecondary = OneUiLightCardSecondary,
    cardElevated = OneUiLightCardElevated,
    surfacePill = OneUiLightPill,
    textPrimary = OneUiLightTextPrimary,
    textSecondary = OneUiLightTextSecondary,
    textMuted = OneUiLightTextMuted,
    divider = OneUiLightDivider,
    borderSubtle = OneUiLightBorderSubtle,
    accent = OneUiLightAccent,
    accentSoft = OneUiLightAccentSoft,
    danger = OneUiLightDanger,
    dangerSoft = OneUiLightDangerSoft,
    statusSuccess = OneUiStatusSuccess,
    statusWarning = OneUiStatusWarning,
    statusError = OneUiStatusError,
    isDark = false
)

val OneUiDarkTokens = OneUiColors(
    background = OneUiDarkBackground,
    panelBackground = OneUiDarkPanel,
    cardBackground = OneUiDarkCard,
    cardSecondary = OneUiDarkCardSecondary,
    cardElevated = OneUiDarkCardElevated,
    surfacePill = OneUiDarkPill,
    textPrimary = OneUiDarkTextPrimary,
    textSecondary = OneUiDarkTextSecondary,
    textMuted = OneUiDarkTextMuted,
    divider = OneUiDarkDivider,
    borderSubtle = OneUiDarkBorderSubtle,
    accent = OneUiDarkAccent,
    accentSoft = OneUiDarkAccentSoft,
    danger = OneUiDarkDanger,
    dangerSoft = OneUiDarkDangerSoft,
    statusSuccess = OneUiStatusSuccess,
    statusWarning = OneUiStatusWarning,
    statusError = OneUiStatusError,
    isDark = true
)

val LocalOneUiColors = staticCompositionLocalOf { OneUiDarkTokens }

object OneUiTheme {
    val colors: OneUiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUiColors.current

    val shapes = OneUiShapes
    val dimensions = OneUiDimensions
}

object OneUiShapes {
    val cardSmall = RoundedCornerShape(12.dp)
    val cardMedium = RoundedCornerShape(18.dp)
    val cardLarge = RoundedCornerShape(24.dp)
    val panel = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
    val searchBar = RoundedCornerShape(24.dp)
    val dialog = RoundedCornerShape(26.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object OneUiDimensions {
    val panelPlaylistWidth = 316.dp
    val panelSettingsWidth = 460.dp
    val panelSettingsPhoneWidth = 360.dp
    val panelExitConfirmWidth = 284.dp
    val hudChannelBarWidth = 440.dp
    val rowHeightChannel = 48.dp
    val rowHeightSetting = 52.dp
    val badgeChannelNumberWidth = 34.dp
}

private val DarkColorScheme = darkColorScheme(
    primary = OneUiDarkAccent,
    secondary = OneUiDarkAccent,
    background = OneUiDarkBackground,
    surface = OneUiDarkCard,
    onPrimary = OneUiDarkBackground,
    onSecondary = OneUiDarkTextPrimary,
    onBackground = OneUiDarkTextPrimary,
    onSurface = OneUiDarkTextPrimary,
    surfaceVariant = OneUiDarkCardSecondary,
    onSurfaceVariant = OneUiDarkTextSecondary,
    outline = OneUiDarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = OneUiLightAccent,
    secondary = OneUiLightAccent,
    background = OneUiLightBackground,
    surface = OneUiLightCard,
    onPrimary = Color.White,
    onSecondary = OneUiLightTextPrimary,
    onBackground = OneUiLightTextPrimary,
    onSurface = OneUiLightTextPrimary,
    surfaceVariant = OneUiLightCardSecondary,
    onSurfaceVariant = OneUiLightTextSecondary,
    outline = OneUiLightDivider
)

// 全局便捷颜色访问器（自动跟随当前 One UI 主题模式响应）
val AppBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.background

val AppSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.cardBackground

val AppSurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.cardSecondary

val AppTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.textPrimary

val AppTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.textSecondary

val AppTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.textMuted

val AppDivider: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.divider

val AppAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.accent

val AppAccentSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.accentSoft

val AppPanelBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.panelBackground

val AppSurfacePill: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.surfacePill

val AppBorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.borderSubtle

val AppDanger: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.danger

val AppDangerSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = OneUiTheme.colors.dangerSoft

@Composable
fun LiveStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val oneUiColors = if (darkTheme) OneUiDarkTokens else OneUiLightTokens
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalOneUiColors provides oneUiColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
