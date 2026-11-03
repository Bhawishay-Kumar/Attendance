package com.example.ui.countdown

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val spin: Float,
    val colorIndex: Int,
    val round: Boolean
)

/**
 * One-shot confetti burst from the middle of its bounds. Particles are generated once from a fixed
 * seed, driven by a single Animatable and drawn on one Canvas, so it costs nothing after ~1.2 s.
 */
@Composable
fun ConfettiBurst(
    palette: List<Color>,
    modifier: Modifier = Modifier,
    originY: Float = 0.42f,
    onFinished: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        val rnd = Random(42)
        List(34) {
            Particle(
                angle = rnd.nextFloat() * 6.2831855f,
                speed = 0.35f + rnd.nextFloat() * 0.65f,
                size = 3f + rnd.nextFloat() * 4f,
                spin = (rnd.nextFloat() - 0.5f) * 720f,
                colorIndex = rnd.nextInt(palette.size.coerceAtLeast(1)),
                round = rnd.nextBoolean()
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1250, easing = LinearOutSlowInEasing))
        onFinished()
    }
    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f || palette.isEmpty()) return@Canvas
        val center = Offset(size.width / 2f, size.height * originY)
        val reach = size.minDimension * 0.95f
        val gravity = size.minDimension * 0.55f
        particles.forEach { p ->
            val dist = reach * p.speed * t
            val x = center.x + cos(p.angle) * dist
            val y = center.y + sin(p.angle) * dist + gravity * t * t
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            val color = palette[p.colorIndex % palette.size].copy(alpha = alpha)
            val s = p.size.dp.toPx()
            if (p.round) {
                drawCircle(color, radius = s / 2f, center = Offset(x, y))
            } else {
                rotate(degrees = p.spin * t, pivot = Offset(x, y)) {
                    drawRect(color, topLeft = Offset(x - s / 2f, y - s), size = Size(s, s * 2f))
                }
            }
        }
    }
}
