@file:Suppress("FunctionName")

// TODO: change to your real package
package com.example.attendance.ui.upgrade

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/* ═══════════════════════════════════════════════════════════════
 *  1. TOKENS, STATUS, MATH
 * ═══════════════════════════════════════════════════════════════ */

object Aurora {
    val Bg = Color(0xFF080816)
    val Violet = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF22D3EE)
    val Mint = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Rose = Color(0xFFFB7185)
    val Pink = Color(0xFFF472B6)
    val TextHi = Color(0xFFF8FAFC)
    val TextLo = Color(0xFF9CA3C7)
}

enum class Status(val label: String, val emoji: String, val color: Color) {
    Safe("Safe zone", "😎", Aurora.Mint),
    Edge("On the edge", "😬", Aurora.Amber),
    Danger("Danger zone", "😱", Aurora.Rose)
}

fun statusOf(pct: Float, required: Float): Status = when {
    pct >= required + 3f -> Status.Safe
    pct >= required -> Status.Edge
    else -> Status.Danger
}

fun statusColor(pct: Float, required: Float): Color = statusOf(pct, required).color

fun pctOf(attended: Int, total: Int): Float = if (total <= 0) 0f else attended * 100f / total

/** Consecutive classes to attend to reach [required]%. -1 = impossible. */
fun classesToReach(attended: Int, total: Int, required: Float): Int {
    if (pctOf(attended, total) >= required) return 0
    if (required >= 100f) return -1
    val x = (required * total - 100f * attended) / (100f - required)
    return ceil(x - 1e-4f).toInt().coerceAtLeast(0)
}

/** Classes you can skip and still stay >= [required]%. */
fun safeBunks(attended: Int, total: Int, required: Float): Int {
    if (required <= 0f) return 99
    return max(0, floor(attended * 100f / required - total + 1e-4f).toInt())
}

/* ═══════════════════════════════════════════════════════════════
 *  2. MOTION TOOLKIT (modifiers)
 * ═══════════════════════════════════════════════════════════════ */

@Composable
fun Txt(
    text: String,
    size: Int,
    color: Color = Aurora.TextHi,
    weight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
    align: TextAlign? = null
) = Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align)

@Composable
fun GradientTitle(text: String, size: Int = 30) = Text(
    text,
    style = TextStyle(
        brush = Brush.horizontalGradient(listOf(Color.White, Aurora.Cyan, Aurora.Violet)),
        fontSize = size.sp,
        fontWeight = FontWeight.Black
    )
)

/** Springy press-down + optional haptic tick. */
fun Modifier.pressScale(haptic: Boolean = false, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hf = LocalHapticFeedback.current
    val s by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null) {
            if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Staggered fade + rise + scale-in. */
fun Modifier.entrance(index: Int, stepMs: Long = 80L, from: Dp = 36.dp): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMs)
        a.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
    }
    this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * from.toPx()
        val s = 0.94f + 0.06f * a.value
        scaleX = s; scaleY = s
    }
}

/** Gentle endless up/down bob. */
fun Modifier.floating(amp: Dp = 6.dp, ms: Int = 2400): Modifier = composed {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y"
    )
    this.graphicsLayer { translationY = y * amp.toPx() }
}

suspend fun Animatable<Float, AnimationVector1D>.shake() {
    repeat(3) { animateTo(12f, tween(45)); animateTo(-12f, tween(45)) }
    animateTo(0f, tween(60))
}

/* ═══════════════════════════════════════════════════════════════
 *  3. BACKGROUND, GLASS, BUTTONS
 * ═══════════════════════════════════════════════════════════════ */

private class Star(val x: Float, val y: Float, val r: Float, val k: Int, val phase: Float, val drift: Int)

