package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class GlassThemeTokens(
    val isDark: Boolean,
    val canvasStart: Color,
    val canvasEnd: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val specularHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val royalIndigo: Color = RoyalIndigo,
    val electricViolet: Color = ElectricViolet,
    val emeraldNeon: Color = EmeraldNeon,
    val amberGold: Color = AmberGold,
    val crimsonRose: Color = CrimsonRose,
    val electricTeal: Color = ElectricTeal
)

val LocalGlassTheme = staticCompositionLocalOf {
    GlassThemeTokens(
        isDark = true,
        canvasStart = DarkCanvasStart,
        canvasEnd = DarkCanvasEnd,
        cardSurface = DarkCardSurface,
        cardBorder = DarkCardBorder,
        specularHighlight = DarkSpecularHighlight,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        textMuted = DarkTextMuted
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = RoyalIndigo,
    secondary = ElectricViolet,
    tertiary = EmeraldNeon,
    background = DarkCanvasEnd,
    surface = DarkCardSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalIndigo,
    secondary = ElectricViolet,
    tertiary = EmeraldNeon,
    background = LightCanvasStart,
    surface = LightCardSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary
)

@Composable
fun BHTTAttendanceTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val glassTokens = if (darkTheme) {
        GlassThemeTokens(
            isDark = true,
            canvasStart = DarkCanvasStart,
            canvasEnd = DarkCanvasEnd,
            cardSurface = DarkCardSurface,
            cardBorder = DarkCardBorder,
            specularHighlight = DarkSpecularHighlight,
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textMuted = DarkTextMuted
        )
    } else {
        GlassThemeTokens(
            isDark = false,
            canvasStart = LightCanvasStart,
            canvasEnd = LightCanvasEnd,
            cardSurface = LightCardSurface,
            cardBorder = LightCardBorder,
            specularHighlight = Color(0xFFF5F6FF).copy(alpha = 0.6f),
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textMuted = LightTextMuted
        )
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalGlassTheme provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
