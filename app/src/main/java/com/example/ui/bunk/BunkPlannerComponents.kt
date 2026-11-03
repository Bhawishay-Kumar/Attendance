package com.example.ui.bunk

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.bunk.BunkDayEntry
import com.example.data.bunk.BunkDayKind
import com.example.data.bunk.BunkOption
import com.example.data.bunk.BunkPlannerEngine
import com.example.data.bunk.SafetyTier
import com.example.data.bunk.SubjectStatus
import com.example.ui.settings.rememberMotionEnabled
import com.example.ui.theme.GlassThemeTokens
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// Motion plumbing
// =============================================================================================

/**
 * Decorative, endless motion (aurora, shimmer, pulses). Tests turn this off so the Compose test
 * clock can settle. Real screens leave it on; it is also gated by the system animation setting.
 */
internal val LocalBunkLiveMotion = compositionLocalOf { true }

@Composable
internal fun rememberLiveMotion(): Boolean = LocalBunkLiveMotion.current && rememberMotionEnabled()

/** 0..1 looping value, read it inside draw/graphicsLayer lambdas so only drawing re-runs. */
@Composable
internal fun rememberLoop(durationMs: Int, restart: Boolean = true, fallback: Float = 0f): State<Float> {
    val live = rememberLiveMotion()
    return if (live) {
        val inf = rememberInfiniteTransition(label = "bunkLoop")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMs, easing = LinearEasing),
                if (restart) RepeatMode.Restart else RepeatMode.Reverse
            ),
            label = "bunkLoopValue"
        )
    } else {
        remember { mutableStateOf(fallback) }
    }
}

/** One-shot 0 -> 1 reveal after [delayMs]; jumps straight to 1 when animations are off. */
@Composable
internal fun rememberReveal(delayMs: Long = 0L, durationMs: Int = 900): Float {
    val motion = rememberLiveMotion()
    var go by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay(delayMs)
            go = true
        }
    }
    val f by animateFloatAsState(
        targetValue = if (go) 1f else 0f,
        animationSpec = tween(if (motion) durationMs else 0, easing = FastOutSlowInEasing),
        label = "bunkReveal"
    )
    return f
}

/** Press feedback (springy scale) plus click. */
@Composable
internal fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "bunkPress"
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

// =============================================================================================
// Formatting helpers (display only; eligibility is decided by raw ratios in the engine)
// =============================================================================================

internal fun pct(value: Double): String = BunkPlannerEngine.fmt(value)

internal fun signed(value: Double): String {
    val s = BunkPlannerEngine.fmt(value)
    return if (value >= 0.0) "+$s" else s
}

internal fun shortWeekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
internal fun fullWeekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
internal fun shortMonth(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

internal fun rangeLabel(start: LocalDate, end: LocalDate): String =
    if (start == end) "${shortWeekday(start)} ${start.dayOfMonth} ${shortMonth(start)}"
    else if (start.month == end.month) "${shortWeekday(start)} ${start.dayOfMonth} – ${shortWeekday(end)} ${end.dayOfMonth} ${shortMonth(end)}"
    else "${start.dayOfMonth} ${shortMonth(start)} – ${end.dayOfMonth} ${shortMonth(end)}"

internal fun rankTitle(rank: Int): String = when (rank) {
    1 -> "Best Option"
    2 -> "Second Option"
    else -> "Third Option"
}

internal fun dayKindLabel(kind: BunkDayKind): String = when (kind) {
    BunkDayKind.BUNK -> "Bunk Day"
    BunkDayKind.WEEKEND -> "Weekend"
    BunkDayKind.HOLIDAY -> "Official Holiday"
    BunkDayKind.NO_CLASS_DAY -> "No Class Day"
}

internal fun rankAccent(glass: GlassThemeTokens, rank: Int): Color = when (rank) {
    1 -> glass.royalIndigo
    2 -> glass.electricTeal
    else -> glass.amberGold
}

/** Darker pair used for filled buttons and badges so white text stays readable in both themes. */
internal fun rankGradient(glass: GlassThemeTokens, rank: Int): List<Color> = when (rank) {
    1 -> listOf(glass.royalIndigo, glass.electricViolet)
    2 -> listOf(lerp(glass.electricTeal, Color.Black, 0.35f), glass.royalIndigo)
    else -> listOf(lerp(glass.amberGold, Color.Black, 0.38f), lerp(glass.crimsonRose, Color.Black, 0.1f))
}

internal fun pctColor(glass: GlassThemeTokens, value: Double, exam: Int, stipend: Int): Color = when {
    value >= maxOf(exam, stipend) -> glass.emeraldNeon
    value >= exam.coerceAtMost(stipend) -> glass.amberGold
    else -> glass.crimsonRose
}

internal fun statusColor(glass: GlassThemeTokens, s: SubjectStatus): Color = when (s) {
    SubjectStatus.ON_TRACK -> glass.emeraldNeon
    SubjectStatus.BELOW_STIPEND -> glass.amberGold
    SubjectStatus.BELOW_EXAM -> glass.crimsonRose
}

internal fun statusLabel(s: SubjectStatus): String = when (s) {
    SubjectStatus.ON_TRACK -> "On track"
    SubjectStatus.BELOW_STIPEND -> "Below stipend target"
    SubjectStatus.BELOW_EXAM -> "Below exam target"
}

internal fun dayKindColor(glass: GlassThemeTokens, kind: BunkDayKind): Color = when (kind) {
    BunkDayKind.BUNK -> glass.crimsonRose
    BunkDayKind.WEEKEND -> glass.royalIndigo
    BunkDayKind.HOLIDAY -> glass.amberGold
    BunkDayKind.NO_CLASS_DAY -> glass.electricTeal
}

// =============================================================================================
// Background
// =============================================================================================

private fun DrawScope.auroraBlob(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(cx, cy),
            radius = r
        ),
        radius = r,
        center = Offset(cx, cy)
    )
}