/** Drifting aurora blobs + twinkling, slowly rising star dust. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val stars = remember {
        List(42) { Star(Random.nextFloat(), Random.nextFloat(), 1f + Random.nextFloat() * 2f, 6 + Random.nextInt(8), Random.nextFloat(), 1 + Random.nextInt(2)) }
    }
    val t = rememberInfiniteTransition(label = "aurora")
    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "p")
    Box(modifier.fillMaxSize().background(Aurora.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val pv = p.value
            val a = pv * 2f * PI.toFloat() * 2f
            fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                val center = Offset(cx, cy)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.34f), Color.Transparent), center, r), r, center)
            }
            blob(Aurora.Violet, size.width * (0.2f + 0.15f * cos(a)), size.height * (0.12f + 0.06f * sin(a)), size.width)
            blob(Aurora.Cyan, size.width * (0.85f + 0.1f * sin(a)), size.height * (0.45f + 0.08f * cos(a)), size.width * 0.8f)
            blob(Aurora.Pink, size.width * (0.3f + 0.12f * cos(a + 2f)), size.height * (0.92f + 0.05f * sin(a)), size.width * 0.9f)
            stars.forEach { s ->
                val tw = 0.2f + 0.7f * abs(sin(2f * PI.toFloat() * (pv * s.k + s.phase)))
                val yy = (((s.y - pv * s.drift) % 1f) + 1f) % 1f
                drawCircle(Color.White.copy(alpha = tw * 0.8f), s.r, Offset(s.x * size.width, yy * size.height))
            }
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Aurora.Violet,
    animatedBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val spin = rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "a")
    Column(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(0.11f), Color.White.copy(0.04f))))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(0.55f), Color.White.copy(0.05f))), shape)
            .drawWithContent {
                drawContent()
                if (animatedBorder) {
                    val r = 28.dp.toPx()
                    val bw = 2.5f.dp.toPx()
                    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                    val inner = Path().apply {
                        addRoundRect(RoundRect(bw, bw, size.width - bw, size.height - bw, CornerRadius(r - bw)))
                    }
                    clipPath(outer) {
                        clipPath(inner, ClipOp.Difference) {
                            rotate(spin.value, center) {
                                drawCircle(
                                    Brush.sweepGradient(
                                        listOf(accent, Color.Transparent, Aurora.Cyan, Color.Transparent, accent), center
                                    ),
                                    radius = max(size.width, size.height)
                                )
                            }
                        }
                    }
                }
            }
            .padding(20.dp),
        content = content
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sh = rememberInfiniteTransition(label = "btn")
        .animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sh")
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Aurora.Violet, Aurora.Cyan)))
            .drawWithContent {
                drawContent()
                val x = size.width * sh.value
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(0.28f), Color.Transparent),
                        Offset(x, 0f), Offset(x + 160f, size.height)
                    )
                )
            }
            .pressScale(haptic = true, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) { Txt(text, 16, weight = FontWeight.Bold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(20.dp))
            .pressScale(haptic = true, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) { Txt(text, 16, weight = FontWeight.SemiBold) }
}

@Composable
fun ScreenHeader(title: String, subtitle: String, emoji: String, scroll: ScrollState) {
    Row(
        Modifier.fillMaxWidth().graphicsLayer {
            translationY = scroll.value * 0.35f
            alpha = (1f - scroll.value / 380f).coerceIn(0f, 1f)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            GradientTitle(title)
            Txt(subtitle, 14, Aurora.TextLo)
        }
        Txt(emoji, 40, modifier = Modifier.floating())
    }
}

@Composable
fun AnimatedPercent(value: Float, color: Color, size: Int = 44) {
    val v by animateFloatAsState(value, tween(900, easing = FastOutSlowInEasing), label = "pct")
    Txt("%.1f%%".format(v), size, color, FontWeight.Black)
}

/* ═══════════════════════════════════════════════════════════════
 *  4. SIGNATURE COMPONENTS — ring, confetti, slider
 * ═══════════════════════════════════════════════════════════════ */

