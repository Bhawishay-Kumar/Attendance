package com.example.ui.reports

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.reports.DayReport
import com.example.data.reports.DayStatus
import com.example.data.reports.ReportAnalytics
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportSessionStatus
import com.example.data.reports.ReportTrendEngine
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val longDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH)
private val weekdayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Weekly navigation handed to the week calendar (previous / next / jump to this week). */
class WeekNav(
    val canPrev: Boolean,
    val canNext: Boolean,
    val isCurrentWeek: Boolean,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onThisWeek: () -> Unit
)

fun dayStatusColor(s: DayStatus, p: RptPalette): Color = when (s) {
    DayStatus.PRESENT -> p.emerald
    DayStatus.ABSENT -> p.rose
    DayStatus.INCOMPLETE -> p.indigo
    DayStatus.HOLIDAY -> p.amber
    DayStatus.CANCELLED -> if (p.isDark) Color(0xFFF97316) else Color(0xFFEA580C)
    DayStatus.PENDING -> p.textSec
    DayStatus.UPCOMING, DayStatus.WEEKEND, DayStatus.NO_SCHEDULE -> p.textMuted
}

fun dayStatusLabel(s: DayStatus): String = when (s) {
    DayStatus.PRESENT -> "Present"
    DayStatus.ABSENT -> "Absent"
    DayStatus.INCOMPLETE -> "Incomplete"
    DayStatus.HOLIDAY -> "Holiday"
    DayStatus.CANCELLED -> "No Class"
    DayStatus.PENDING -> "Pending"
    DayStatus.UPCOMING -> "Upcoming"
    DayStatus.WEEKEND -> "Weekend"
    DayStatus.NO_SCHEDULE -> "No classes"
}

private fun dayGlyph(s: DayStatus): String = when (s) {
    DayStatus.PRESENT -> "\u2713"
    DayStatus.ABSENT -> "\u2715"
    DayStatus.CANCELLED -> "\u2298"
    DayStatus.HOLIDAY -> "\u2605"
    DayStatus.INCOMPLETE -> "\u25D0"
    DayStatus.PENDING -> "\u2026"
    else -> "\u00B7"
}

private fun isQuiet(s: DayStatus) = s == DayStatus.WEEKEND || s == DayStatus.UPCOMING || s == DayStatus.NO_SCHEDULE

/** Marked (Present/Absent) manual sessions out of the manual sessions that count that day. */
private fun manualProgress(d: DayReport): Pair<Float, Pair<Int, Int>> {
    val live = d.sessions.filter { !it.subject.isAutoPresent && it.status != ReportSessionStatus.NO_CLASS }
    if (live.isEmpty()) return 0f to (0 to 0)
    val marked = live.filter { it.status == ReportSessionStatus.PRESENT || it.status == ReportSessionStatus.ABSENT }
    val att = marked.filter { it.status == ReportSessionStatus.PRESENT }.sumOf { it.weight }
    val cond = marked.sumOf { it.weight }
    return (marked.size.toFloat() / live.size) to (att to cond)
}

// ------------------------------------------------------------------ shared building blocks

@Composable
fun CalendarLegend(p: RptPalette) {
    val items = listOf(DayStatus.PRESENT, DayStatus.ABSENT, DayStatus.INCOMPLETE, DayStatus.HOLIDAY, DayStatus.CANCELLED, DayStatus.PENDING)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { LegendDot(dayStatusColor(it, p), dayStatusLabel(it), p) }
    }
}

