package com.example.ui.pdf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.pdf.PdfConfig
import com.example.data.pdf.PdfConfigResolver
import com.example.data.pdf.PdfPeriodType
import com.example.data.pdf.PdfReportType
import com.example.data.reports.ArchiveEntry
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.ui.reports.GlassCard
import com.example.ui.reports.RText
import com.example.ui.reports.RptPalette
import com.example.ui.reports.StatusPill
import com.example.ui.reports.popIn
import com.example.ui.reports.pressClickable
import com.example.ui.reports.shineSweep
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

/** Period + report-type selection with only the controls relevant to the chosen period. */
@Composable
fun PdfConfigScreen(
    p: RptPalette,
    config: PdfConfig,
    archive: List<ArchiveEntry>,
    earliestWeek: LocalDate,
    today: LocalDate,
    haptic: () -> Unit,
    onChange: ((PdfConfig) -> PdfConfig) -> Unit,
    onGenerate: () -> Unit,
    reduced: Boolean = false
) {
    val (range, error) = PdfConfigResolver.resolve(config, today)
    val latestWeek = ReportDateRange.weekContaining(today).start
    val running = config.periodType != PdfPeriodType.CUSTOM && range != null && !range.end.isBefore(today)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0 — hero
        Box(Modifier.staggerIn(0, reduced)) { HeroCard(p, reduced) }

        // 1 — period cards
        Column(Modifier.staggerIn(1, reduced), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(p, "Report period")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionCard(p, "Weekly", "Mon – Sun", Icons.Default.ViewWeek, config.periodType == PdfPeriodType.WEEKLY, Modifier.weight(1f)) {
                    haptic(); onChange { it.copy(periodType = PdfPeriodType.WEEKLY) }
                }
                OptionCard(p, "Monthly", "Whole month", Icons.Default.CalendarMonth, config.periodType == PdfPeriodType.MONTHLY, Modifier.weight(1f)) {
                    haptic(); onChange { it.copy(periodType = PdfPeriodType.MONTHLY) }
                }
                OptionCard(p, "Custom", "Pick dates", Icons.Default.DateRange, config.periodType == PdfPeriodType.CUSTOM, Modifier.weight(1f)) {
                    haptic(); onChange { it.copy(periodType = PdfPeriodType.CUSTOM) }
                }
            }
        }

        // 2 — the controls for the chosen period, sliding in the direction of the choice
        Box(Modifier.staggerIn(2, reduced)) {
            AnimatedContent(
                targetState = config.periodType,
                transitionSpec = {
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(300, 70)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { dir * it / 4 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -dir * it / 5 }) using
                        SizeTransform(clip = false)
                },
                label = "periodPanel"
            ) { type ->
                when (type) {
                    PdfPeriodType.WEEKLY -> WeekPicker(p, config.weekStart ?: latestWeek, earliestWeek, latestWeek, haptic, reduced) { ws ->
                        onChange { it.copy(weekStart = ws) }
                    }
                    PdfPeriodType.MONTHLY -> MonthPicker(p, archive, config.monthId, haptic, reduced) { id ->
                        onChange { it.copy(monthId = id) }
                    }
                    PdfPeriodType.CUSTOM -> CustomPicker(p, config, today, error) { s, e ->
                        onChange { it.copy(customStart = s, customEnd = e) }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = running,
            enter = fadeIn(tween(260)) + expandVertically(tween(300)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(220))
        ) {
            RText(
                "This period is still running, so the report is labelled “To date” and only counts classes up to today.",
                11f, p.textSec
            )
        }

        // 3 — report type
        Column(Modifier.staggerIn(3, reduced), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(p, "Report type")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionCard(p, "Summary", "Overall %, targets, subjects", Icons.Default.Description, config.reportType == PdfReportType.SUMMARY, Modifier.weight(1f)) {
                    haptic(); onChange { it.copy(reportType = PdfReportType.SUMMARY) }
                }
                OptionCard(p, "Detailed", "Plus date-wise records", Icons.Default.TableChart, config.reportType == PdfReportType.DETAILED, Modifier.weight(1f)) {
                    haptic(); onChange { it.copy(reportType = PdfReportType.DETAILED) }
                }
            }
        }

        // 4 — live summary of what will be generated (animates whenever a choice changes)
        Box(Modifier.staggerIn(4, reduced)) {
            YourReportStrip(p, config, range, error, reduced)
        }

        // 5 — call to action
        Box(Modifier.staggerIn(5, reduced)) {
            PdfGenerateButton(p, "Generate Report", Icons.Default.PictureAsPdf, enabled = error == null, reduced = reduced, modifier = Modifier.fillMaxWidth()) {
                haptic(); onGenerate()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroCard(p: RptPalette, reduced: Boolean) {
    GlassCard(p, Modifier.fillMaxWidth(), corner = 24.dp, accent = p.violet) {
        Row(
            Modifier.shineSweep(enabled = !reduced, periodMs = 5200, strength = 0.12f).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).floating(reduced).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(p.indigo, p.violet, p.cyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                RText("Attendance report", 16f, p.text, weight = FontWeight.Bold, display = true)
                RText("Pick a period, preview the real PDF, then save or share.", 11.5f, p.textSec)
            }
            Icon(Icons.Default.AutoAwesome, null, tint = p.cyan, modifier = Modifier.size(20.dp).floating(reduced, 4.dp, 2100))
        }
    }
}

@Composable
private fun SectionLabel(p: RptPalette, text: String) =
    RText(text.uppercase(), 11f, p.textSec, weight = FontWeight.Bold, letterSpacing = 1f)

/** Selectable card with animated colour, spring scale and a check badge that pops in. */
@Composable
private fun OptionCard(
    p: RptPalette, title: String, sub: String, icon: ImageVector, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val tone by animateColorAsState(if (selected) p.cyan else p.border, tween(260), label = "tone")
    val tint by animateFloatAsState(if (selected) 1f else 0f, tween(320), label = "tint")
    val scale by animateFloatAsState(
        if (selected) 1f else 0.975f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "scale"
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(p.card)
            .background(Brush.linearGradient(listOf(p.cyan.copy(alpha = 0.20f * tint), p.violet.copy(alpha = 0.10f * tint))))
            .border(BorderStroke(1.dp, tone), shape)
            .pressClickable { onClick() }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (selected) p.cyan else p.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(160)),
                    exit = scaleOut(tween(140)) + fadeOut(tween(120))
                ) { Icon(Icons.Default.CheckCircle, "Selected", tint = p.cyan, modifier = Modifier.size(18.dp)) }
            }
            RText(title, 14f, if (selected) p.text else p.textSec, weight = FontWeight.Bold, display = true)
            RText(sub, 10.5f, if (selected) p.textSec else p.textMuted)
        }
    }
}

