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
    val cardBackground: Color,
    val cardSecondary: Color,
    val cardElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val accent: Color,
    val accentSoft: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean
)

val OneUiLightTokens = OneUiColors(
    background = OneUiLightBackground,
    cardBackground = OneUiLightCard,
    cardSecondary = OneUiLightCardSecondary,
    cardElevated = OneUiLightCardElevated,
    textPrimary = OneUiLightTextPrimary,
    textSecondary = OneUiLightTextSecondary,
    textMuted = OneUiLightTextMuted,
    divider = OneUiLightDivider,
    accent = OneUiLightAccent,
    accentSoft = OneUiLightAccentSoft,
    statusSuccess = OneUiStatusSuccess,
    statusWarning = OneUiStatusWarning,
    statusError = OneUiStatusError,
    isDark = false
)

val OneUiDarkTokens = OneUiColors(
    background = OneUiDarkBackground,
    cardBackground = OneUiDarkCard,
    cardSecondary = OneUiDarkCardSecondary,
    cardElevated = OneUiDarkCardElevated,
    textPrimary = OneUiDarkTextPrimary,
    textSecondary = OneUiDarkTextSecondary,
    textMuted = OneUiDarkTextMuted,
    divider = OneUiDarkDivider,
    accent = OneUiDarkAccent,
    accentSoft = OneUiDarkAccentSoft,
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
}

object OneUiShapes {
    val cardSmall = RoundedCornerShape(12.dp)
    val cardMedium = RoundedCornerShape(18.dp)
    val cardLarge = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
    val searchBar = RoundedCornerShape(24.dp)
    val dialog = RoundedCornerShape(26.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
