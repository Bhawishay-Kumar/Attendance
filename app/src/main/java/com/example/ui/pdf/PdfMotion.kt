package com.example.ui.pdf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.reports.RText
import com.example.ui.reports.RptPalette
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/*
 * Motion toolkit for the PDF Report screens. Everything is UI-only, light-weight (values are
 * read in draw/graphicsLayer blocks, so nothing recomposes per frame) and honours the system
 * "reduced motion" setting: when [reduced] is true, items appear instantly and loops stay still.
 */

/** Staggered "rise in": fade + slide up + tiny scale. Use increasing [index] for a cascade. */
@Composable
fun Modifier.staggerIn(index: Int, reduced: Boolean, key: Any? = Unit): Modifier {
    var shown by remember(key) { mutableStateOf(reduced) }
    LaunchedEffect(key) {
        if (!reduced) {
            delay(index.coerceIn(0, 10) * 70L)
            shown = true
        }
    }
    val a by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(if (reduced) 0 else 480, easing = FastOutSlowInEasing),
        label = "staggerIn"
    )
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 34.dp.toPx()
        val s = 0.97f + 0.03f * a
        scaleX = s
        scaleY = s
    }
}

/** Gentle up/down drift for icons and badges. */
@Composable
fun Modifier.floating(reduced: Boolean, amplitude: Dp = 3.dp, periodMs: Int = 2600): Modifier {
    if (reduced) return this
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatV"
    )
    return this.graphicsLayer { translationY = v * amplitude.toPx() }
}

/** Moving highlight over a flat placeholder (skeleton while a page is rendering). */
@Composable
fun Modifier.shimmer(base: Color, highlight: Color, reduced: Boolean): Modifier {
    if (reduced) return this.background(base)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -0.6f, 1.6f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX"
    )
    return this.drawBehind {
        drawRect(base)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(w * x - w * 0.35f, 0f),
                end = Offset(w * x + w * 0.35f, size.height * 0.4f)
            )
        )
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(path, color)
}

/** Glowing "smart" orb shown while the report is being created: sweep ring, pulsing core, orbiting sparkles. */
@Composable
fun AiOrb(p: RptPalette, reduced: Boolean, modifier: Modifier = Modifier.size(160.dp)) {
    val t = rememberInfiniteTransition(label = "orb")
    val rotAnim by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "rot")
    val pulseAnim by t.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val orbitAnim by t.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "orbit")
    Canvas(modifier) {
        val rot = if (reduced) 0f else rotAnim
        val pulse = if (reduced) 1f else pulseAnim
        val orbit = if (reduced) 0f else orbitAnim
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(p.violet.copy(alpha = 0.38f), Color.Transparent), c, r), r, c)
        rotate(rot, c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, p.cyan, p.violet, p.indigo, Color.Transparent), c),
                r * 0.78f, c, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        val core = r * 0.48f * pulse
        drawCircle(Brush.radialGradient(listOf(p.cyan, p.indigo, p.violet), c, core), core, c)
        drawCircle(Color.White.copy(alpha = 0.25f), core * 0.45f, Offset(c.x - core * 0.25f, c.y - core * 0.3f))
        for (i in 0 until 3) {
            val ang = Math.toRadians((orbit + i * 120f).toDouble())
            val pos = Offset(c.x + cos(ang).toFloat() * r * 0.92f, c.y + sin(ang).toFloat() * r * 0.92f)
            sparkle(pos, (5f + i * 1.5f).dp.toPx(), Color.White.copy(alpha = 0.92f))
        }
    }
}

/** Cross-fading status line that cycles through [steps] (decorative; not a progress claim). */
@Composable
fun CyclingStatus(p: RptPalette, steps: List<String>, reduced: Boolean) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(steps) {
        while (true) {
            delay(1300)
            i = (i + 1) % steps.size
        }
    }
    AnimatedContent(
        targetState = i,
        transitionSpec = {
            if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (fadeIn(tween(350, 80)) + slideInVertically(tween(350)) { it / 2 }) togetherWith
                (fadeOut(tween(180)) + slideOutVertically(tween(250)) { -it / 2 })
        },
        label = "status"
    ) { idx -> RText(steps[idx], 15f, p.text, weight = FontWeight.SemiBold, display = true) }
}

/** One-shot "report ready" tick: expanding ripple, popping disc and a check that draws itself. */
@Composable
fun SuccessBurst(p: RptPalette, reduced: Boolean, modifier: Modifier = Modifier.size(132.dp)) {
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val pr = progress.value
        val c = center
        val r = size.minDimension / 2f
        drawCircle(p.emerald.copy(alpha = (1f - pr) * 0.55f), r * (0.45f + 0.55f * pr), c, style = Stroke(3.dp.toPx()))
        val disc = (pr / 0.5f).coerceAtMost(1f)
        drawCircle(Brush.linearGradient(listOf(p.emerald, p.cyan)), r * 0.62f * disc, c)
        val cp = ((pr - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (cp > 0f) {
            val w = r
            val path = Path().apply {
                moveTo(c.x - w * 0.24f, c.y + w * 0.02f)
                lineTo(c.x - w * 0.07f, c.y + w * 0.20f)
                lineTo(c.x + w * 0.27f, c.y - w * 0.17f)
            }
            val m = PathMeasure()
            m.setPath(path, false)
            val dst = Path()
            m.getSegment(0f, m.length * cp, dst, true)
            drawPath(dst, Color.White, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
