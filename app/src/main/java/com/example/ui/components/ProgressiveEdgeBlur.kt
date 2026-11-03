package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalGlassTheme

enum class BlurDirection { TOP, BOTTOM }

/**
 * Pure, neutral bottom edge fade (ZERO blue tint) that works with Haze
 * so scrolling content softly melts behind the floating GlassDock.
 */
@Composable
fun ProgressiveEdgeBlur(
    isDark: Boolean,
    direction: BlurDirection = BlurDirection.BOTTOM,
    height: Dp = 105.dp,
    modifier: Modifier = Modifier
) {
    // Matches the exact neutral app canvas—no blue or purple color cast
    val baseColor = if (isDark) Color(0xFF070B14) else if (direction == BlurDirection.BOTTOM) com.example.ui.theme.LightCanvasEnd else com.example.ui.theme.LightCanvasStart

    val colors = if (direction == BlurDirection.BOTTOM) {
        listOf(
            Color.Transparent,
            baseColor.copy(alpha = 0.45f),
            baseColor.copy(alpha = 0.85f)
        )
    } else {
        listOf(
            baseColor.copy(alpha = 0.85f),
            baseColor.copy(alpha = 0.45f),
            Color.Transparent
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(colors = colors))
    )
}

/**
 * Top edge neutral blur overlay.
 */
@Composable
fun TopEdgeBlur(
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    enabled: Boolean = true
) {
    if (!enabled) return
    val glass = LocalGlassTheme.current
    val baseColor = if (glass.isDark) Color(0xFF070B14) else com.example.ui.theme.LightCanvasStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.90f),
                        baseColor.copy(alpha = 0.60f),
                        baseColor.copy(alpha = 0.20f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Bottom edge neutral blur overlay helper.
 */
@Composable
fun BottomEdgeBlur(
    modifier: Modifier = Modifier,
    height: Dp = 105.dp,
    enabled: Boolean = true
) {
    if (!enabled) return
    val glass = LocalGlassTheme.current
    ProgressiveEdgeBlur(
        isDark = glass.isDark,
        direction = BlurDirection.BOTTOM,
        height = height,
        modifier = modifier
    )
}