@Composable
private fun YourReportStrip(p: RptPalette, config: PdfConfig, range: ReportDateRange?, error: String?, reduced: Boolean) {
    val kind = if (config.reportType == PdfReportType.SUMMARY) "Summary" else "Detailed"
    val text = when {
        error != null -> error
        range == null -> "Choose a period"
        config.periodType == PdfPeriodType.MONTHLY -> "${ReportAnalyticsEngine.monthLabel(YearMonth.from(range.start))} · $kind"
        else -> "${range.start.format(dayFmt)} – ${range.end.format(dateFmt)} · $kind"
    }
    val isError = error != null
    val tone = if (isError) p.rose else p.cyan
    GlassCard(p, Modifier.fillMaxWidth(), corner = 18.dp, accent = tone) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Default.DateRange else Icons.Default.AutoAwesome, null, tint = tone, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                RText(if (isError) "Check your selection" else "Your report", 10f, p.textSec, weight = FontWeight.Bold, letterSpacing = 0.8f)
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (fadeIn(tween(260, 60)) + slideInVertically(tween(300)) { it / 2 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 2 })
                    },
                    label = "stripText"
                ) { t -> RText(t, 13.5f, if (isError) p.rose else p.text, weight = FontWeight.Bold, display = true) }
            }
        }
    }
}