/** Rotating gradient ring marking today on circular month cells (angle read in the draw phase). */
@Composable
private fun TodayRing(p: RptPalette, reduced: Boolean, modifier: Modifier) {
    val angle: State<Float> = if (reduced) {
        remember { mutableStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "todayRing").animateFloat(
            0f, 360f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "a"
        )
    }
    Canvas(modifier) {
        rotate(angle.value) {
            drawCircle(
                Brush.sweepGradient(listOf(p.indigo, p.cyan, p.violet, Color.Transparent, p.indigo)),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/** Gently breathing rounded border marking today on week cards (no rotation; the spinning ring is month-grid only). */
@Composable
private fun TodayPulseBorder(p: RptPalette, reduced: Boolean, corner: Dp, modifier: Modifier) {
    val a: State<Float> = if (reduced) {
        remember { mutableStateOf(0.9f) }
    } else {
        rememberInfiniteTransition(label = "todayPulse").animateFloat(
            0.45f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
        )
    }
    Canvas(modifier) {
        drawRoundRect(
            p.indigo.copy(alpha = a.value),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(2.dp.toPx())
        )
    }
}

/** Twinkling four-point sparkle used on holiday cells. */
@Composable
private fun Sparkle(color: Color, reduced: Boolean, modifier: Modifier = Modifier) {
    val tw: State<Float> = if (reduced) {
        remember { mutableStateOf(0.9f) }
    } else {
        rememberInfiniteTransition(label = "sparkle").animateFloat(
            0.3f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s"
        )
    }
    Canvas(modifier.size(9.dp).graphicsLayer { alpha = tw.value; scaleX = 0.7f + 0.3f * tw.value; scaleY = 0.7f + 0.3f * tw.value }) {
        val c = center
        val l = size.width / 2f
        drawLine(color, Offset(c.x - l, c.y), Offset(c.x + l, c.y), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - l), Offset(c.x, c.y + l), 1.6.dp.toPx(), StrokeCap.Round)
        drawCircle(color, 1.4.dp.toPx(), c)
    }
}

@Composable
private fun CalArrow(glyph: String, enabled: Boolean, p: RptPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) p.indigo.copy(alpha = 0.20f) else Color.Transparent)
            .pressClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { RText(glyph, 22f, if (enabled) p.text else p.textMuted.copy(alpha = 0.35f), weight = FontWeight.Bold) }
}

/** Animated detail for the tapped date: accent bar, real periods, subjects and statuses. */
@Composable
fun DayDetailPanel(p: RptPalette, day: DayReport?, holidayName: (String) -> String?, reduced: Boolean) {
    AnimatedContent(
        targetState = day,
        transitionSpec = {
            (fadeIn(tween(240)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 8 }) togetherWith fadeOut(tween(120))
        },
        label = "dayDetail"
    ) { d ->
        val accent = if (d == null) p.indigo else dayStatusColor(d.status, p)
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), p.trackBg)))
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d == null) {
                    RText("Tap a day to see its periods and statuses.", 12f, p.textMuted)
                } else {
                    val date = LocalDate.parse(d.date)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        RText(date.format(longDay), 13f, p.text, weight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        StatusPill(dayStatusLabel(d.status), accent, size = 11f)
                    }
                    when (d.status) {
                        DayStatus.HOLIDAY -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Sparkle(p.amber, reduced)
                            Spacer(Modifier.width(8.dp))
                            RText(holidayName(d.date) ?: "Holiday (marked manually)", 12f, p.amber, weight = FontWeight.SemiBold)
                        }
                        DayStatus.WEEKEND -> RText("No classes on weekends.", 12f, p.textMuted)
                        DayStatus.NO_SCHEDULE -> RText("No classes are scheduled on this day.", 12f, p.textMuted)
                        else -> {
                            if (d.conductedCredits > 0) {
                                RText("${d.attendedCredits}/${d.conductedCredits} credit hrs counted (auto-credited included)", 11f, p.textSec, tnum = true)
                            }
                            d.sessions.forEachIndexed { i, s ->
                                Row(
                                    Modifier.fillMaxWidth().entrance(i, reduced, key = d.date, stepMs = 40L),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor(s.status, p)))
                                        Spacer(Modifier.width(8.dp))
                                        RText("P${s.periodNumber}", 10f, p.textMuted, weight = FontWeight.Bold, tnum = true, modifier = Modifier.width(24.dp))
                                        RText(s.subject.shortName + if (s.isTutorial) " \u00B7 Tutorial" else "", 12f, p.text, weight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                    RText("${s.weight}h", 10f, p.textMuted, tnum = true)
                                    Spacer(Modifier.width(8.dp))
                                    StatusPill(statusLabel(s.status), statusColor(s.status, p), size = 10f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HolidayPills(p: RptPalette, a: ReportAnalytics, holidayName: (String) -> String?, reduced: Boolean) {
    val hols = a.days.filter { it.status == DayStatus.HOLIDAY }
    if (hols.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RText("Holidays in this period", 12f, p.amber, weight = FontWeight.Bold)
        hols.forEachIndexed { i, h ->
            val d = LocalDate.parse(h.date)
            Row(
                Modifier.fillMaxWidth().entrance(i, reduced, key = a.range, stepMs = 70L)
                    .clip(RoundedCornerShape(50)).background(p.amber.copy(alpha = 0.14f))
                    .border(BorderStroke(1.dp, p.amber.copy(alpha = 0.35f)), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Sparkle(p.amber, reduced)
                    Spacer(Modifier.width(8.dp))
                    RText("${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} \u00B7 ${holidayName(h.date) ?: "Holiday (manual)"}", 11f, p.text, weight = FontWeight.SemiBold, maxLines = 1)
                }
                RText("Holiday", 10f, p.amber, weight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ month calendar

@Composable
fun MonthCalendarCard(
    p: RptPalette,
    a: ReportAnalytics,
    selected: String?,
    onSelect: (String?) -> Unit,
    today: LocalDate,
    holidayName: (String) -> String?,
    reduced: Boolean
) {
    val days = a.days
    if (days.isEmpty()) return
    val first = LocalDate.parse(days.first().date)
    val offset = first.dayOfWeek.value - 1
    val rows = (offset + days.size + 6) / 7
    val selectedDay = days.firstOrNull { it.date == selected }

    val working = days.filter { it.status != DayStatus.HOLIDAY && it.status != DayStatus.WEEKEND && it.status != DayStatus.NO_SCHEDULE && !LocalDate.parse(it.date).isAfter(today) }
    val marked = working.count { it.status == DayStatus.PRESENT || it.status == DayStatus.ABSENT || it.status == DayStatus.INCOMPLETE || it.status == DayStatus.CANCELLED }
    val markedFrac = if (working.isEmpty()) 0f else marked.toFloat() / working.size
    val streak = remember(days) { ReportTrendEngine.presentStreak(days) }
    val counts = remember(days) {
        listOf(DayStatus.PRESENT, DayStatus.ABSENT, DayStatus.INCOMPLETE, DayStatus.HOLIDAY, DayStatus.CANCELLED, DayStatus.PENDING)
            .map { s -> s to days.count { it.status == s } }.filter { it.second > 0 }
    }

    GlassCard(p, Modifier.fillMaxWidth(), corner = 28.dp, accent = p.indigo) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).shineSweep(!reduced, 4800, 0.12f),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    RText(ReportAnalyticsEngine.monthLabel(YearMonth.from(first)), 18f, p.text, weight = FontWeight.Bold, display = true, maxLines = 1)
                    RText(
                        if (working.isEmpty()) "No working days yet" else "$marked of ${working.size} working days marked",
                        11f, p.textSec, tnum = true, maxLines = 1
                    )
                    if (streak >= 2) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(p.emerald.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlameGlyph(p.emerald, 12.dp)
                            Spacer(Modifier.width(5.dp))
                            RText("$streak-day streak", 11f, p.emerald, weight = FontWeight.Bold, tnum = true)
                        }
                    }
                }
                if (working.isNotEmpty()) {
                    ProgressRing(markedFrac, 0, 0, p, reduced, ringSize = 52.dp, showTargets = false) {
                        RText("${(markedFrac * 100).toInt()}%", 11f, p.text, weight = FontWeight.Bold, tnum = true)
                    }
                }
            }
            if (counts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counts.forEachIndexed { i, (s, n) ->
                        StatusPill("$n ${dayStatusLabel(s)}", dayStatusColor(s, p), Modifier.popIn(i, reduced, a.range, 60L), size = 11f)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekdayShort.forEachIndexed { i, w ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        RText(w.take(1), 11f, if (i >= 5) p.textMuted.copy(alpha = 0.6f) else p.textSec, weight = FontWeight.Bold)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c - offset
                            if (idx in days.indices) {
                                val d = days[idx]
                                DayCell(
                                    p, d, isToday = d.date == today.toString(), isSelected = d.date == selected,
                                    order = idx + offset, reduced = reduced, key = a.range,
                                    onClick = { onSelect(if (selected == d.date) null else d.date) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
            CalendarLegend(p)
            DayDetailPanel(p, selectedDay, holidayName, reduced)
            HolidayPills(p, a, holidayName, reduced)
        }
    }
}

@Composable
private fun DayCell(
    p: RptPalette,
    d: DayReport,
    isToday: Boolean,
    isSelected: Boolean,
    order: Int,
    reduced: Boolean,
    key: Any,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val color = dayStatusColor(d.status, p)
    val quiet = isQuiet(d.status)
    val filled = d.status == DayStatus.PRESENT || d.status == DayStatus.ABSENT || d.status == DayStatus.HOLIDAY
    val selScale by animateFloatAsState(
        if (isSelected) 1.14f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "cellSel"
    )
    val date = remember(d.date) { LocalDate.parse(d.date) }
    val markedFrac = remember(d) { manualProgress(d).first }
    val numColor = when {
        filled -> Color.White
        quiet -> p.textMuted.copy(alpha = if (d.status == DayStatus.WEEKEND) 0.5f else 0.8f)
        else -> color
    }
    Box(
        modifier
            .aspectRatio(1f)
            .popIn(order, reduced, key, 16L)
            .graphicsLayer { scaleX = selScale; scaleY = selScale }
            .drawBehind {
                val r = size.minDimension / 2f
                val c = center
                when (d.status) {
                    DayStatus.PRESENT, DayStatus.ABSENT, DayStatus.HOLIDAY -> {
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r * 1.5f), radius = r * 1.5f, center = c)
                        drawCircle(
                            Brush.linearGradient(listOf(color.copy(alpha = 0.98f), color.copy(alpha = 0.6f)), start = Offset(0f, 0f), end = Offset(size.width, size.height)),
                            radius = r * 0.92f, center = c
                        )
                    }
                    DayStatus.INCOMPLETE -> {
                        drawCircle(color.copy(alpha = 0.12f), r * 0.92f, c)
                        drawCircle(color.copy(alpha = 0.28f), r * 0.86f, c, style = Stroke(2.dp.toPx()))
                        drawArc(
                            color, -90f, 360f * markedFrac.coerceAtLeast(0.08f), false,
                            Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    DayStatus.CANCELLED -> {
                        drawCircle(color.copy(alpha = 0.75f), r * 0.86f, c, style = Stroke(2.dp.toPx()))
                        drawLine(color.copy(alpha = 0.75f), Offset(c.x - r * 0.5f, c.y + r * 0.5f), Offset(c.x + r * 0.5f, c.y - r * 0.5f), 2.dp.toPx(), StrokeCap.Round)
                    }
                    DayStatus.PENDING -> {
                        drawCircle(
                            p.textSec.copy(alpha = 0.55f), r * 0.86f, c,
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)))
                        )
                    }
                    else -> Unit
                }
                if (isSelected) {
                    drawCircle(if (quiet) p.indigo else Color.White.copy(alpha = 0.95f), r * 1.0f, c, style = Stroke(2.5.dp.toPx()))
                }
            }
            .clip(CircleShape)
            .pressClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RText("${date.dayOfMonth}", 13f, numColor, weight = FontWeight.Bold, tnum = true)
        if (d.status == DayStatus.HOLIDAY) {
            Sparkle(Color.White, reduced, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
        }
        if (isToday) TodayRing(p, reduced, Modifier.matchParentSize())
    }
}

// ------------------------------------------------------------------ week calendar

@Composable
fun WeekCalendarCard(
    p: RptPalette,
    a: ReportAnalytics,
    selected: String?,
    onSelect: (String?) -> Unit,
    today: LocalDate,
    holidayName: (String) -> String?,
    reduced: Boolean,
    nav: WeekNav?
) {
    val days = a.days
    if (days.isEmpty()) return
    val start = LocalDate.parse(days.first().date)
    val end = LocalDate.parse(days.last().date)
    val selectedDay = days.firstOrNull { it.date == selected }
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val counts = remember(days) {
        listOf(DayStatus.PRESENT, DayStatus.ABSENT, DayStatus.INCOMPLETE, DayStatus.HOLIDAY, DayStatus.CANCELLED, DayStatus.PENDING)
            .map { s -> s to days.count { it.status == s } }.filter { it.second > 0 }
    }
    GlassCard(p, Modifier.fillMaxWidth(), corner = 28.dp, accent = p.violet) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).shineSweep(!reduced, 4800, 0.12f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nav != null) CalArrow("\u2039", nav.canPrev, p, nav.onPrev)
                // Title column takes every spare pixel between the two arrows; the range never wraps or truncates.
                Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = start,
                        transitionSpec = {
                            val dir = if (targetState.isAfter(initialState)) 1 else -1
                            (slideInHorizontally(tween(280)) { dir * it / 2 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(280)) { -dir * it / 2 } + fadeOut(tween(120)))
                        },
                        label = "weekLabel"
                    ) { s ->
                        RText("${s.format(fmt)} \u2013 ${s.plusDays(6).format(fmt)}", 15f, p.text, weight = FontWeight.Bold, display = true, maxLines = 1)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (nav != null && !nav.isCurrentWeek) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(p.indigo, p.violet)))
                                .pressClickable { nav.onThisWeek() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) { RText("Jump to this week", 11f, Color.White, weight = FontWeight.Bold, maxLines = 1) }
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(p.indigo.copy(alpha = 0.16f))
                                .border(BorderStroke(1.dp, p.indigo.copy(alpha = 0.35f)), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) { RText("This week", 11f, p.indigo, weight = FontWeight.Bold, maxLines = 1) }
                    }
                }
                if (nav != null) CalArrow("\u203A", nav.canNext, p, nav.onNext)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d ->
                    WeekDayCard(
                        p, d, isToday = d.date == today.toString(), isSelected = d.date == selected,
                        order = i, reduced = reduced, key = start,
                        onClick = { onSelect(if (selected == d.date) null else d.date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (a.hasManualData) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RText("Week attendance", 11f, p.textSec)
                        RText("${fmt1(a.percentage)}%", 12f, p.text, weight = FontWeight.Bold, tnum = true)
                    }
                    GradientProgressBar((a.percentage / 100.0).toFloat(), p, reduced, height = 8.dp)
                }
            }
            if (counts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counts.forEachIndexed { i, (s, n) ->
                        StatusPill("$n ${dayStatusLabel(s)}", dayStatusColor(s, p), Modifier.popIn(i, reduced, start, 60L), size = 11f)
                    }
                }
            }
            DayDetailPanel(p, selectedDay, holidayName, reduced)
        }
    }
}