/** Slow drifting colour blobs behind the whole planner. Dark: cosmic. Light: soft pastel. */
@Composable
internal fun AuroraBackground(modifier: Modifier = Modifier) {
    val glass = LocalGlassTheme.current
    val t = rememberLoop(24000, fallback = 0.25f)
    val c1 = glass.royalIndigo
    val c2 = glass.electricViolet
    val c3 = glass.electricTeal
    val alpha = if (glass.isDark) 0.30f else 0.20f
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val ang = t.value * 2f * PI.toFloat()
                val w = size.width
                val h = size.height
                val r = max(w, h) * 0.55f
                auroraBlob(c1, alpha, w * (0.15f + 0.12f * sin(ang)), h * (0.10f + 0.06f * cos(ang)), r)
                auroraBlob(c2, alpha, w * (0.92f + 0.08f * cos(ang)), h * (0.38f + 0.08f * sin(ang)), r)
                auroraBlob(c3, alpha * 0.8f, w * (0.30f + 0.15f * sin(ang + 2f)), h * (0.96f + 0.04f * cos(ang)), r)
            }
    )
}

// =============================================================================================
// Cards, chips, text
// =============================================================================================

/** Glass card with accent glow and gradient hairline border. [shine] sweeps light across the border. */
@Composable
internal fun BunkGlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    accent2: Color? = null,
    shine: Boolean = false,
    content: @Composable () -> Unit
) {
    val glass = LocalGlassTheme.current
    val shape = RoundedCornerShape(24.dp)
    val shift: State<Float> = if (shine) rememberLoop(3400, fallback = 0.3f) else remember { mutableStateOf(0.3f) }
    val tint = accent ?: glass.royalIndigo
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                if (glass.isDark) 0.dp else 10.dp, shape,
                ambientColor = tint.copy(alpha = 0.22f), spotColor = tint.copy(alpha = 0.22f)
            )
            .clip(shape)
            .background(glass.cardSurface)
            .drawBehind {
                val c = Offset(size.width * 0.12f, 0f)
                val r = size.width * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = if (glass.isDark) 0.18f else 0.12f), Color.Transparent),
                        center = c, radius = r
                    ),
                    radius = r, center = c
                )
            }
            .drawWithContent {
                drawContent()
                val sw = 1.5.dp.toPx()
                val brush: Brush = if (accent != null) {
                    val second = accent2 ?: accent
                    if (shine) {
                        val x = size.width * (shift.value * 2f - 0.5f)
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.30f), second.copy(alpha = 0.95f), accent.copy(alpha = 0.30f)),
                            start = Offset(x - size.width * 0.6f, 0f),
                            end = Offset(x + size.width * 0.6f, size.height)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.65f), second.copy(alpha = 0.25f)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    }
                } else SolidColor(glass.cardBorder)
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(sw)
                )
            }
            .padding(16.dp)
    ) { content() }
}

@Composable
internal fun BunkChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
internal fun TierChip(tier: SafetyTier) {
    val glass = LocalGlassTheme.current
    if (tier == SafetyTier.COMFORTABLE) BunkChip("Comfortable", glass.emeraldNeon) else BunkChip("Tight", glass.amberGold)
}

/** Icon + message pill used for risk lines and notes. */
@Composable
internal fun InfoPill(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
internal fun StatBlock(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    sub: String? = null
) {
    val glass = LocalGlassTheme.current
    Column(modifier = modifier) {
        Text(value, fontFamily = OutfitFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textMuted)
        if (sub != null) Text(sub, fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = glass.textMuted)
    }
}

/** Tinted tile with a count-up number. */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    sub: String? = null
) {
    val glass = LocalGlassTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(value, fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, color = glass.textSecondary)
        if (sub != null) Text(sub, fontFamily = PlusJakartaSansFontFamily, fontSize = 9.5.sp, color = glass.textMuted)
    }
}

