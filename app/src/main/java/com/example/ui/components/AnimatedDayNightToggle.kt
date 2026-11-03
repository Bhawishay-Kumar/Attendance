package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.ui.settings.rememberAppHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/*
 * AnimatedDayNightToggle  (liquid-glass design, ported from day-night-toggle.html)
 * ------------------------------------------------------------------------------
 * ONE shared Day/Night control for the whole app. Everything is derived from a single
 * progress value p (0 = Day, 1 = Night) that is driven by a spring (stiffness 380, damping
 * ratio ~0.69 - the same physics as the HTML reference), so thumb, track, icons, stars and
 * clouds always stay in sync.
 *
 * All sizes of the reference are defined for a 70px tall toggle; here everything is scaled by
 * k = height / 70, so `height` is the only size you ever need to change (width = 2 x height).
 *
 * The whole control is ONE draw pass; p is read only inside the draw lambda, so nothing
 * recomposes while it animates. Nothing runs when idle (no infinite animations).
 */

// ---- motion (matches the HTML: k = 380, c = 27  ->  zeta = 27 / (2 * sqrt(380)) ~= 0.69) ----
private val ThumbSpring = spring<Float>(dampingRatio = 0.693f, stiffness = 380f)
private val SwellSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 300f)   // .body scale 1.14, overshoots like cubic-bezier(.25,1.8,.5,1)
private val PressSpring = spring<Float>(dampingRatio = 0.50f, stiffness = 700f)   // .tg:active scale .98
private val ShineEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)                     // CSS ease-out

private const val SunRaysPath =
    "M12 2.5v2.4M12 19.1v2.4M2.5 12h2.4M19.1 12h2.4M5.3 5.3l1.7 1.7M17 17l1.7 1.7M5.3 18.7L7 17M17 7l1.7-1.7"
private const val MoonPath = "M21 12.8A8.5 8.5 0 1 1 11.2 3a6.8 6.8 0 0 0 9.8 9.8z"

private val InkDay = Color(0xFF8D9FC8)
private val InkNight = Color(0xFF6C84B8)
private val SunBlue = Color(0xFF1F6BFF)
private val StarColor = Color(0xFFCFE0FF)

private fun rgb(r: Int, g: Int, b: Int, a: Float = 1f): Color = Color(r, g, b).copy(alpha = a)

/** CSS-style linear-gradient(angle) for a box at (0,0) of size w x h. */
private fun cssLinear(angleDeg: Float, w: Float, h: Float, vararg stops: Pair<Float, Color>): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val len = abs(w * dx) + abs(h * dy)
    val cx = w / 2f
    val cy = h / 2f
    return Brush.linearGradient(
        *stops,
        start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
        end = Offset(cx + dx * len / 2f, cy + dy * len / 2f)
    )
}

/**
 * Cheap soft shadow (CSS box-shadow equivalent): n stacked rounded rects whose sizes sweep
 * across the blur range, so the alpha falls off smoothly toward the edge.
 */
private fun DrawScope.softShadow(
    w: Float, h: Float, corner: Float,
    offY: Float, blur: Float, spread: Float,
    color: Color, alpha: Float
) {
    if (alpha < 0.004f) return
    val n = 8
    val a = 1f - (1f - alpha.coerceIn(0f, 0.98f)).pow(1f / n)
    for (i in 0 until n) {
        val s = spread + blur * ((i + 0.5f) / n - 0.5f)
        val ww = w + 2f * s
        val hh = h + 2f * s
        if (ww <= 0f || hh <= 0f) continue
        drawRoundRect(
            color = color.copy(alpha = a),
            topLeft = Offset(-s, offY - s),
            size = Size(ww, hh),
            cornerRadius = CornerRadius((corner + s).coerceAtLeast(0f))
        )
    }
}