@Composable
private fun WeekDayCard(
    p: RptPalette,
    d: DayReport,
    isToday: Boolean,
    isSelected: Boolean,
    order: Int,
    reduced: Boolean,
    key: Any,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val color = dayStatusColor(d.status, p)
    val quiet = isQuiet(d.status)
    val lift by animateFloatAsState(if (isSelected) -8f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "lift")
    val (_, pair) = remember(d) { manualProgress(d) }
    val manualAtt = pair.first
    val manualCond = pair.second
    val frac = if (manualCond > 0) manualAtt.toFloat() / manualCond else 0f
    var started by remember(key) { mutableStateOf(reduced) }
    LaunchedEffect(key) { started = true }
    val fill by animateFloatAsState(
        if (started) frac else 0f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow), label = "fill"
    )
    val shape = RoundedCornerShape(20.dp)
    val date = remember(d.date) { LocalDate.parse(d.date) }
    val dots = remember(d) { d.sessions.filter { !it.subject.isAutoPresent } }
    Box(
        modifier
            .popIn(order, reduced, key, 60L)
            .graphicsLayer { translationY = lift.dp.toPx() }
            .clip(shape)
            .background(
                if (quiet) Brush.verticalGradient(listOf(p.trackBg.copy(alpha = 0.5f), p.trackBg.copy(alpha = 0.2f)))
                else Brush.verticalGradient(listOf(color.copy(alpha = if (isSelected) 0.42f else 0.22f), color.copy(alpha = 0.05f)))
            )
            .border(BorderStroke(if (isSelected) 2.dp else 1.dp, if (quiet) Color.Transparent else color.copy(alpha = if (isSelected) 0.95f else 0.3f)), shape)
            .pressClickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RText(weekdayShort[d.dayOfWeek - 1], 11f, p.textSec, weight = FontWeight.SemiBold)
            RText("${date.dayOfMonth}", 18f, if (quiet) p.textMuted else p.text, weight = FontWeight.Bold, display = true, tnum = true)
            Box(
                Modifier.size(26.dp).popIn(order + 5, reduced, key, 60L).clip(CircleShape).background(color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { RText(dayGlyph(d.status), 13f, color, weight = FontWeight.Bold) }
            if (dots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    dots.take(6).forEach { s ->
                        val dc = statusColor(s.status, p)
                        val hollow = s.status == ReportSessionStatus.PENDING || s.status == ReportSessionStatus.UPCOMING
                        Box(
                            Modifier.size(5.dp).clip(CircleShape)
                                .then(if (hollow) Modifier.border(BorderStroke(1.dp, dc.copy(alpha = 0.7f)), CircleShape) else Modifier.background(dc))
                        )
                    }
                }
            }
            Box(
                Modifier.width(10.dp).height(46.dp).clip(RoundedCornerShape(50)).background(p.trackBg),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (manualCond > 0) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fill.coerceIn(0f, 1f)).clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f))))
                    )
                }
            }
            RText(if (manualCond > 0) "$manualAtt/$manualCond" else "\u00B7", 11f, p.textSec, tnum = true, maxLines = 1)
        }
        if (isToday) TodayPulseBorder(p, reduced, 20.dp, Modifier.matchParentSize())
    }
}
