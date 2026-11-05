package com.example.ui.reports

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.reports.ReportAnalytics
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ entrance animation

/**
 * Fade + rise + slight scale-in with a per-item delay, so lists and grids reveal in a wave.
 * Does nothing (shown immediately) under reduced motion. [key] restarts it when data changes.
 */
@Composable
fun Modifier.entrance(index: Int, reduced: Boolean, key: Any? = Unit, stepMs: Long = 55L): Modifier {
    var shown by remember(key) { mutableStateOf(reduced) }
    LaunchedEffect(key) {
        if (!reduced) {
            delay((index.coerceAtMost(14) * stepMs))
            shown = true
        }
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(if (reduced) 0 else 420, easing = FastOutSlowInEasing), label = "entrance")
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 22.dp.toPx()
        val sc = 0.95f + 0.05f * a
        scaleX = sc
        scaleY = sc
    }
}

// ------------------------------------------------------------------ stat tiles

enum class StatKind { ATTENDED, MISSED, CONDUCTED, CANCELLED }

private fun statTint(kind: StatKind, p: RptPalette): Color = when (kind) {
    StatKind.ATTENDED -> if (p.isDark) Color(0xFF10B981) else Color(0xFF059669)
    StatKind.MISSED -> if (p.isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    StatKind.CONDUCTED -> if (p.isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    StatKind.CANCELLED -> if (p.isDark) Color(0xFFF97316) else Color(0xFFEA580C)
}

/** Crisp vector-style glyphs drawn on a Canvas: check, cross, calendar, ban. */
@Composable
fun StatIcon(kind: StatKind, color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.11f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            StatKind.ATTENDED -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.54f); lineTo(w * 0.42f, h * 0.76f); lineTo(w * 0.84f, h * 0.27f)
                }
                drawPath(path, color, style = st)
            }
            StatKind.MISSED -> {
                drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), sw, StrokeCap.Round)
            }
            StatKind.CONDUCTED -> {
                drawRoundRect(color, Offset(w * 0.12f, h * 0.2f), Size(w * 0.76f, h * 0.68f), CornerRadius(w * 0.14f), style = st)
                drawLine(color, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.88f, h * 0.42f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.08f), Offset(w * 0.32f, h * 0.28f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, h * 0.08f), Offset(w * 0.68f, h * 0.28f), sw, StrokeCap.Round)
                for (r in 0..1) for (c in 0..2) {
                    drawCircle(color, w * 0.05f, Offset(w * (0.30f + 0.2f * c), h * (0.56f + 0.15f * r)))
                }
            }
            StatKind.CANCELLED -> {
                drawCircle(color, w * 0.40f, center, style = st)
                drawLine(color, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.26f, h * 0.74f), sw, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun ReportStatTile(
    p: RptPalette,
    kind: StatKind,
    value: Int,
    label: String,
    index: Int,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = statTint(kind, p)
    var started by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(if (started) value else 0, tween(if (reduced) 0 else 900, easing = FastOutSlowInEasing), label = "count")
    val shape = RoundedCornerShape(18.dp)
    val base = if (p.isDark) Color(0xFF121826) else com.example.ui.theme.LightPearl
    Box(
        modifier = modifier
            .popIn(index, reduced, stepMs = 70L)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = if (p.isDark) 0.30f else 0.18f), base, base.copy(alpha = 0.92f)),
                    start = Offset(0f, 0f), end = Offset(420f, 520f)
                )
            )
            .border(BorderStroke(1.dp, if (p.isDark) Color(0x553B4A8F) else tint.copy(alpha = 0.35f)), shape)
            .shineSweep(!reduced, 4200 + index * 500, 0.14f)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f))
                    .border(BorderStroke(1.2.dp, tint.copy(alpha = 0.85f)), CircleShape),
                contentAlignment = Alignment.Center
            ) { StatIcon(kind, tint, 15.dp) }
            RText("$shown", 20f, p.text, weight = FontWeight.Bold, display = true, tnum = true)
            RText(label, 11f, p.textSec, weight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Compact four-tile summary: Attended / Missed / Conducted / Cancelled for the selected period. */
@Composable
fun ReportStatTiles(p: RptPalette, a: ReportAnalytics, periodWord: String, reduced: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RText("$periodWord Summary", 14f, p.text, weight = FontWeight.Bold, display = true)
            RText("credit hours", 11f, p.textMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportStatTile(p, StatKind.ATTENDED, a.attendedCredits, "Attended", 0, reduced, Modifier.weight(1f))
            ReportStatTile(p, StatKind.MISSED, a.missedCredits, "Missed", 1, reduced, Modifier.weight(1f))
            ReportStatTile(p, StatKind.CONDUCTED, a.conductedCredits, "Conducted", 2, reduced, Modifier.weight(1f))
            ReportStatTile(p, StatKind.CANCELLED, a.cancelledCredits, "Cancelled", 3, reduced, Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ progress ring

/**
 * Animated gradient ring with a soft pulsing glow. Target notches are shown only when there is
 * data (an empty ring with stray marks looked like a rendering glitch). The glow value is read
 * in the draw phase, so the ring never recomposes per frame.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    examTarget: Int,
    stipendTarget: Int,
    p: RptPalette,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    ringSize: Dp = 128.dp,
    showTargets: Boolean = true,
    content: @Composable () -> Unit
) {
    var started by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { started = true }
    val f by animateFloatAsState(
        if (started) fraction.coerceIn(0f, 1f) else 0f,
        tween(if (reduced) 0 else 1100, easing = FastOutSlowInEasing), label = "ring"
    )
    val glow: State<Float> = if (reduced) {
        remember { mutableStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "glow").animateFloat(
            0.25f, 0.8f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "g"
        )
    }
    val sweepColors = remember(p.indigo, p.cyan, p.emerald) { listOf(p.indigo, p.cyan, p.emerald, p.indigo) }
    val targetEntries = remember(examTarget, stipendTarget, p.amber, p.text) {
        listOf(examTarget to p.amber, stipendTarget to p.text)
    }
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val stroke = ringSize.toPx() * 0.095f
            val inset = stroke / 2f + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawCircle(
                Brush.radialGradient(listOf(p.emerald.copy(alpha = 0.16f * glow.value), Color.Transparent)),
                radius = size.minDimension / 2f
            )
            drawArc(p.trackBg, 0f, 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (f > 0.002f) {
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(sweepColors),
                        0f, 360f * f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            if (showTargets && fraction > 0f) {
                val r = arcSize.width / 2f
                val c = center
                targetEntries.forEach { (t, col) ->
                    val ang = Math.toRadians(-90.0 + 360.0 * t / 100.0)
                    val cx = kotlin.math.cos(ang).toFloat(); val cy = kotlin.math.sin(ang).toFloat()
                    drawLine(
                        col.copy(alpha = 0.7f),
                        Offset(c.x + cx * (r - stroke * 0.55f), c.y + cy * (r - stroke * 0.55f)),
                        Offset(c.x + cx * (r + stroke * 0.55f), c.y + cy * (r + stroke * 0.55f)),
                        strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
                    )
                }
            }
        }
        content()
    }
}