/** Ring with tick marks, gradient arc, orbiting spark and a white target tick. */
@Composable
fun AttendanceRing(
    percent: Float,
    required: Float,
    modifier: Modifier = Modifier.size(200.dp),
    caption: String? = null,
    showTicks: Boolean = true
) {
    val sweep by animateFloatAsState(
        percent.coerceIn(0f, 100f) / 100f * 300f,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow),
        label = "sweep"
    )
    val color by animateColorAsState(statusColor(percent, required), tween(600), label = "ringColor")
    val pulse = rememberInfiniteTransition(label = "ringPulse")
        .animateFloat(0.6f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val stroke = size.minDimension * 0.085f
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = Size(size.width - stroke, size.height - stroke)
            val c = center
            val r = sz.width / 2

            if (showTicks) {
                val tr = r - stroke * 1.5f
                for (i in 0 until 41) {
                    val deg = 300f * i / 40f
                    val a = Math.toRadians((120f + deg).toDouble())
                    val lit = deg <= sweep
                    drawLine(
                        color.copy(alpha = if (lit) 0.75f else 0.12f),
                        Offset(c.x + cos(a).toFloat() * tr, c.y + sin(a).toFloat() * tr),
                        Offset(c.x + cos(a).toFloat() * (tr - stroke * 0.55f), c.y + sin(a).toFloat() * (tr - stroke * 0.55f)),
                        strokeWidth = 3f, cap = StrokeCap.Round
                    )
                }
            }

            rotate(120f, c) {
                drawArc(Color.White.copy(0.08f), 0f, 300f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color.copy(0.22f), 0f, sweep, false, tl, sz, style = Stroke(stroke * 1.8f, cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(0f to color.copy(0.4f), 0.83f to color, 1f to color, center = c),
                    0f, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }

            if (sweep > 2f) {
                val a = Math.toRadians((120f + sweep).toDouble())
                val p = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
                drawCircle(Color.White.copy(0.35f * pulse.value), stroke * 1.3f * pulse.value, p)
                drawCircle(Color.White, stroke * 0.42f, p)
            }

            val ta = Math.toRadians((120f + required / 100f * 300f).toDouble())
            val dx = cos(ta).toFloat()
            val dy = sin(ta).toFloat()
            drawLine(
                Color.White,
                Offset(c.x + dx * (r - stroke), c.y + dy * (r - stroke)),
                Offset(c.x + dx * (r + stroke), c.y + dy * (r + stroke)),
                strokeWidth = 4f, cap = StrokeCap.Round
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedPercent(percent, color, 40)
            if (caption != null) Txt(caption, 13, Aurora.TextLo)
        }
    }
}

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

/** Fire by incrementing [trigger]. Draws a confetti burst from the centre. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    val palette = listOf(Aurora.Mint, Aurora.Cyan, Aurora.Violet, Aurora.Pink, Aurora.Amber)
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            particles = List(56) {
                Particle(
                    -(PI.toFloat()) * (0.05f + Random.nextFloat() * 0.9f),
                    260f + Random.nextFloat() * 520f,
                    7f + Random.nextFloat() * 9f,
                    palette.random(),
                    Random.nextFloat() * 720f - 360f
                )
            }
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val t = progress.value
        if (t < 1f) {
            particles.forEach { p ->
                val x = center.x + cos(p.angle) * p.speed * t
                val y = center.y + sin(p.angle) * p.speed * t + 650f * t * t
                rotate(p.spin * t, Offset(x, y)) {
                    drawRect(p.color.copy(alpha = 1f - t), Offset(x - p.size / 2, y - p.size / 3), Size(p.size, p.size * 0.6f))
                }
            }
        }
    }
}

/** Draggable / tappable slider with glow, growing thumb and tick haptics. */
@Composable
fun GlowSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    val hf = LocalHapticFeedback.current
    val cb by rememberUpdatedState(onChange)
    val last = remember { floatArrayOf(value) }
    val thumb by animateFloatAsState(if (dragging) 15f else 11f, spring(Spring.DampingRatioMediumBouncy), label = "thumb")
    val frac by animateFloatAsState(
        ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f),
        spring(stiffness = Spring.StiffnessMedium), label = "frac"
    )

    fun setFrom(x: Float, w: Float) {
        val f = (x / w).coerceIn(0f, 1f)
        val v = (range.start + f * (range.endInclusive - range.start)).roundToInt().toFloat()
        if (v != last[0]) {
            last[0] = v
            hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            cb(v)
        }
    }

    Canvas(
        modifier.fillMaxWidth().height(44.dp)
            .pointerInput(Unit) { detectTapGestures { setFrom(it.x, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; setFrom(it.x, size.width.toFloat()) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { ch, _ -> ch.consume(); setFrom(ch.position.x, size.width.toFloat()) }
                )
            }
    ) {
        val pad = 16.dp.toPx()
        val w = size.width - pad * 2
        val y = size.height / 2
        val x = pad + w * frac
        drawLine(Color.White.copy(0.1f), Offset(pad, y), Offset(pad + w, y), 10f, StrokeCap.Round)
        drawLine(Brush.horizontalGradient(listOf(color.copy(0.5f), color), pad, x), Offset(pad, y), Offset(x, y), 10f, StrokeCap.Round)
        drawCircle(color.copy(0.28f), (thumb + 9f).dp.toPx() / 2.2f, Offset(x, y))
        drawCircle(Color.White, thumb.dp.toPx() / 2f + 2f, Offset(x, y))
        drawCircle(color, thumb.dp.toPx() / 3f, Offset(x, y))
    }
}

/* ═══════════════════════════════════════════════════════════════
 *  5. FEATURE 1 — WHAT-IF PREDICTOR  (hero feature)
 * ═══════════════════════════════════════════════════════════════ */

@Composable
private fun RoundBtn(symbol: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(color.copy(0.18f))
            .border(1.dp, color.copy(0.55f), CircleShape).pressScale(haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Txt(symbol, 24, color, FontWeight.Bold) }
}

@Composable
private fun Stepper(title: String, value: Int, color: Color, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(title, 13, Aurora.TextLo)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("−", color) { if (value > 0) onChange(value - 1) }
            AnimatedContent(
                value,
                modifier = Modifier.width(58.dp),
                transitionSpec = {
                    if (targetState > initialState)
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    else
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                },
                label = "stepper"
            ) { Box(Modifier.fillMaxWidth(), Alignment.Center) { Txt("$it", 32, color, FontWeight.Black) } }
            RoundBtn("+", color) { if (value < 60) onChange(value + 1) }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Aurora.Violet else Color.White.copy(0.08f), tween(300), label = "chip")
    val s by animateFloatAsState(if (selected) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "chipScale")
    Box(
        Modifier.graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(50)).background(bg).pressScale(haptic = true, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Txt(label, 14, weight = FontWeight.SemiBold) }
}

@Composable
private fun InsightTile(modifier: Modifier, emoji: String, title: String, value: Int, color: Color) {
    GlassCard(modifier, accent = color) {
        Txt(emoji, 26, modifier = Modifier.floating(3.dp))
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            value,
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
            label = "insight"
        ) { Txt(if (it < 0) "—" else "$it", 36, color, FontWeight.Black) }
        Txt(title, 12, Aurora.TextLo)
    }
}

