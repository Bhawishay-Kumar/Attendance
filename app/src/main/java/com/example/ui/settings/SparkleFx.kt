package com.example.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 * Native port of the "message-card.html" sparkle interaction:
 *  - Heart: blue → red bloom, 1 s heartbeat, expanding ring, 12 mini hearts sprayed upward.
 *  - Smile: closed happy eyes at rest; on tap opens its eyes, laughs, blushes, bounces, sends 7 mini smiles up.
 * Particles are drawn by one full-screen overlay (FxOverlay) so they can fly outside the card.
 */

// ───────────────────────── shapes (same path data as the HTML) ─────────────────────────

private val heartPath: Path by lazy {
    PathParser().parsePathString(
        "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
    ).toPath()
}
private val arcL: Path by lazy { PathParser().parsePathString("M12.5 21.5q4-5 8 0").toPath() }
private val arcR: Path by lazy { PathParser().parsePathString("M27.5 21.5q4-5 8 0").toPath() }
private val grinPath: Path by lazy { PathParser().parsePathString("M14.5 28.5q9.5 8.5 19 0").toPath() }
private val mouthPath: Path by lazy { PathParser().parsePathString("M12.5 27h23c0 8.5-5 14-11.5 14S12.5 35.5 12.5 27z").toPath() }
private val tonguePath: Path by lazy { PathParser().parsePathString("M18 37.5c2-3 10-3 12 0-3 2.2-9 2.2-12 0z").toPath() }
private val miniMouth: Path by lazy { PathParser().parsePathString("M14 28q10 9 20 0").toPath() }

private val faceBrush = Brush.radialGradient(
    0f to Color(0xFFFFEC9A), 0.6f to Color(0xFFFFC93F), 1f to Color(0xFFF5A21A),
    center = Offset(19.6f, 15.2f), radius = 34.3f
)
// Moving-gradient palettes (first colour == last colour so the loop is seamless)
private val heartFlowBlue = listOf(
    Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFF22D3EE)
)
private val heartFlowRed = listOf(
    Color(0xFFFF6B81), Color(0xFFE51C46), Color(0xFFFF9AA9), Color(0xFFFF3B5C), Color(0xFFFF6B81)
)

/** Diagonal repeating gradient; sliding [t] 0..1 moves it exactly one period (24 units) so it flows forever. */
private fun flowBrush(colors: List<Color>, t: Float): Brush {
    val o = 24f * t
    return Brush.linearGradient(colors, Offset(o, o), Offset(o + 24f, o + 24f), TileMode.Repeated)
}
private val eye = Color(0xFF5A3200)
private val brow = Color(0xFF6B3A00)
private val miniHeartColors = listOf(Color(0xFFFF3B5C), Color(0xFFFF6B81), Color(0xFFFF8FA3), Color(0xFFE51C46))

// ───────────────────────── particle host ─────────────────────────

enum class FxKind { HEART, SMILE, RING }

class FxParticle(
    val kind: FxKind, val x0: Float, val y0: Float, val dx: Float, val dy: Float,
    val rot: Float, val sizePx: Float, val durationMs: Int, val delayMs: Int, val color: Color
) { val prog = Animatable(0f) }

/** Holds live particles. [haptic] is called for taps (wired to the Settings haptic preference). */
class FxHost(val haptic: () -> Unit) {
    val particles = mutableStateListOf<FxParticle>()
    var motion: Boolean = true

    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    fun burst(kind: FxKind, center: Offset, count: Int, fromDeg: Float, toDeg: Float, density: Float) {
        if (!motion) return
        repeat(count) { i ->
            val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * (i + Random.nextFloat() * 0.6f) / count).toDouble())
            val d = rnd(40f, 100f) * density
            particles.add(
                FxParticle(
                    kind, center.x, center.y,
                    (cos(a) * d).toFloat(), (sin(a) * d).toFloat() - rnd(10f, 40f) * density,
                    rnd(-50f, 50f), rnd(9f, 19f) * density, rnd(900f, 1400f).toInt(), rnd(0f, 120f).toInt(),
                    miniHeartColors.random()
                )
            )
        }
    }

    fun ring(center: Offset, sizePx: Float) {
        if (!motion) return
        particles.add(FxParticle(FxKind.RING, center.x, center.y, 0f, 0f, 0f, sizePx, 700, 0, Color(0xFFFF3B5C)))
    }
}

val LocalFxHost = staticCompositionLocalOf<FxHost?> { null }

@Composable
fun ProvideFx(host: FxHost, content: @Composable () -> Unit) {
    host.motion = rememberMotionEnabled()
    CompositionLocalProvider(LocalFxHost provides host, content = content)
}

private val flyEase = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

@Composable
private fun FxDriver(p: FxParticle, host: FxHost) {
    LaunchedEffect(p) {
        delay(p.delayMs.toLong())
        p.prog.animateTo(1f, tween(p.durationMs, easing = flyEase))
        host.particles.remove(p)
    }
}