@Composable
private fun WeekPicker(
    p: RptPalette, weekStart: LocalDate, earliest: LocalDate, latest: LocalDate,
    haptic: () -> Unit, reduced: Boolean, onPick: (LocalDate) -> Unit
) {
    val canPrev = weekStart.isAfter(earliest)
    val canNext = weekStart.isBefore(latest)
    GlassCard(p, Modifier.fillMaxWidth(), corner = 20.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(p, canPrev, left = true) { haptic(); onPick(weekStart.minusWeeks(1)) }
            AnimatedContent(
                targetState = weekStart,
                modifier = Modifier.weight(1f).clipToBounds(),
                transitionSpec = {
                    val dir = if (targetState.isAfter(initialState)) 1 else -1
                    if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it / 2 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -dir * it / 2 })
                },
                label = "weekLabel"
            ) { ws ->
                val r = ReportDateRange.weekContaining(ws)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RText("${r.start.format(dayFmt)} – ${r.end.format(dateFmt)}", 14f, p.text, weight = FontWeight.Bold, display = true)
                    if (ws == latest) StatusPill("This week · To date", p.cyan, Modifier.padding(top = 6.dp), size = 10f)
                }
            }
            StepButton(p, canNext, left = false) { haptic(); onPick(weekStart.plusWeeks(1)) }
        }
    }
}

@Composable
private fun StepButton(p: RptPalette, enabled: Boolean, left: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (enabled) 1f else 0.3f, tween(200), label = "stepAlpha")
    Box(
        Modifier.size(40.dp).alpha(a).clip(CircleShape).background(p.trackBg)
            .pressClickable(enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (left) "Previous week" else "Next week", tint = p.text
        )
    }
}

@Composable
private fun MonthPicker(
    p: RptPalette, archive: List<ArchiveEntry>, selected: String?,
    haptic: () -> Unit, reduced: Boolean, onPick: (String) -> Unit
) {
    val months = archive.sortedByDescending { it.monthId }
    if (months.isEmpty()) {
        RText("No months with attendance yet.", 12f, p.textSec)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        months.forEachIndexed { i, m ->
            val sel = m.monthId == selected
            val bg by animateColorAsState(if (sel) p.cyan.copy(alpha = 0.20f) else p.trackBg, tween(240), label = "chipBg")
            val bd by animateColorAsState(if (sel) p.cyan.copy(alpha = 0.7f) else p.border, tween(240), label = "chipBd")
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.popIn(i, reduced).clip(shape).background(bg)
                    .border(BorderStroke(1.dp, bd), shape)
                    .pressClickable { haptic(); onPick(m.monthId) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                RText(
                    ReportAnalyticsEngine.monthLabel(m.monthId, short = true) + if (m.isCurrentMonth) " · To date" else "",
                    12f, if (sel) p.text else p.textSec, weight = FontWeight.SemiBold, maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPicker(
    p: RptPalette, config: PdfConfig, today: LocalDate, error: String?, onPick: (LocalDate?, LocalDate?) -> Unit
) {
    var editing by remember { mutableStateOf<String?>(null) } // "start" | "end"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(p, "Start date", config.customStart, Modifier.weight(1f)) { editing = "start" }
            DateField(p, "End date", config.customEnd, Modifier.weight(1f)) { editing = "end" }
        }
        RText("Dates after today can't be selected.", 11f, p.textMuted)
    }
    editing?.let { which ->
        val initial = (if (which == "start") config.customStart else config.customEnd) ?: today
        val selectable = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = selectable
        )
        DatePickerDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "start") onPick(d, config.customEnd) else onPick(config.customStart, d)
                    }
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateField(p: RptPalette, label: String, value: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(p, modifier.pressClickable { onClick() }, corner = 18.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RText(label, 10.5f, p.textSec)
                AnimatedContent(
                    targetState = value?.format(dateFmt) ?: "Select",
                    transitionSpec = {
                        (fadeIn(tween(240, 60)) + slideInVertically(tween(280)) { it / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 2 })
                    },
                    label = "dateText"
                ) { t -> RText(t, 13.5f, p.text, weight = FontWeight.Bold, display = true) }
            }
            Icon(Icons.Default.CalendarToday, null, tint = p.cyan, modifier = Modifier.size(18.dp))
        }
    }
}