/** Smooth, draggable projection chart: attend-all (green) vs miss-all (red). */
@Composable
private fun ProjectionChart(attended: Int, total: Int, required: Float, modifier: Modifier = Modifier, horizon: Int = 12) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(attended, total) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
    }
    var frac by remember { mutableStateOf<Float?>(null) }
    val up = remember(attended, total) { (0..horizon).map { pctOf(attended + it, total + it) } }
    val down = remember(attended, total) { (0..horizon).map { pctOf(attended, total + it) } }
    val idx = ((frac ?: 1f) * horizon).roundToInt().coerceIn(0, horizon)

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(if (frac == null) "Drag to explore" else "After $idx classes", 12, Aurora.TextLo)
            if (frac != null) {
                Txt("✔ %.1f%%".format(up[idx]), 12, Aurora.Mint, FontWeight.Bold)
                Txt("✖ %.1f%%".format(down[idx]), 12, Aurora.Rose, FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { frac = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { frac = null },
                    onDragCancel = { frac = null },
                    onHorizontalDrag = { ch, _ -> ch.consume(); frac = (ch.position.x / size.width).coerceIn(0f, 1f) }
                )
            }
        ) {
            val lo = (min(down.minOf { it }, required) - 5f).coerceAtLeast(0f)
            val hi = (max(up.maxOf { it }, required) + 3f).coerceAtMost(100f)
            val span = max(hi - lo, 1f)
            fun yOf(v: Float) = size.height - (v - lo) / span * size.height
            fun xOf(i: Int) = size.width * i / horizon
            fun smooth(vs: List<Float>) = Path().apply {
                moveTo(xOf(0), yOf(vs[0]))
                for (i in 1..horizon) {
                    val px = xOf(i - 1); val py = yOf(vs[i - 1])
                    val x = xOf(i); val y = yOf(vs[i])
                    val cx = (px + x) / 2
                    cubicTo(cx, py, cx, y, x, y)
                }
            }

            drawLine(
                Color.White.copy(0.55f), Offset(0f, yOf(required)), Offset(size.width, yOf(required)),
                strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
            val upPath = smooth(up)
            val fill = Path().apply {
                addPath(upPath)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            clipRect(right = size.width * progress.value) {
                drawPath(fill, Brush.verticalGradient(listOf(Aurora.Mint.copy(0.30f), Color.Transparent)))
                drawPath(upPath, Aurora.Mint, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(smooth(down), Aurora.Rose, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            if (frac == null && progress.value < 1f) {
                val fi = progress.value * horizon
                val i0 = fi.toInt().coerceAtMost(horizon - 1)
                val v = up[i0] + (up[i0 + 1] - up[i0]) * (fi - i0)
                val o = Offset(size.width * progress.value, yOf(v))
                drawCircle(Aurora.Mint.copy(0.3f), 18f, o)
                drawCircle(Color.White, 7f, o)
            }
            if (frac != null) {
                val sx = xOf(idx)
                drawLine(Color.White.copy(0.45f), Offset(sx, 0f), Offset(sx, size.height), 2f)
                drawCircle(Aurora.Mint, 9f, Offset(sx, yOf(up[idx])))
                drawCircle(Aurora.Rose, 9f, Offset(sx, yOf(down[idx])))
            }
        }
    }
}

@Composable
fun WhatIfPredictorScreen(
    subject: String,
    attended: Int,
    total: Int,
    required: Float,
    modifier: Modifier = Modifier
) {
    var attend by remember { mutableIntStateOf(0) }
    var miss by remember { mutableIntStateOf(0) }
    var target by remember { mutableFloatStateOf(required) }
    val scroll = rememberScrollState()
    val hf = LocalHapticFeedback.current

    val current = pctOf(attended, total)
    val newAtt = attended + attend
    val newTot = total + attend + miss
    val predicted = pctOf(newAtt, newTot)
    val delta = predicted - current
    val status = statusOf(predicted, target)
    val ringColor by animateColorAsState(status.color, tween(600), label = "hero")
    val deltaColor by animateColorAsState(if (delta >= 0f) Aurora.Mint else Aurora.Rose, label = "delta")

    val glowScale by rememberInfiniteTransition(label = "glow").animateFloat(
        0.92f, 1.1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )
    val shake = remember { Animatable(0f) }
    var confetti by remember { mutableIntStateOf(0) }
    var prev by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (status != prev) {
            prev = status
            when (status) {
                Status.Safe -> { confetti++; hf.performHapticFeedback(HapticFeedbackType.LongPress) }
                Status.Danger -> { hf.performHapticFeedback(HapticFeedbackType.LongPress); shake.shake() }
                Status.Edge -> {}
            }
        }
    }

    val need = classesToReach(newAtt, newTot, target)
    val sub = when (status) {
        Status.Safe -> "Comfortably above your ${target.roundToInt()}% target"
        Status.Edge -> "Just above target — one slip could hurt"
        Status.Danger -> if (need < 0) "Target is out of reach" else "Attend $need in a row to recover"
    }
    val presets = listOf("Attend 5" to (5 to 0), "Miss 3" to (0 to 3), "Mixed" to (3 to 2), "Reset" to (0 to 0))

    Column(
        modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(Modifier.entrance(0)) {
            ScreenHeader("What-If Predictor", "$subject  ·  $attended/$total classes", "🔮", scroll)
        }

        // Hero
        GlassCard(Modifier.fillMaxWidth().entrance(1), accent = ringColor, animatedBorder = true) {
            Box(Modifier.fillMaxWidth().height(260.dp), Alignment.Center) {
                Box(
                    Modifier.size(260.dp).graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                        .background(Brush.radialGradient(listOf(ringColor.copy(0.30f), Color.Transparent)), CircleShape)
                )
                AttendanceRing(
                    predicted, target,
                    Modifier.size(230.dp).graphicsLayer { translationX = shake.value }, "predicted"
                )
                ConfettiBurst(confetti, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    status,
                    transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                    label = "mood"
                ) { Txt(it.emoji, 44, modifier = Modifier.floating(4.dp)) }
                Spacer(Modifier.width(12.dp))
                AnimatedContent(
                    status to sub,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { (fadeIn(tween(350)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150)) },
                    label = "verdict"
                ) { (s, text) ->
                    Column {
                        Txt(s.label, 20, s.color, FontWeight.ExtraBold)
                        Txt(text, 13, Aurora.TextLo)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Txt("Now %.1f%%".format(current), 12, Aurora.TextLo)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(deltaColor.copy(0.18f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Txt("${if (delta >= 0) "▲ +" else "▼ "}%.1f%%".format(delta), 14, deltaColor, FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).entrance(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { (name, am) ->
                PresetChip(name, selected = attend == am.first && miss == am.second) {
                    attend = am.first; miss = am.second
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth().entrance(3), accent = Aurora.Cyan) {
            Row(Modifier.fillMaxWidth()) {
                Stepper("I will ATTEND", attend, Aurora.Mint, Modifier.weight(1f)) { attend = it }
                Stepper("I will MISS", miss, Aurora.Rose, Modifier.weight(1f)) { miss = it }
            }
        }

        GlassCard(Modifier.fillMaxWidth().entrance(4), accent = Aurora.Amber) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("🎯 Target attendance", 15, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Txt("${target.roundToInt()}%", 22, Aurora.Amber, FontWeight.Black)
            }
            GlowSlider(target, { target = it }, 50f..95f, Aurora.Amber)
        }

        GlassCard(Modifier.fillMaxWidth().entrance(5), accent = Aurora.Violet) {
            Txt("Next 12 classes", 16, weight = FontWeight.Bold)
            Row {
                Txt("● attend all  ", 12, Aurora.Mint); Txt("● miss all  ", 12, Aurora.Rose); Txt("- - target", 12, Aurora.TextLo)
            }
            Spacer(Modifier.height(10.dp))
            ProjectionChart(newAtt, newTot, target)
        }

        Row(Modifier.fillMaxWidth().entrance(6), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightTile(Modifier.weight(1f), "🎯", "classes to reach target", need, Aurora.Cyan)
            InsightTile(Modifier.weight(1f), "🏖️", "classes you can skip", safeBunks(newAtt, newTot, target), Aurora.Mint)
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
 *  6. FEATURE 2 — BUNK PLANNER
 * ═══════════════════════════════════════════════════════════════ */

@Composable
private fun DayChip(label: String, number: Int, skipped: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val base by animateColorAsState(if (skipped) Aurora.Rose.copy(0.9f) else Aurora.Mint.copy(0.16f), tween(300), label = "dayBg")
    val rot by animateFloatAsState(if (skipped) -6f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "dayRot")
    val ripple = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(skipped) {
        if (first) first = false else {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(550, easing = LinearOutSlowInEasing))
        }
    }
    Column(
        modifier
            .graphicsLayer { rotationZ = rot }
            .clip(RoundedCornerShape(18.dp))
            .background(base)
            .drawWithContent {
                drawContent()
                val r = ripple.value
                if (r < 1f) drawCircle(Color.White.copy(alpha = (1f - r) * 0.45f), size.maxDimension * r, center)
            }
            .pressScale(haptic = true, onClick = onToggle)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Txt(label, 12, Aurora.TextLo.copy(alpha = 0.95f), FontWeight.SemiBold)
        Txt("$number", 16, weight = FontWeight.Black)
        AnimatedContent(
            skipped,
            transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "dayIcon"
        ) { Txt(if (it) "🏖️" else "📚", 20) }
    }
}

@Composable
private fun BudgetSegments(n: Int, budget: Int, used: Int) {
    Row(Modifier.fillMaxWidth().height(20.dp), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
        repeat(n) { i ->
            val isUsed = i < used
            val within = i < budget
            val c by animateColorAsState(
                when {
                    isUsed && within -> Aurora.Mint
                    isUsed -> Aurora.Rose
                    within -> Aurora.Mint.copy(0.22f)
                    else -> Color.White.copy(0.07f)
                },
                tween(300), label = "seg"
            )
            val h by animateDpAsState(if (isUsed) 20.dp else 10.dp, spring(Spring.DampingRatioMediumBouncy), label = "segH")
            Box(Modifier.weight(1f).height(h).clip(RoundedCornerShape(50)).background(c))
        }
    }
}

@Composable
fun BunkPlannerScreen(
    subject: String,
    attended: Int,
    total: Int,
    required: Float,
    modifier: Modifier = Modifier,
    upcoming: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
) {
    val n = upcoming.size
    val skipped = remember { mutableStateListOf<Boolean>().apply { repeat(n) { add(false) } } }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val hf = LocalHapticFeedback.current

    val skipCount = skipped.count { it }
    val result = pctOf(attended + (n - skipCount), total + n)
    val budget = ((0..n).count { pctOf(attended + n - it, total + n) >= required } - 1).coerceAtLeast(0)
    val over = skipCount > budget || result < required
    val accent by animateColorAsState(if (over) Aurora.Rose else Aurora.Mint, tween(500), label = "accent")

    val shake = remember { Animatable(0f) }
    LaunchedEffect(skipCount) {
        if (over && skipCount > 0) {
            hf.performHapticFeedback(HapticFeedbackType.LongPress)
            shake.shake()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(Modifier.entrance(0)) {
            ScreenHeader("Bunk Planner", "$subject · tap the days you want to skip", "🏖️", scroll)
        }

        GlassCard(
            Modifier.fillMaxWidth().entrance(1).graphicsLayer { translationX = shake.value },
            accent = accent, animatedBorder = true
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Result after plan", 13, Aurora.TextLo)
                    AnimatedPercent(result, statusColor(result, required), 42)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Txt("Skips used ", 13, Aurora.TextLo)
                        AnimatedContent(
                            skipCount,
                            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                            label = "skipCount"
                        ) { Txt("$it", 13, accent, FontWeight.Black) }
                        Txt(" / $budget safe", 13, Aurora.TextLo)
                    }
                }
                AttendanceRing(result, required, Modifier.size(110.dp), showTicks = false)
            }
            Spacer(Modifier.height(16.dp))
            BudgetSegments(n, budget, skipCount)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                over,
                transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() },
                label = "planMsg"
            ) {
                Txt(
                    if (it) "⚠️ This plan drops you below ${required.roundToInt()}%"
                    else "✅ You stay above ${required.roundToInt()}% — enjoy",
                    14, if (it) Aurora.Rose else Aurora.Mint, FontWeight.SemiBold
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth().entrance(2), accent = Aurora.Cyan) {
            Txt("Next $n classes", 16, weight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            upcoming.withIndex().toList().chunked(4).forEachIndexed { rowIdx, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (i, label) ->
                        DayChip(label, i + 1, skipped[i], Modifier.weight(1f).entrance(rowIdx * 4 + (i % 4), 35L, 18.dp)) {
                            skipped[i] = !skipped[i]
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().entrance(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("✨ Auto-plan max skips", Modifier.weight(1f)) {
                scope.launch {
                    for (i in 0 until n) skipped[i] = false
                    for (i in 0 until min(budget, n)) { skipped[i] = true; delay(70) }
                }
            }
            GhostButton("Clear") {
                scope.launch { for (i in n - 1 downTo 0) { if (skipped[i]) { skipped[i] = false; delay(25) } } }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
 *  7. FEATURE 3 — PDF REPORT
 * ═══════════════════════════════════════════════════════════════ */

enum class ReportState { Idle, Generating, Done }

@Composable
fun AnimatedCheck(color: Color, modifier: Modifier = Modifier.size(72.dp)) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        drawCircle(color.copy(0.16f), size.minDimension / 2 * (0.7f + 0.3f * p.value))
        drawArc(color, -90f, 360f * p.value, false, Offset(4f, 4f), Size(size.width - 8f, size.height - 8f), style = Stroke(5f, cap = StrokeCap.Round))
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.45f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.34f)
        }
        val pm = PathMeasure()
        pm.setPath(path, false)
        val seg = Path()
        pm.getSegment(0f, pm.length * p.value, seg, true)
        drawPath(seg, color, style = Stroke(size.width * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun FillBar(frac: Float, widthFrac: Float, color: Color) {
    val f by animateFloatAsState(frac, tween(350), label = "fill")
    Box(Modifier.fillMaxWidth(widthFrac).height(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCBD5E1))) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** Floating A4 sheet. Bars fill with progress, a scan line sweeps, back pages fan out on Done. */
@Composable
private fun ReportSheetStack(state: ReportState, progress: Float) {
    val fan by animateFloatAsState(
        if (state == ReportState.Done) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "fan"
    )
    val t = rememberInfiniteTransition(label = "sheet")
    val bob by t.animateFloat(-8f, 8f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val tilt by t.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    val shimmer by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "shimmer")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "scan")
    val accent by animateColorAsState(if (state == ReportState.Done) Aurora.Mint else Aurora.Violet, tween(600), label = "acc")
    val ringFrac by animateFloatAsState(
        when (state) { ReportState.Idle -> 0.25f; ReportState.Generating -> max(0.1f, progress); ReportState.Done -> 1f },
        tween(500), label = "ringFrac"
    )
    val shape = RoundedCornerShape(18.dp)

    Box(Modifier.size(280.dp, 290.dp), Alignment.Center) {
        listOf(-1f, 1f).forEach { dir ->
            Box(
                Modifier.size(170.dp, 230.dp)
                    .graphicsLayer {
                        rotationZ = dir * 12f * fan
                        translationX = dir * 50.dp.toPx() * fan
                        translationY = 8.dp.toPx() * fan
                        alpha = 0.3f + 0.6f * fan
                    }
                    .clip(shape).background(Color(0xFFCBD5E1))
            )
        }
        Box(
            Modifier.size(180.dp, 240.dp)
                .graphicsLayer { translationY = bob.dp.toPx(); rotationZ = tilt * (1f - fan) }
                .clip(shape).background(Color(0xFFF1F5F9))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(50)).background(accent))
                Canvas(Modifier.size(60.dp).align(Alignment.CenterHorizontally)) {
                    drawArc(accent.copy(0.2f), 0f, 360f, false, style = Stroke(10f))
                    drawArc(accent, -90f, 360f * ringFrac, false, style = Stroke(10f, cap = StrokeCap.Round))
                }
                listOf(0.95f, 0.6f, 0.8f, 0.5f, 0.7f).forEachIndexed { i, w ->
                    val f = when (state) {
                        ReportState.Idle -> 0.3f
                        ReportState.Generating -> (progress * 5f - i).coerceIn(0f, 1f)
                        ReportState.Done -> 1f
                    }
                    FillBar(f, w, accent)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(0.55f), Color.Transparent),
                        Offset(size.width * shimmer, 0f), Offset(size.width * shimmer + 140f, size.height * 0.4f)
                    )
                )
                if (state == ReportState.Generating) {
                    val y = size.height * scan
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Aurora.Cyan.copy(0.45f), Color.Transparent),
                            startY = y - 40f, endY = y + 40f
                        ),
                        Offset(0f, y - 40f), Size(size.width, 80f)
                    )
                    drawLine(Aurora.Cyan, Offset(0f, y), Offset(size.width, y), 3f)
                }
            }
        }
        AnimatedVisibility(
            visible = state == ReportState.Done,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 14.dp),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier.graphicsLayer { rotationZ = -12f }
                    .clip(RoundedCornerShape(10.dp)).background(Aurora.Mint).padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Txt("✓ READY", 13, Color(0xFF052E1C), FontWeight.Black) }
        }
    }
}

@Composable
private fun OptionTile(emoji: String, title: String, sub: String, checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (checked) Aurora.Violet else Color.White.copy(0.12f), tween(300), label = "ob")
    val bg by animateColorAsState(if (checked) Aurora.Violet.copy(0.18f) else Color.White.copy(0.05f), tween(300), label = "obg")
    val pop by animateFloatAsState(if (checked) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "pop")
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, border, shape)
            .pressScale(haptic = true, onClick = onToggle).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(emoji, 26)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, 15, weight = FontWeight.SemiBold)
            Txt(sub, 12, Aurora.TextLo)
        }
        Canvas(Modifier.size(26.dp)) {
            drawCircle(Color.White.copy(0.2f), style = Stroke(3f))
            drawCircle(Aurora.Violet, size.minDimension / 2 * pop)
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(path, Color.White.copy(alpha = pop.coerceIn(0f, 1f)), style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private val pdfSteps = listOf("Collecting attendance data", "Drawing charts", "Building pages", "Finalizing PDF")

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    val pop by animateFloatAsState(if (done) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "stepPop")
    val spin by rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinA")
    val textColor by animateColorAsState(
        if (done) Aurora.Mint else if (active) Aurora.TextHi else Aurora.TextLo, label = "stepText"
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) {
            if (active && !done) {
                drawArc(Aurora.Cyan, spin, 250f, false, style = Stroke(4f, cap = StrokeCap.Round))
            } else {
                drawCircle(Color.White.copy(0.18f), style = Stroke(3f))
                drawCircle(Aurora.Mint, size.minDimension / 2 * pop)
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.34f)
                }
                drawPath(path, Color(0xFF052E1C).copy(alpha = pop.coerceIn(0f, 1f)), style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Spacer(Modifier.width(12.dp))
        Txt(label, 14, textColor, if (active || done) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ProgressOrb(progress: Float) {
    val p by animateFloatAsState(progress, tween(400), label = "orb")
    val spin by rememberInfiniteTransition(label = "orbSpin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "orbA")
    Box(Modifier.size(116.dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin * 0.2f }) {
            drawCircle(Aurora.Violet.copy(0.12f), size.minDimension / 2)
        }
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            drawArc(Color.White.copy(0.1f), 0f, 360f, false, style = Stroke(10f))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(Aurora.Violet, Aurora.Cyan, Aurora.Violet), center),
                    0f, 360f * p, false, style = Stroke(10f, cap = StrokeCap.Round)
                )
            }
        }
        Txt("${(p * 100).roundToInt()}%", 24, weight = FontWeight.Black)
    }
}

@Composable
fun PdfReportScreen(
    state: ReportState,
    progress: Float,
    onGenerate: (charts: Boolean, subjectWise: Boolean, predictions: Boolean) -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var charts by remember { mutableStateOf(true) }
    var subjectWise by remember { mutableStateOf(true) }
    var predictions by remember { mutableStateOf(true) }
    var burst by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val hf = LocalHapticFeedback.current
    LaunchedEffect(state) {
        if (state == ReportState.Done) { burst++; hf.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(Modifier.entrance(0)) {
            ScreenHeader("PDF Report", "A clean, shareable summary of your attendance", "📄", scroll)
        }

        Box(Modifier.fillMaxWidth().entrance(1), Alignment.Center) {
            ReportSheetStack(state, progress)
            ConfettiBurst(burst, Modifier.matchParentSize())
        }

        AnimatedContent(
            state == ReportState.Generating,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { (fadeIn(tween(300)) + expandVertically()) togetherWith (fadeOut(tween(200)) + shrinkVertically()) },
            label = "mid"
        ) { generating ->
            if (generating) {
                GlassCard(Modifier.fillMaxWidth(), accent = Aurora.Cyan, animatedBorder = true) {
                    pdfSteps.forEachIndexed { i, s ->
                        StepRow(s, done = progress >= (i + 1) / 4f, active = progress >= i / 4f && progress < (i + 1) / 4f)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().entrance(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionTile("📊", "Charts & rings", "Visual attendance breakdown", charts) { charts = !charts }
                    OptionTile("📚", "Subject-wise table", "Every subject with its status", subjectWise) { subjectWise = !subjectWise }
                    OptionTile("🔮", "What-if predictions", "Safe-skip and recovery numbers", predictions) { predictions = !predictions }
                }
            }
        }

        Box(Modifier.fillMaxWidth().entrance(3), Alignment.Center) {
            AnimatedContent(
                state,
                transitionSpec = {
                    (fadeIn(tween(350)) + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 0.8f))
                },
                label = "cta"
            ) { s ->
                when (s) {
                    ReportState.Idle -> PrimaryButton("Generate PDF report") { onGenerate(charts, subjectWise, predictions) }
                    ReportState.Generating -> ProgressOrb(progress)
                    ReportState.Done -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedCheck(Aurora.Mint)
                        Spacer(Modifier.height(8.dp))
                        Txt("Report ready", 18, Aurora.Mint, FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton("Share", onClick = onShare)
                            GhostButton("New", onClick = onReset)
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
 *  8. HOST — liquid nav bar + directional tab transitions
 * ═══════════════════════════════════════════════════════════════ */

enum class FeatureTab(val label: String, val icon: String) {
    Predict("What-If", "🔮"), Bunk("Bunk", "🏖️"), Report("Report", "📄")
}

@Composable
private fun NavItem(tab: FeatureTab, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val iconScale by animateFloatAsState(if (active) 1.3f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ic")
    val rot = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) { rot.animateTo(-18f, tween(110)); rot.animateTo(0f, spring(Spring.DampingRatioHighBouncy)) }
    }
    val textColor by animateColorAsState(if (active) Color.White else Aurora.TextLo, label = "navText")
    Column(
        modifier.pressScale(haptic = true, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Txt(tab.icon, 20, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale; rotationZ = rot.value })
        Txt(tab.label, 11, textColor, FontWeight.SemiBold)
    }
}

@Composable
private fun GlassNavBar(selected: FeatureTab, onSelect: (FeatureTab) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    var last by remember { mutableIntStateOf(selected.ordinal) }
    val movingRight = selected.ordinal >= last
    SideEffect { last = selected.ordinal }
    BoxWithConstraints(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(70.dp)
            .clip(shape).background(Color.White.copy(0.08f)).border(1.dp, Color.White.copy(0.15f), shape)
    ) {
        val w = maxWidth / FeatureTab.values().size
        val fast = spring<Dp>(dampingRatio = 0.75f, stiffness = 700f)
        val slow = spring<Dp>(dampingRatio = 0.7f, stiffness = 240f)
        val left by animateDpAsState(w * selected.ordinal, if (movingRight) slow else fast, label = "left")
        val right by animateDpAsState(w * (selected.ordinal + 1), if (movingRight) fast else slow, label = "right")
        Box(
            Modifier.offset(x = left).width(right - left).fillMaxHeight().padding(6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.horizontalGradient(listOf(Aurora.Violet, Aurora.Cyan)))
        )
        Row(Modifier.fillMaxSize()) {
            FeatureTab.values().forEach { tab ->
                NavItem(tab, tab == selected, Modifier.weight(1f).fillMaxHeight()) { onSelect(tab) }
            }
        }
    }
}

/**
 * Drop-in host. Wire your own ViewModel values into the parameters.
 * Tip: add Modifier.statusBarsPadding() / navigationBarsPadding() if you use edge-to-edge.
 */
@Composable
fun UpgradedFeatureHost(
    subject: String,
    attended: Int,
    total: Int,
    required: Float,
    reportState: ReportState,
    reportProgress: Float,
    onGenerateReport: (Boolean, Boolean, Boolean) -> Unit,
    onShareReport: () -> Unit,
    onResetReport: () -> Unit
) {
    var tab by remember { mutableStateOf(FeatureTab.Predict) }
    AuroraBackground {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(500, easing = FastOutSlowInEasing)) { it * dir / 3 } +
                            fadeIn(tween(450, delayMillis = 80)) + scaleIn(tween(500), initialScale = 0.92f)) togetherWith
                            (slideOutHorizontally(tween(350)) { -it * dir / 4 } + fadeOut(tween(220)) + scaleOut(tween(350), targetScale = 0.96f))
                    },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        FeatureTab.Predict -> WhatIfPredictorScreen(subject, attended, total, required)
                        FeatureTab.Bunk -> BunkPlannerScreen(subject, attended, total, required)
                        FeatureTab.Report -> PdfReportScreen(
                            reportState, reportProgress, onGenerateReport, onShareReport, onResetReport
                        )
                    }
                }
            }
            GlassNavBar(tab) { tab = it }
        }
    }
}