/** Percentage that counts up from 0 to [value]. The final frame shows the exact value. */
@Composable
internal fun CountUpPercent(
    value: Double,
    color: Color,
    fontSize: TextUnit,
    delayMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val f = rememberReveal(delayMs, 1000)
    Text(
        "${pct(value * f)}%",
        modifier = modifier,
        fontFamily = OutfitFontFamily, fontSize = fontSize, fontWeight = FontWeight.Bold, color = color
    )
}

// =============================================================================================
// Progress visuals
// =============================================================================================

/** Thin bar with a target tick. The fill animates unless the user disabled animations. */
@Composable
internal fun AttendanceBar(value: Double, target: Int, color: Color, modifier: Modifier = Modifier) {
    val glass = LocalGlassTheme.current
    val motion = rememberMotionEnabled()
    val fraction by animateFloatAsState(
        targetValue = (value / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = if (motion) tween(900, easing = FastOutSlowInEasing) else tween(0),
        label = "attBar"
    )
    Box(modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(glass.cardBorder)) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color)))
        )
        Box(Modifier.fillMaxWidth((target / 100f).coerceIn(0f, 1f)).height(8.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(2.dp)
                    .height(8.dp)
                    .background(glass.textPrimary.copy(alpha = 0.8f))
            )
        }
    }
}

/** Circular attendance gauge with a target tick; sweeps in and counts up. */
@Composable
internal fun AttendanceRing(
    percent: Double?,
    target: Int,
    color: Color,
    diameter: Dp,
    caption: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    delayMs: Long = 150L
) {
    val glass = LocalGlassTheme.current
    val f = rememberReveal(delayMs, 1100)
    val track = glass.cardBorder
    val tick = glass.textPrimary.copy(alpha = 0.85f)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw)
            )
            if (percent != null) {
                val sweep = 360f * (percent / 100.0).toFloat().coerceIn(0f, 1f) * f
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            val ang = ((-90f + target.coerceIn(0, 100) * 3.6f) * PI.toFloat()) / 180f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = arcSize.width / 2f
            val dx = cos(ang)
            val dy = sin(ang)
            drawLine(
                color = tick,
                start = Offset(cx + dx * (radius - sw * 0.85f), cy + dy * (radius - sw * 0.85f)),
                end = Offset(cx + dx * (radius + sw * 0.85f), cy + dy * (radius + sw * 0.85f)),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (percent != null) {
                CountUpPercent(percent, color, (diameter.value * 0.2f).sp, delayMs)
            } else {
                Text("—", fontFamily = OutfitFontFamily, fontSize = (diameter.value * 0.2f).sp, fontWeight = FontWeight.Bold, color = glass.textMuted)
            }
            Text(caption, fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, color = glass.textMuted)
        }
    }
}

/** Big gradient badge with the count of consecutive days off. */
@Composable
internal fun LeaveBadge(
    days: Int,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    delayMs: Long = 100L
) {
    val f = rememberReveal(delayMs, 800)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = 0.7f + 0.3f * f
                scaleX = s; scaleY = s; alpha = f
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(days * f).roundToInt()}",
                fontFamily = OutfitFontFamily, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text("days off", fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

/** Numbered medal; the best option gets a soft pulsing halo. */
@Composable
internal fun RankMedal(rank: Int, gradient: List<Color>, accent: Color, modifier: Modifier = Modifier) {
    val pulse: State<Float> = if (rank == 1) rememberLoop(1900, fallback = 0f) else remember { mutableStateOf(0f) }
    val f = rememberReveal(60L, 600)
    Box(modifier.size(42.dp), contentAlignment = Alignment.Center) {
        if (rank == 1) {
            Box(
                Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        val k = pulse.value
                        scaleX = 1f + 0.35f * k; scaleY = 1f + 0.35f * k; alpha = 0.5f * (1f - k)
                    }
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .graphicsLayer { val s = 0.4f + 0.6f * f; scaleX = s; scaleY = s; alpha = f }
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
    }
}

/** Tiny twinkling stars for the best option. */
@Composable
internal fun Twinkles(color: Color, modifier: Modifier = Modifier) {
    val t = rememberLoop(2600, fallback = 0.2f)
    Box(
        modifier.drawBehind {
            val spots = listOf(
                Offset(0.70f, 0.10f), Offset(0.84f, 0.22f), Offset(0.93f, 0.08f),
                Offset(0.78f, 0.34f), Offset(0.96f, 0.30f)
            )
            spots.forEachIndexed { i, p ->
                val a = 0.5f + 0.5f * sin(t.value * 2f * PI.toFloat() + i * 1.3f)
                drawCircle(
                    color = color.copy(alpha = 0.15f + 0.6f * a),
                    radius = (1.2f + 1.6f * a).dp.toPx(),
                    center = Offset(size.width * p.x, size.height * p.y)
                )
            }
        }
    )
}

/** Animated halo + floating icon used by empty and warning states. */
@Composable
internal fun HeroIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val float = rememberLoop(2800, fallback = 0f)
    val halo = rememberLoop(2100, fallback = 0f)
    Box(modifier.size(size + 28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val k = halo.value
                    scaleX = 1f + 0.5f * k; scaleY = 1f + 0.5f * k; alpha = 0.35f * (1f - k)
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            Modifier
                .size(size)
                .graphicsLayer { translationY = sin(float.value * 2f * PI.toFloat()) * 4.dp.toPx() }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Skeleton block with a moving highlight. */
@Composable
internal fun ShimmerBox(modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val glass = LocalGlassTheme.current
    val shift = rememberLoop(1300, fallback = 0.5f)
    val base = glass.cardBorder.copy(alpha = if (glass.isDark) 0.55f else 0.7f)
    val hi = glass.textMuted.copy(alpha = 0.28f)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                val x = size.width * (shift.value * 2f - 0.5f)
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(base, hi, base),
                        start = Offset(x - size.width * 0.5f, 0f),
                        end = Offset(x + size.width * 0.5f, size.height)
                    )
                )
            }
    )
}

