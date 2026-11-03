package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Star(
    val xRatio: Float,
    val yRatio: Float,
    val baseRadius: Float,
    val pulseSpeed: Float,
    val phase: Float,
    val color: Color
)

private val NEBULA_1_COLORS = listOf(Color(0x224F46E5), Color(0x107C3AED), Color.Transparent)
private val NEBULA_2_COLORS = listOf(Color(0x1A06B6D4), Color.Transparent)

@Composable
fun StarfieldCanvas(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    val stars = remember {
        val rnd = Random(42)
        List(85) {
            Star(
                xRatio = rnd.nextFloat(),
                yRatio = rnd.nextFloat(),
                baseRadius = rnd.nextFloat() * 1.6f + 0.6f,
                pulseSpeed = rnd.nextFloat() * 1.5f + 0.8f,
                phase = rnd.nextFloat() * (2f * PI.toFloat()),
                color = if (rnd.nextFloat() > 0.35f) Color.White else Color(0xFFC7D2FE)
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "starfield")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Shooting star cycle every 8 seconds
    val shootingStarProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shootingStar"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Layer 1: Nebula background glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = NEBULA_1_COLORS,
                center = Offset(w * 0.75f, h * 0.25f),
                radius = w * 0.65f
            ),
            radius = w * 0.65f,
            center = Offset(w * 0.75f, h * 0.25f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = NEBULA_2_COLORS,
                center = Offset(w * 0.2f, h * 0.7f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.2f, h * 0.7f)
        )

        // Layer 2: Twinkling Micro-Stars
        stars.forEach { star ->
            val alpha = (0.25f + 0.65f * (sin(time * star.pulseSpeed + star.phase) * 0.5f + 0.5f)).coerceIn(0.1f, 1f)
            val radius = star.baseRadius * (0.85f + 0.3f * alpha)
            drawCircle(
                color = star.color.copy(alpha = alpha),
                radius = radius,
                center = Offset(star.xRatio * w, star.yRatio * h)
            )
        }

        // Layer 3: Diagonal Falling / Shooting Star Streak (streaks softly between progress 0.15 and 0.35)
        if (shootingStarProgress in 0.15f..0.35f) {
            val shootFactor = (shootingStarProgress - 0.15f) / 0.20f
            val startX = w * 0.85f - (shootFactor * w * 0.65f)
            val startY = h * 0.10f + (shootFactor * h * 0.35f)
            val length = 75f
            val endX = startX - length * 0.8f
            val endY = startY + length * 0.5f

            val streakAlpha = when {
                shootFactor < 0.2f -> shootFactor / 0.2f
                shootFactor > 0.8f -> (1f - shootFactor) / 0.2f
                else -> 1f
            }

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0xFFFEF08A).copy(alpha = streakAlpha), Color.White.copy(alpha = streakAlpha)),
                    start = Offset(endX, endY),
                    end = Offset(startX, startY)
                ),
                start = Offset(endX, endY),
                end = Offset(startX, startY),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }
    }
}
