package com.example.ui.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.reports.BreakdownSlice
import com.example.data.reports.ReportSessionStatus
import com.example.data.reports.SubjectComparison
import com.example.data.reports.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

fun shortDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(dateFmt) }.getOrDefault(iso)

fun fmt1(d: Double): String = String.format(Locale.US, "%.1f", d)

/** Animated gradient progress bar with a shimmer sweep (shimmer is skipped under reduced motion). */
@Composable
fun GradientProgressBar(
    fraction: Float,
    p: RptPalette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 10.dp,
    colors: List<Color> = listOf(p.indigo, p.cyan, p.emerald)
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val f by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 900, easing = FastOutSlowInEasing),
        label = "progress"
    )
    val shimmer: State<Float> = if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "s"
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(p.trackBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(f)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(colors))
                .drawWithContent {
                    drawContent()
                    if (!reducedMotion && f > 0.02f) {
                        val band = size.width * 0.35f
                        val x = (size.width + band) * shimmer.value - band
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                                startX = x, endX = x + band
                            )
                        )
                    }
                }
        )
    }
}

/**
 * Real-data line chart. Draws the line in (animated), shows target lines, and lets the user tap
 * or drag to read the exact date and value of a point.
 */
@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    examTarget: Int,
    stipendTarget: Int,
    p: RptPalette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            RText(
                if (points.isEmpty()) "No confirmed attendance in this window yet."
                else "Only one recorded day so far. Trends need at least two.",
                12f, p.textMuted
            )
        }
        return
    }
    val progress = remember(points) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(points) {
        if (!reducedMotion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val sel = points[selected.coerceIn(0, points.lastIndex)]

    val minV = minOf(points.minOf { it.cumulativePercentage }, examTarget.toDouble(), stipendTarget.toDouble())
    val lo = (kotlin.math.floor((minV - 5.0) / 10.0) * 10.0).coerceIn(0.0, 90.0)
    val hi = 100.0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RText(shortDate(sel.date), 12f, p.textSec, weight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("${fmt1(sel.cumulativePercentage)}%", 16f, p.text, weight = FontWeight.Bold, display = true, tnum = true)
                sel.dayPercentage?.let {
                    RText("  day ${fmt1(it)}%", 11f, p.textMuted, tnum = true)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(points) {
                    detectTapGestures { o ->
                        val step = size.width.toFloat() / (points.size - 1)
                        selected = (o.x / step).let { kotlin.math.round(it).toInt() }.coerceIn(0, points.lastIndex)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        val step = size.width.toFloat() / (points.size - 1)
                        selected = (change.position.x / step).let { kotlin.math.round(it).toInt() }.coerceIn(0, points.lastIndex)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1)
            fun y(v: Double) = (h * (1.0 - (v - lo) / (hi - lo))).toFloat()

            // gridlines + target lines
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            listOf(examTarget to p.amber, stipendTarget to p.emerald).forEach { (t, c) ->
                if (t > lo) drawLine(c.copy(alpha = 0.55f), Offset(0f, y(t.toDouble())), Offset(w, y(t.toDouble())), strokeWidth = 1.5f * density, pathEffect = dash)
            }

            val line = Path()
            points.forEachIndexed { i, pt ->
                val px = x(i); val py = y(pt.cumulativePercentage)
                if (i == 0) line.moveTo(px, py) else {
                    val px0 = x(i - 1); val py0 = y(points[i - 1].cumulativePercentage)
                    val cx = (px0 + px) / 2f
                    line.cubicTo(cx, py0, cx, py, px, py)
                }
            }
            // area under curve
            val area = Path().apply {
                addPath(line)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(p.indigo.copy(alpha = 0.30f * progress.value), Color.Transparent)),
            )
            val pm = PathMeasure()
            pm.setPath(line, false)
            val seg = Path()
            pm.getSegment(0f, pm.length * progress.value, seg, true)
            drawPath(
                seg,
                Brush.horizontalGradient(listOf(p.indigo, p.cyan, p.emerald)),
                style = Stroke(width = 3f * density, cap = StrokeCap.Round)
            )
            if (progress.value > 0.98f) {
                points.forEachIndexed { i, pt ->
                    drawCircle(p.bg, 4f * density, Offset(x(i), y(pt.cumulativePercentage)))
                    drawCircle(p.cyan, 2.5f * density, Offset(x(i), y(pt.cumulativePercentage)))
                }
            }
            // selection guide
            val si = selected.coerceIn(0, points.lastIndex)
            drawLine(p.textMuted.copy(alpha = 0.5f), Offset(x(si), 0f), Offset(x(si), h), strokeWidth = 1f * density)
            drawCircle(p.emerald, 6f * density, Offset(x(si), y(points[si].cumulativePercentage)))
            drawCircle(p.bg, 3f * density, Offset(x(si), y(points[si].cumulativePercentage)))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RText(shortDate(points.first().date), 10f, p.textMuted)
            if (points.size > 2) RText(shortDate(points[points.size / 2].date), 10f, p.textMuted)
            RText(shortDate(points.last().date), 10f, p.textMuted)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(p.amber, "Exam $examTarget%", p)
            LegendDot(p.emerald, "Stipend $stipendTarget%", p)
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, p: RptPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(5.dp))
        RText(label, 10f, p.textSec)
    }
}

/** Horizontal bars of per-subject percentage, with exam/stipend markers. Bars grow on entry. */
@Composable
fun SubjectComparisonBars(
    items: List<SubjectComparison>,
    examTarget: Int,
    stipendTarget: Int,
    p: RptPalette,
    reducedMotion: Boolean
) {
    if (items.isEmpty()) {
        RText("No subject has confirmed hours in this window.", 12f, p.textMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { it0 ->
            val color = when {
                it0.percentage < examTarget -> p.rose
                it0.percentage < stipendTarget -> p.amber
                else -> p.emerald
            }
            val frac by animateFloatAsState(
                (it0.percentage / 100.0).toFloat().coerceIn(0f, 1f),
                tween(if (reducedMotion) 0 else 700), label = "bar"
            )
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RText(it0.name, 12f, p.text, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    RText("${fmt1(it0.percentage)}%  ${it0.attended}/${it0.conducted}h", 11f, color, weight = FontWeight.Bold, tnum = true)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(p.trackBg)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(50)).background(color))
                }
            }
        }
    }
}