// =============================================================================================
// Date strip
// =============================================================================================

/** One date of the break. Red = you must miss it; the others are already free. */
@Composable
internal fun DateChip(entry: BunkDayEntry, index: Int) {
    val glass = LocalGlassTheme.current
    val motion = rememberMotionEnabled()
    var shown by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) {
        if (motion) { delay(index * 40L); shown = true }
    }
    val a by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "chipIn"
    )
    val color = dayKindColor(glass, entry.kind)
    val isBunk = entry.kind == BunkDayKind.BUNK
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                alpha = a.coerceIn(0f, 1f)
                scaleX = 0.8f + 0.2f * a; scaleY = 0.8f + 0.2f * a
                translationY = (1f - a) * 12.dp.toPx()
            }
            .width(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (isBunk) Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.14f)))
                else Brush.verticalGradient(listOf(color.copy(alpha = 0.12f), color.copy(alpha = 0.06f)))
            )
            .border(1.dp, color.copy(alpha = if (isBunk) 0.75f else 0.28f), RoundedCornerShape(15.dp))
            .padding(vertical = 8.dp)
    ) {
        Text(shortWeekday(entry.date), fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = glass.textSecondary)
        Text(
            entry.date.dayOfMonth.toString(),
            fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary
        )
        Text(
            when (entry.kind) {
                BunkDayKind.BUNK -> "Absent"
                BunkDayKind.WEEKEND -> "Free"
                BunkDayKind.HOLIDAY -> "Holiday"
                BunkDayKind.NO_CLASS_DAY -> "No class"
            },
            fontFamily = PlusJakartaSansFontFamily, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            color = color, textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun DateStrip(option: BunkOption) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        option.days.forEachIndexed { i, e -> DateChip(e, i) }
    }
}

@Composable
internal fun StripLegend() {
    val glass = LocalGlassTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            "You miss" to glass.crimsonRose,
            "Weekend" to glass.royalIndigo,
            "Holiday" to glass.amberGold
        ).forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(4.dp))
                Text(t, fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = glass.textMuted)
            }
        }
    }
}

/** Shows how few days you miss compared with the days you get off. */
@Composable
internal fun FreeVsMissedBar(option: BunkOption) {
    val glass = LocalGlassTheme.current
    val f = rememberReveal(200L, 800)
    val missed = option.workingDaysMissed
    val free = option.calendarDays - option.workingDaysMissed
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .graphicsLayer { alpha = f }
                .clip(RoundedCornerShape(50)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (missed > 0) {
                Box(Modifier.weight(missed.toFloat()).fillMaxHeight().background(glass.crimsonRose))
            }
            if (free > 0) {
                Box(
                    Modifier
                        .weight(free.toFloat())
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(glass.royalIndigo, glass.electricViolet)))
                )
            }
        }
        Text(
            "Miss $missed working ${if (missed == 1) "day" else "days"} → get ${option.calendarDays} days off ($free free)",
            fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textSecondary
        )
    }
}

@Composable
internal fun GradientButton(
    text: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(gradient))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            if (trailing != null) trailing()
        }
    }
}

/** Provides the live-motion flag to everything below. */
@Composable
internal fun ProvideLiveMotion(live: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBunkLiveMotion provides live) { content() }
}