/** Transparent full-screen layer that draws all particles. Put it above the scrolling content. */
@Composable
fun FxOverlay(host: FxHost) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        host.particles.forEach { p -> key(p) { FxDriver(p, host) } }
        Canvas(Modifier.fillMaxSize()) {
            host.particles.forEach { p ->
                val f = p.prog.value
                if (f <= 0f && p.kind != FxKind.RING) return@forEach
                val cx0 = p.x0 - origin.x
                val cy0 = p.y0 - origin.y
                if (p.kind == FxKind.RING) {
                    val sc = 0.6f + 2.6f * f
                    drawCircle(
                        p.color.copy(alpha = 0.9f * (1f - f)), p.sizePx / 2f * sc, Offset(cx0, cy0),
                        style = Stroke(max(2f, 2f * density))
                    )
                    return@forEach
                }
                val px: Float; val py: Float; val sc: Float; val rot: Float; val a: Float
                if (f < 0.35f) {
                    val u = f / 0.35f
                    px = p.dx * 0.55f * u; py = p.dy * 0.55f * u; sc = 0.2f + 0.9f * u; rot = p.rot * 0.5f * u; a = u
                } else {
                    val u = (f - 0.35f) / 0.65f
                    px = p.dx * (0.55f + 0.45f * u)
                    py = p.dy * 0.55f + (p.dy - 30f * density - p.dy * 0.55f) * u
                    sc = 1.1f - 0.55f * u; rot = p.rot * (0.5f + 0.5f * u); a = 1f - u
                }
                val unit = p.sizePx / (if (p.kind == FxKind.HEART) 24f else 48f)
                withTransform({
                    translate(cx0 + px, cy0 + py)
                    rotate(rot, Offset.Zero)
                    scale(unit * sc, unit * sc, Offset.Zero)
                    val half = if (p.kind == FxKind.HEART) 12f else 24f
                    translate(-half, -half)
                }) {
                    if (p.kind == FxKind.HEART) drawPath(heartPath, p.color, alpha = a)
                    else drawMiniFace(a)
                }
            }
        }
    }
}

private fun DrawScope.drawMiniFace(alpha: Float) {
    drawCircle(faceBrush, 22f, Offset(24f, 24f), alpha = alpha)
    drawCircle(eye, 3f, Offset(16.5f, 20f), alpha = alpha)
    drawCircle(eye, 3f, Offset(31.5f, 20f), alpha = alpha)
    drawPath(miniMouth, brow, alpha = alpha, style = Stroke(3.2f, cap = StrokeCap.Round))
}

// ───────────────────────── keyframe helper ─────────────────────────

private fun kf(t: Float, vararg p: Pair<Float, Float>): Float {
    if (t <= p.first().first) return p.first().second
    for (i in 1 until p.size) {
        val (t1, v1) = p[i]
        if (t <= t1) {
            val (t0, v0) = p[i - 1]
            val u = if (t1 == t0) 1f else (t - t0) / (t1 - t0)
            return v0 + (v1 - v0) * u
        }
    }
    return p.last().second
}

// ───────────────────────── heart button ─────────────────────────