fun statusColor(s: ReportSessionStatus, p: RptPalette): Color = when (s) {
    ReportSessionStatus.PRESENT -> p.emerald
    ReportSessionStatus.AUTO_PRESENT -> p.cyan
    ReportSessionStatus.ABSENT -> p.rose
    ReportSessionStatus.NO_CLASS -> p.amber
    ReportSessionStatus.PENDING -> p.textMuted
    ReportSessionStatus.UPCOMING -> p.indigo
}

fun statusLabel(s: ReportSessionStatus): String = when (s) {
    ReportSessionStatus.PRESENT -> "Present"
    ReportSessionStatus.AUTO_PRESENT -> "Auto-credited"
    ReportSessionStatus.ABSENT -> "Absent"
    ReportSessionStatus.NO_CLASS -> "No Class"
    ReportSessionStatus.PENDING -> "Pending"
    ReportSessionStatus.UPCOMING -> "Upcoming"
}

/** Stacked session-count bar plus a legend with sessions and weighted hours per status. */
@Composable
fun BreakdownChart(slices: List<BreakdownSlice>, p: RptPalette, reducedMotion: Boolean) {
    val total = slices.sumOf { it.sessions }
    if (total == 0) {
        RText("No sessions in this window.", 12f, p.textMuted)
        return
    }
    var started by remember(slices) { mutableStateOf(false) }
    LaunchedEffect(slices) { started = true }
    val reveal by animateFloatAsState(if (started) 1f else 0f, tween(if (reducedMotion) 0 else 800), label = "reveal")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(p.trackBg)) {
            slices.filter { it.sessions > 0 }.forEach { s ->
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(s.sessions.toFloat() * reveal.coerceAtLeast(0.001f))
                        .background(statusColor(s.status, p))
                )
            }
            if (reveal < 1f) Box(Modifier.fillMaxHeight().weight(total * (1f - reveal).coerceAtLeast(0.001f)))
        }
        slices.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LegendDot(statusColor(s.status, p), statusLabel(s.status), p)
                RText("${s.sessions} sessions · ${s.hours} hrs", 11f, p.textSec, tnum = true)
            }
        }
    }
}