/** Draws a 24x24 icon centred at (cx, cy) with the given on-screen size, rotation and scale. */
private fun DrawScope.icon24(
    cx: Float, cy: Float, size: Float, deg: Float, scl: Float,
    block: DrawScope.() -> Unit
) {
    withTransform({
        translate(cx, cy)
        rotate(deg, Offset.Zero)
        scale(scl, scl, Offset.Zero)
        scale(size / 24f, size / 24f, Offset.Zero)
        translate(-12f, -12f)
    }) { block() }
}

/**
 * @param isDark   current theme (true = Night). The toggle follows this value, so it also animates
 *                 when the theme is changed from somewhere else.
 * @param onToggle called with the NEW state only when the user really changes it (tap, or a drag /
 *                 flick that ends past the middle). A drag that snaps back never calls it.
 * @param height   visual height (default 32dp, slightly smaller than before); width is always 2 x height (reference ratio 140 x 70).
 *                 The touch target stays at least 48dp tall.
 */
@Composable
fun AnimatedDayNightToggle(
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHapticFeedback()

    // "Remove animations" accessibility setting -> snap instead of animate.
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    val hPx = with(density) { height.toPx() }
    val padPx = hPx * 5f / 70f
    val dPx = hPx - 2f * padPx
    val travelPx = hPx                       // = width - 2*pad - thumb  (== height for a 2:1 pill)
    val thumbRadiusPx = dPx / 2f
    val grabSlopPx = with(density) { 6.dp.toPx() }

    val progress = remember { Animatable(if (isDark) 1f else 0f) }
    val shine = remember { Animatable(1f) }                 // 0..1 sweep, 1 = finished/hidden
    var dragging by remember { mutableStateOf(false) }
    var dragFromThumb by remember { mutableStateOf(false) }
    var pressedAny by remember { mutableStateOf(false) }
    var pressedOnThumb by remember { mutableStateOf(false) }
    val lastToggleAt = remember { mutableLongStateOf(0L) }
    val interaction = remember { MutableInteractionSource() }

    val latestIsDark by rememberUpdatedState(isDark)
    val latestOnToggle by rememberUpdatedState(onToggle)

    fun thumbCenterX(): Float = padPx + thumbRadiusPx + travelPx * progress.value

    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    pressedAny = true
                    pressedOnThumb = abs(i.pressPosition.x - thumbCenterX()) <= thumbRadiusPx + grabSlopPx
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressedAny = false
                    pressedOnThumb = false
                }
            }
        }
    }

    suspend fun settleTo(target: Float) {
        if (reduceMotion) progress.snapTo(target) else progress.animateTo(target, ThumbSpring)
    }

    // Follow external state (also covers the theme being changed from another screen).
    LaunchedEffect(isDark) {
        if (!dragging) settleTo(if (isDark) 1f else 0f)
    }

    fun notifyChange(newIsDark: Boolean) {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        latestOnToggle(newIsDark)
    }

    val draggableState = rememberDraggableState { delta ->
        if (dragging) {
            val lo = -3f / travelPx
            val hi = 1f + 3f / travelPx
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                progress.snapTo((progress.value + delta / travelPx).coerceIn(lo, hi))
            }
        }
    }

    // .thumb.press .body { scale: 1.14 }  - held while pressing the thumb, dragging or travelling
    val swell by animateFloatAsState(
        targetValue = if (pressedOnThumb || dragging || progress.isRunning) 1.14f else 1f,
        animationSpec = SwellSpring,
        label = "toggleSwell"
    )
    // .tg:active { transform: scale(.98) }
    val trackPress by animateFloatAsState(
        targetValue = if (pressedAny) 0.98f else 1f,
        animationSpec = PressSpring,
        label = "toggleTrackPress"
    )

    val widthDp = height * 2
    val touchHeight = if (height > 48.dp) height else 48.dp

    Box(
        modifier = modifier
            .size(widthDp, touchHeight)
            .semantics {
                contentDescription = "Day and night theme"
                stateDescription = if (isDark) "Night mode" else "Day mode"
            }
            .toggleable(
                value = isDark,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null
            ) { requested ->
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastToggleAt.longValue > 150L) {          // swallow accidental double-taps
                    lastToggleAt.longValue = now
                    scope.launch {                                      // liquid-glass shine sweep
                        shine.snapTo(0f)
                        if (reduceMotion) shine.snapTo(1f)
                        else shine.animateTo(1f, tween(700, easing = ShineEasing))
                    }
                    scope.launch { settleTo(if (requested) 1f else 0f) }
                    notifyChange(requested)
                }
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { start ->
                    // only a drag that starts on the thumb moves it (same as the reference)
                    dragFromThumb = abs(start.x - thumbCenterX()) <= thumbRadiusPx + grabSlopPx
                    dragging = dragFromThumb
                },
                onDragStopped = { velocityPx ->
                    val wasDragging = dragging
                    dragging = false
                    dragFromThumb = false
                    if (wasDragging) {
                        val v = (velocityPx / travelPx).coerceIn(-20f, 20f)     // p per second
                        val target = progress.value + v * 0.12f > 0.5f          // a flick carries on
                        scope.launch {
                            if (target != latestIsDark) notifyChange(target)
                            if (reduceMotion) progress.snapTo(if (target) 1f else 0f)
                            else progress.animateTo(if (target) 1f else 0f, ThumbSpring, v)
                        }
                    }
                }
            )
            .testTag("animated_day_night_toggle"),
        contentAlignment = Alignment.Center
    ) {
        val sunRays = remember { PathParser().parsePathString(SunRaysPath).toPath() }
        val moon = remember { PathParser().parsePathString(MoonPath).toPath() }

        Box(
            modifier = Modifier
                .size(widthDp, height)
                .graphicsLayer {
                    scaleX = trackPress
                    scaleY = trackPress
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val k = h / 70f
                    val pad = 5f * k
                    val d = h - 2f * pad
                    val r = d / 2f
                    val travel = h

                    val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(h / 2f))) }
                    val disc = Path().apply { addOval(Rect(0f, 0f, d, d)) }

                    // ---- static brushes (built once per size) ----
                    val dayBase = cssLinear(135f, w, h, 0f to Color.White.copy(alpha = 0.78f), 1f to rgb(224, 235, 255, 0.55f))
                    val nightBase = cssLinear(135f, w, h, 0f to rgb(26, 48, 118, 0.62f), 1f to rgb(8, 18, 56, 0.78f))
                    val dayInset = Brush.verticalGradient(listOf(rgb(120, 150, 230, 0.16f), Color.Transparent), 0f, 9f * k)
                    val nightInset = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent), 0f, 10f * k)
                    val dayEdge = cssLinear(120f, w, h, 0f to Color.White, 0.5f to rgb(140, 195, 255, 0.95f), 1f to Color.White)
                    val nightEdge = cssLinear(120f, w, h, 0f to rgb(45, 125, 255, 0.95f), 0.4f to rgb(90, 205, 255, 0.5f), 1f to rgb(125, 85, 255, 0.85f))
                    // top sheen: left/right 12%, top 2px, height 42%
                    val sheenW = w * 0.76f
                    val sheenRx = sheenW * 0.70f
                    val sheenRy = h * 0.42f
                    val sheenCx = w / 2f
                    val sheenTop = 2f * k
                    val sheen = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(sheenCx, sheenTop),
                        radius = sheenRx * 0.75f
                    )

                    val dayTint = cssLinear(145f, d, d, 0f to Color.White, 1f to Color(0xFFE8F0FF))
                    val dayTintBottom = Brush.verticalGradient(
                        listOf(Color.Transparent, rgb(110, 140, 230, 0.22f)), d - 10f * k, d
                    )
                    val nightTint = cssLinear(145f, d, d, 0f to Color(0xFF5A9BFF), 1f to Color(0xFF2563EB))
                    val nightTintTop = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, 11f * k
                    )
                    val nightTintBottom = Brush.verticalGradient(
                        listOf(Color.Transparent, rgb(10, 30, 150, 0.35f)), d - 12f * k, d
                    )
                    val lensHighlight = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.42f), Color.Transparent), 0f, d * 0.5f
                    )
                    val shineW = d * 0.45f
                    val shineBrush = Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.60f), 1f to Color.Transparent,
                        startX = 0f, endX = shineW
                    )
                    val skew = tan(Math.toRadians(-18.0)).toFloat()

                    onDrawBehind {
                        val raw = progress.value
                        val p = raw.coerceIn(0f, 1f)
                        val vel = abs(progress.velocity)
                        val swellNow = swell
                        val shineNow = shine.value
                        val ink = lerp(InkDay, InkNight, p)

                        // ============ TRACK ============
                        // outer drop shadows (never visible through the glass)
                        clipPath(pill, ClipOp.Difference) {
                            softShadow(w, h, h / 2f, 16f * k, 30f * k, -16f * k, rgb(70, 110, 230), 0.55f * (1f - p))
                            softShadow(w, h, h / 2f, 18f * k, 34f * k, -14f * k, rgb(10, 40, 210), 0.85f * p)
                        }

                        clipPath(pill) {
                            drawRect(dayBase, alpha = 1f - p)
                            drawRect(dayInset, size = Size(w, 9f * k), alpha = 1f - p)
                            drawRect(nightBase, alpha = p)
                            drawRect(nightInset, size = Size(w, 10f * k), alpha = p)

                            // glass sheen
                            withTransform({ scale(1f, sheenRy / sheenRx, Offset(sheenCx, sheenTop)) }) {
                                drawCircle(sheen, radius = sheenRx * 0.75f, center = Offset(sheenCx, sheenTop))
                            }

                            // stars (opacity = p)
                            if (p > 0.01f) {
                                val big = 3f * k
                                val small = 2f * k
                                drawCircle(StarColor, big / 2f, Offset(w * 0.36f + big / 2f, h * 0.26f + big / 2f), alpha = p)
                                drawCircle(StarColor, small / 2f, Offset(w * 0.47f + small / 2f, h * 0.62f + small / 2f), alpha = p)
                                drawCircle(StarColor, small / 2f, Offset(w * 0.25f + small / 2f, h * 0.70f + small / 2f), alpha = p)
                                drawCircle(StarColor, small / 2f, Offset(w * 0.53f + small / 2f, h * 0.30f + small / 2f), alpha = p)
                            }

                            // cloud (opacity = 1 - p, drifts 14px to the right)
                            val ca = 1f - p
                            if (ca > 0.01f) {
                                val cl = w * 0.44f + p * 14f * k
                                val ct = h * 0.56f
                                val cw = w * 0.26f
                                val ch = h * 0.22f
                                fun cloudPill(x: Float, y: Float, ww: Float, hh: Float, a: Float) = drawRoundRect(
                                    Color.White.copy(alpha = a * ca), Offset(x, y), Size(ww, hh), CornerRadius(hh / 2f)
                                )
                                cloudPill(cl + 2f * k - 8f * k, ct + 2f * k - 6f * k, cw - 4f * k, ch - 4f * k, 0.80f)
                                cloudPill(cl + 3f * k + 10f * k, ct + 3f * k - 4f * k, cw - 6f * k, ch - 6f * k, 0.75f)
                                cloudPill(cl, ct, cw, ch, 0.88f)
                            }

                            // slot icons: sun hint on the left (night), moon hint on the right (day)
                            icon24(h / 2f, h / 2f, 0.42f * h, (1f - p) * -70f, 0.5f + p * 0.5f) {
                                drawCircle(ink, 4.4f, Offset(12f, 12f), alpha = p)
                                drawPath(sunRays, ink, p, Stroke(2.2f, cap = StrokeCap.Round))
                            }
                            icon24(w - h / 2f, h / 2f, 0.42f * h, p * 70f, 1f - p * 0.5f) {
                                drawPath(moon, ink, 1f - p)
                            }
                        }

                        // gradient edge (1.5px, inside the pill)
                        val e = 1.5f * k
                        drawRoundRect(dayEdge, Offset(e / 2f, e / 2f), Size(w - e, h - e), CornerRadius(h / 2f - e / 2f), alpha = 1f - p, style = Stroke(e))
                        drawRoundRect(nightEdge, Offset(e / 2f, e / 2f), Size(w - e, h - e), CornerRadius(h / 2f - e / 2f), alpha = p, style = Stroke(e))

                        // ============ THUMB ============
                        val pos = raw.coerceIn(-0.06f, 1.06f)
                        val cx = pad + r + travel * pos
                        val cy = pad + r
                        val sx = 1f + min(0.30f, vel / 11f)                    // velocity stretch
                        val sy = 1f - (sx - 1f) * 0.55f                          // ...and squash
                        withTransform({
                            translate(cx, cy)
                            scale(swellNow * sx, swellNow * sy, Offset.Zero)
                            translate(-r, -r)
                        }) {
                            clipPath(disc, ClipOp.Difference) {
                                softShadow(d, d, r, 10f * k, 20f * k, -6f * k, rgb(60, 100, 220), 0.55f * (1f - p))
                                softShadow(d, d, r, 0f, 26f * k, 0f, rgb(50, 120, 255), 0.75f * p)        // night glow
                                softShadow(d, d, r, 10f * k, 20f * k, -6f * k, rgb(20, 60, 220), 0.85f * p)
                            }

                            clipPath(disc) {
                                // day glass
                                drawRect(dayTint, alpha = 1f - p)
                                drawRect(dayTintBottom, Offset(0f, d - 10f * k), Size(d, 10f * k), alpha = 1f - p)
                                drawCircle(Color.White.copy(alpha = 0.9f), r - 0.5f * k, Offset(r, r), alpha = 1f - p, style = Stroke(1f * k))
                                // night glass
                                drawRect(nightTint, alpha = p)
                                drawRect(nightTintTop, size = Size(d, 11f * k), alpha = p)
                                drawRect(nightTintBottom, Offset(0f, d - 12f * k), Size(d, 12f * k), alpha = p)
                                drawCircle(rgb(135, 205, 255, 0.65f), r - 0.75f * k, Offset(r, r), alpha = p, style = Stroke(1.5f * k))
                                // top highlight
                                drawRect(lensHighlight, size = Size(d, d * 0.5f))

                                // diagonal shine sweep (left -60% -> 130%, skewX(-18deg))
                                if (shineNow in 0.001f..0.999f) {
                                    val x0 = d * (-0.6f + 1.9f * shineNow)
                                    val cv = drawContext.canvas
                                    cv.save()
                                    cv.translate(r, r)
                                    cv.skew(skew, 0f)
                                    cv.translate(-r, -r)
                                    translate(x0, 0f) { drawRect(shineBrush, size = Size(shineW, d)) }
                                    cv.restore()
                                }

                                // sun icon (blue) fades out / rotates away, moon (white, glowing) fades in
                                val sunA = (1f - 2f * p).coerceIn(0f, 1f)
                                if (sunA > 0f) {
                                    icon24(r, r, 0.5f * d, p * 160f, 1f - p * 0.7f) {
                                        drawCircle(SunBlue, 4.4f, Offset(12f, 12f), alpha = sunA)
                                        drawPath(sunRays, SunBlue, sunA, Stroke(2.2f, cap = StrokeCap.Round))
                                    }
                                }
                                val moonA = (2f * p - 1f).coerceIn(0f, 1f)
                                if (moonA > 0f) {
                                    drawCircle(
                                        Brush.radialGradient(
                                            listOf(Color.White.copy(alpha = 0.45f * moonA), Color.Transparent),
                                            center = Offset(r, r), radius = 0.42f * d
                                        ),
                                        radius = 0.42f * d, center = Offset(r, r)
                                    )
                                    icon24(r, r, 0.5f * d, (1f - p) * -160f, 0.3f + p * 0.7f) {
                                        drawPath(moon, Color.White, moonA)
                                    }
                                }
                            }
                        }
                    }
                }
        )
    }
}