/** Resting heart = flowing blue/cyan/violet gradient; tap -> flowing red gradient with beat, ring and mini hearts. [white] gives the white-on-tile variant. */
@Composable
fun HeartButton(size: Dp, modifier: Modifier = Modifier, white: Boolean = false) {
    val fx = LocalFxHost.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(false) }
    val beat = remember { Animatable(1f) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(1f) }
    val bloom by animateFloatAsState(
        if (on) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "bloom"
    )
    val flow by rememberLoop(2600, "heartFlow")

    Box(
        modifier
            .padding(2.dp)
            .tapClick(pressedScale = 0.82f, role = androidx.compose.ui.semantics.Role.Switch) {
                on = !on
                if (on) {
                    scope.launch {
                        beat.snapTo(0.7f)
                        beat.animateTo(1f, keyframes {
                            durationMillis = 1000
                            0.7f at 0; 1.5f at 220; 0.95f at 400; 1.28f at 580; 1f at 800
                        })
                    }
                    fx?.ring(center, sizePx)
                    fx?.burst(FxKind.HEART, center, 12, -200f, 20f, density)
                    fx?.haptic?.invoke()
                }
            }
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                center = Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f)
                sizePx = c.size.width.toFloat()
            }
    ) {
        Canvas(Modifier.size(size).graphicsLayer { scaleX = beat.value; scaleY = beat.value }) {
            val s = this.size.minDimension / 24f
            withTransform({ scale(s, s, Offset.Zero) }) {
                val r = bloom.coerceAtLeast(0f)
                if (r > 0.02f) drawCircle(Color(0xFFFF3B5C).copy(alpha = 0.35f * r.coerceAtMost(1f)), 13f, Offset(12f, 12f))
                val blueA = (1f - bloom).coerceIn(0f, 1f)
                if (blueA > 0f) {
                    withTransform({ scale(1f - 0.4f * bloom.coerceIn(0f, 1f), 1f - 0.4f * bloom.coerceIn(0f, 1f), Offset(12f, 12f)) }) {
                        if (white) drawPath(heartPath, Color.White, alpha = blueA) else drawPath(heartPath, flowBrush(heartFlowBlue, flow), alpha = blueA)
                    }
                }
                if (r > 0f) {
                    withTransform({ scale(r, r, Offset(12f, 12f)) }) {
                        drawPath(heartPath, flowBrush(heartFlowRed, flow), alpha = (r * 4f).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}

// ───────────────────────── smile button ─────────────────────────

/** Closed happy eyes at rest; tap → eyes open, laughs, blushes, bounces and sends 7 mini smiles up. */
@Composable
fun SmileButton(size: Dp, modifier: Modifier = Modifier) {
    val fx = LocalFxHost.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val t = remember { Animatable(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .padding(2.dp)
            .tapClick(pressedScale = 0.82f) {
                scope.launch { t.snapTo(0f); t.animateTo(1f, tween(1500, easing = LinearEasing)) }
                fx?.burst(FxKind.SMILE, center, 7, -160f, -20f, density)
                fx?.haptic?.invoke()
            }
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                center = Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f)
            }
    ) {
        Canvas(
            Modifier.size(size).graphicsLayer {
                val v = t.value
                scaleX = kf(v, 0f to 1f, .12f to .82f, .28f to 1.28f, .42f to 1.2f, .56f to 1.2f, .70f to 1.12f, 1f to 1f)
                scaleY = kf(v, 0f to 1f, .12f to 1.12f, .28f to 1.28f, .42f to 1.2f, .56f to 1.2f, .70f to 1.12f, 1f to 1f)
                translationY = kf(v, 0f to 0f, .12f to 0f, .28f to -9f, .42f to -4f, .56f to 0f, 1f to 0f) * this.density
                rotationZ = kf(v, 0f to 0f, .12f to 0f, .28f to -8f, .42f to 8f, .56f to -6f, .70f to 5f, 1f to 0f)
                clip = false
            }
        ) {
            val v = t.value
            val arc = kf(v, 0f to 1f, .10f to 0f, .82f to 0f, 1f to 1f)
            val roundA = kf(v, 0f to 0f, .14f to 1f, .76f to 1f, .88f to 0f, 1f to 0f)
            val roundSy = kf(v, 0f to .2f, .14f to 1f, .40f to 1f, .48f to .15f, .56f to 1f, .76f to 1f, .88f to .2f, 1f to .2f)
            val grin = kf(v, 0f to 1f, .10f to 0f, .84f to 0f, 1f to 1f)
            val openA = kf(v, 0f to 0f, .12f to 1f, .84f to 1f, .92f to 0f, 1f to 0f)
            val openSy = kf(v, 0f to .4f, .12f to .6f, .28f to 1.1f, .38f to .7f, .48f to 1.1f, .58f to .7f, .68f to 1.05f, .84f to .6f, 1f to .4f)
            val cheekS = kf(v, 0f to 1f, .4f to 1.35f, .7f to 1.35f, 1f to 1f)
            val cheekA = kf(v, 0f to .55f, .4f to .85f, .7f to .85f, 1f to .55f)
            val s = this.size.minDimension / 48f
            withTransform({ scale(s, s, Offset.Zero) }) {
                drawCircle(faceBrush, 22f, Offset(24f, 24f))
                for (cx in listOf(11.5f, 36.5f)) {
                    withTransform({ scale(cheekS, cheekS, Offset(cx, 29f)) }) {
                        drawOval(Color(0xFFFF7A8A), Offset(cx - 4.6f, 29f - 3.2f), Size(9.2f, 6.4f), alpha = cheekA)
                    }
                }
                if (arc > 0f) {
                    drawPath(arcL, brow, alpha = arc, style = Stroke(3f, cap = StrokeCap.Round))
                    drawPath(arcR, brow, alpha = arc, style = Stroke(3f, cap = StrokeCap.Round))
                }
                if (roundA > 0f) withTransform({ scale(1f, roundSy, Offset(24f, 19.5f)) }) {
                    for (ex in listOf(17f, 31f)) {
                        drawOval(eye, Offset(ex - 3.2f, 20f - 4.2f), Size(6.4f, 8.4f), alpha = roundA)
                        drawCircle(Color.White, 1.2f, Offset(ex + 1f, 18.4f), alpha = roundA)
                    }
                }
                if (grin > 0f) drawPath(grinPath, brow, alpha = grin, style = Stroke(3f, cap = StrokeCap.Round))
                if (openA > 0f) withTransform({ scale(1f, openSy, Offset(24f, 34f)) }) {
                    drawPath(mouthPath, Color(0xFF7A1F2B), alpha = openA)
                    drawPath(tonguePath, Color(0xFFFF7B8F), alpha = openA)
                    drawRect(Color.White, Offset(13.5f, 27f), Size(21f, 3.5f), alpha = openA)
                }
            }
        }
    }
}
