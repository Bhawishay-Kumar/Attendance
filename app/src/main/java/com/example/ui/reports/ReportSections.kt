package com.example.ui.reports

import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.SubjectCategory
import com.example.data.reports.ForecastResult
import com.example.data.reports.Insight
import com.example.data.reports.InsightSeverity
import com.example.data.reports.ReportAnalytics
import com.example.data.reports.ReportTrendEngine
import com.example.data.reports.SubjectReport
import com.example.data.reports.TrendPoint
import com.example.data.reports.TrendWindow
import com.example.data.reports.ReportTimeframe
import java.time.LocalDate

// ================================================================ OVERVIEW

/** Subjects that most need attention first (below exam, then below stipend, then lowest %). */
fun priorityPreview(a: ReportAnalytics, n: Int = 3): List<SubjectReport> {
    val withData = a.subjects.filter { it.conductedCredits > 0 }
    val manual = withData.filter { !it.subject.isAutoPresent }
    val pool = if (manual.size >= n) manual else withData
    val rank = { r: SubjectReport ->
        when {
            r.percentage < a.examTarget -> 0
            r.percentage < a.stipendTarget -> 1
            else -> 2
        }
    }
    return pool.sortedWith(compareBy<SubjectReport> { rank(it) }.thenBy { it.percentage }).take(n)
}

@Composable
fun OverviewSection(
    p: RptPalette,
    a: ReportAnalytics,
    timeframe: ReportTimeframe,
    reducedMotion: Boolean,
    today: LocalDate,
    selectedDate: String?,
    onSelectDate: (String?) -> Unit,
    holidayName: (String) -> String?,
    weekNav: WeekNav?,
    onViewAll: () -> Unit,
    onOpenSubject: (String) -> Unit,
    onOpenMonthSummary: (() -> Unit)?,
    onOpenPdf: (String) -> Unit = {}
) {
    val key = a.range
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.entrance(0, reducedMotion, key)) { HeroCard(p, a, reducedMotion, onOpenMonthSummary) }
        ReportStatTiles(p, a, if (timeframe == ReportTimeframe.WEEKLY) "Weekly" else "Monthly", reducedMotion)
        Row(Modifier.fillMaxWidth().entrance(2, reducedMotion, key), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EligibilityTile(p, "Exam (${a.examTarget}%)", a.isExamEligible, a.hasData, a.safeSkipHoursExam, a.recoveryHoursExam, { c -> CapGlyph(c, 22.dp) }, Modifier.weight(1f))
            EligibilityTile(p, "Stipend (${a.stipendTarget}%)", a.isStipendEligible, a.hasData, a.safeSkipHoursStipend, a.recoveryHoursStipend, { c -> CoinGlyph(c, 22.dp) }, Modifier.weight(1f))
        }
        Box(Modifier.entrance(3, reducedMotion, key)) { AutoPresentStrip(p, a) }
        if (timeframe == ReportTimeframe.WEEKLY) {
            WeekCalendarCard(p, a, selectedDate, onSelectDate, today, holidayName, reducedMotion, weekNav)
        } else {
            MonthCalendarCard(p, a, selectedDate, onSelectDate, today, holidayName, reducedMotion)
        }
        com.example.ui.pdf.PdfReportEntryCard(p, a, onOpenPdf)
        SectionTitle(p, "Subject-Wise Analysis", "Quick status check across enrolled subjects", "View All (${a.subjects.size})", onViewAll)
        val preview = priorityPreview(a)
        if (preview.isEmpty()) {
            EmptyNote(p, "No subject has confirmed hours in this period yet.")
        } else {
            preview.forEachIndexed { i, r ->
                Box(Modifier.entrance(i, reducedMotion, key)) {
                    SubjectRow(p, a, r, expanded = false, onClick = { onOpenSubject(r.subject.code) }, periodLabel = "", showAllHistory = false, onToggleHistory = {})
                }
            }
        }
    }
}

@Composable
fun EmptyNote(p: RptPalette, text: String) {
    GlassCard(p, Modifier.fillMaxWidth(), corner = 18.dp) {
        Box(Modifier.padding(16.dp)) { RText(text, 12f, p.textMuted) }
    }
}

// ================================================================ SUBJECTS

fun matchesFilter(r: SubjectReport, f: SubjectCategory): Boolean = when (f) {
    SubjectCategory.ALL -> true
    SubjectCategory.MAJOR -> r.subject.category == SubjectCategory.MAJOR && !r.subject.isAutoPresent
    SubjectCategory.MINOR -> r.subject.category == SubjectCategory.MINOR
    SubjectCategory.LIBRARY -> r.subject.isAutoPresent || r.subject.category == SubjectCategory.LIBRARY
}

private fun recoveryWeight(r: SubjectReport, a: ReportAnalytics): Int {
    if (r.conductedCredits == 0) return 0
    val h = when {
        r.percentage < a.examTarget -> r.recoveryHoursExam
        r.percentage < a.stipendTarget -> r.recoveryHoursStipend
        else -> 0
    }
    return if (h < 0) Int.MAX_VALUE / 2 else h
}

fun sortSubjects(list: List<SubjectReport>, sort: SubjectSort, a: ReportAnalytics): List<SubjectReport> {
    if (sort == SubjectSort.ALPHA) return list.sortedBy { it.subject.shortName.lowercase() }
    val (data, none) = list.partition { it.conductedCredits > 0 }
    val sorted = when (sort) {
        SubjectSort.HIGHEST -> data.sortedByDescending { it.percentage }
        SubjectSort.LOWEST -> data.sortedBy { it.percentage }
        SubjectSort.RECOVERY -> data.sortedByDescending { recoveryWeight(it, a) }
        SubjectSort.ALPHA -> data
    }
    return sorted + none
}

@Composable
fun SubjectsSection(
    p: RptPalette,
    a: ReportAnalytics,
    periodLabel: String,
    filter: SubjectCategory,
    onFilter: (SubjectCategory) -> Unit,
    sort: SubjectSort,
    onSort: (SubjectSort) -> Unit,
    expandedCode: String?,
    onExpand: (String?) -> Unit,
    historyAll: Set<String>,
    onToggleHistory: (String) -> Unit,
    reducedMotion: Boolean
) {
    val filters = listOf(
        SubjectCategory.ALL to "All",
        SubjectCategory.MAJOR to "Major",
        SubjectCategory.MINOR to "Minor/Honours",
        SubjectCategory.LIBRARY to "Library & Auto"
    )
    val visible = sortSubjects(a.subjects.filter { matchesFilter(it, filter) }, sort, a)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(p, "Subjects", "${visible.size} of ${a.subjects.size} enrolled subjects · $periodLabel")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { (f, label) ->
                val count = a.subjects.count { matchesFilter(it, f) }
                FilterChip(p, "$label ($count)", filter == f) { onFilter(f) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RText("Sort", 11f, p.textMuted, weight = FontWeight.Bold)
            SubjectSort.values().forEach { s -> FilterChip(p, s.label, sort == s) { onSort(s) } }
        }
        if (visible.isEmpty()) {
            EmptyNote(p, "No subjects match this filter.")
        } else {
            visible.forEachIndexed { i, r ->
                androidx.compose.runtime.key(r.subject.code) {
                    Box(Modifier.entrance(i, reducedMotion, a.range)) {
                        SubjectRow(
                            p, a, r,
                            expanded = expandedCode == r.subject.code,
                            onClick = { onExpand(if (expandedCode == r.subject.code) null else r.subject.code) },
                            periodLabel = periodLabel,
                            showAllHistory = r.subject.code in historyAll,
                            onToggleHistory = { onToggleHistory(r.subject.code) }
                        )
                    }
                }
            }
        }
    }
}

// ================================================================ TRENDS

@Composable
fun TrendsSection(
    p: RptPalette,
    window: TrendWindow,
    onWindow: (TrendWindow) -> Unit,
    windowLabel: String,
    windowAnalytics: ReportAnalytics,
    semesterPoints: List<TrendPoint>,
    deltaPoints: Double?,
    previousLabel: String,
    reducedMotion: Boolean
) {
    val labels = listOf(
        TrendWindow.LAST_7_DAYS to "7 Days",
        TrendWindow.LAST_30_DAYS to "30 Days",
        TrendWindow.THIS_PERIOD to "This Period"
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { (w, l) -> FilterChip(p, l, window == w) { onWindow(w) } }
        }
        RText(windowLabel, 11f, p.textSec)

        GlassCard(p, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RText("Attendance Trajectory", 14f, p.text, weight = FontWeight.Bold, display = true)
                RText("Running weighted attendance over recorded days", 11f, p.textSec)
                TrendLineChart(ReportTrendEngine.trajectory(windowAnalytics), windowAnalytics.examTarget, windowAnalytics.stipendTarget, p, reducedMotion)
                if (deltaPoints != null) {
                    val up = deltaPoints >= 0
                    StatusPill("${if (up) "+" else ""}${fmt1(deltaPoints)} pts vs $previousLabel", if (up) p.emerald else p.rose)
                }
            }
        }
        GlassCard(p, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RText("Semester Progress Curve", 14f, p.text, weight = FontWeight.Bold, display = true)
                RText("Cumulative attendance since your first record", 11f, p.textSec)
                TrendLineChart(semesterPoints, windowAnalytics.examTarget, windowAnalytics.stipendTarget, p, reducedMotion)
            }
        }
        GlassCard(p, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RText("Subject Comparison", 14f, p.text, weight = FontWeight.Bold, display = true)
                SubjectComparisonBars(ReportTrendEngine.subjectComparison(windowAnalytics), windowAnalytics.examTarget, windowAnalytics.stipendTarget, p, reducedMotion)
            }
        }
        GlassCard(p, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RText("Attendance Breakdown", 14f, p.text, weight = FontWeight.Bold, display = true)
                BreakdownChart(ReportTrendEngine.breakdown(windowAnalytics), p, reducedMotion)
                RText("No Class sessions are shown separately and never count as absences.", 10f, p.textMuted)
            }
        }
    }
}

// ================================================================ INSIGHTS

private fun severityColor(s: InsightSeverity, p: RptPalette): Color = when (s) {
    InsightSeverity.POSITIVE -> p.emerald
    InsightSeverity.INFO -> p.indigo
    InsightSeverity.WARNING -> p.amber
    InsightSeverity.CRITICAL -> p.rose
}

fun insightsNeedAttention(list: List<Insight>): Boolean =
    list.any { it.severity == InsightSeverity.CRITICAL || it.severity == InsightSeverity.WARNING }

@Composable
fun InsightsSection(
    p: RptPalette,
    a: ReportAnalytics,
    insights: List<Insight>,
    actionPlan: List<String>,
    forecast: ForecastResult?,
    reducedMotion: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(p, "Insights", "Rule-based and computed from your recorded attendance")
        if (insights.isEmpty()) {
            EmptyNote(p, "Not enough confirmed attendance in this period to generate insights yet.")
        }
        insights.filter { !it.id.startsWith("forecast") }.forEachIndexed { idx, i ->
            val c = severityColor(i.severity, p)
            GlassCard(p, Modifier.fillMaxWidth().entrance(idx, reducedMotion, a.range), corner = 18.dp, accent = c) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    RText(i.icon, 18f, c)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        RText(i.title, 13f, p.text, weight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        RText(i.detail, 12f, p.textSec)
                    }
                }
            }
        }
        if (actionPlan.isNotEmpty()) {
            GlassCard(p, Modifier.fillMaxWidth(), corner = 20.dp, accent = p.indigo) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RText("Recommended Action Plan", 14f, p.text, weight = FontWeight.Bold, display = true)
                    actionPlan.forEachIndexed { idx, s ->
                        Row(verticalAlignment = Alignment.Top) {
                            RText("${idx + 1}.", 12f, p.indigo, weight = FontWeight.Bold, tnum = true, modifier = Modifier.width(22.dp))
                            RText(s, 12f, p.textSec)
                        }
                    }
                }
            }
        }
        ForecastCard(p, a, forecast)
    }
}

@Composable
fun ForecastCard(p: RptPalette, a: ReportAnalytics, f: ForecastResult?) {
    GlassCard(p, Modifier.fillMaxWidth(), corner = 20.dp, accent = p.violet) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("Attendance Forecast", 14f, p.text, weight = FontWeight.Bold, display = true, modifier = Modifier.weight(1f))
                StatusPill("Estimate", p.violet, size = 10f)
            }
            if (f == null) {
                RText(
                    if (!a.hasData) "A forecast needs at least one confirmed session."
                    else "No forecast: this period has ended or has no scheduled unrecorded classes left.",
                    12f, p.textMuted
                )
            } else {
                RText("${shortDate(f.windowStart)} – ${shortDate(f.windowEnd)} · ${f.remainingHours} scheduled hr(s) in ${f.remainingSessions} session(s), excluding auto-credited ones", 11f, p.textSec, tnum = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ForecastTile(p, "If you attend all", "${fmt1(f.ifAttendAllPercentage)}%", p.emerald, Modifier.weight(1f))
                    ForecastTile(p, "If you miss all", "${fmt1(f.ifMissAllPercentage)}%", p.rose, Modifier.weight(1f))
                }
                RText(
                    "Max you can miss and still finish ≥ ${a.stipendTarget}%: ${f.missBudgetStipend?.let { "$it hr(s)" } ?: "not reachable"}  ·  ≥ ${a.examTarget}%: ${f.missBudgetExam?.let { "$it hr(s)" } ?: "not reachable"}",
                    11f, p.textSec, tnum = true
                )
                RText("Unrecorded future classes are never counted as present. Nothing here changes your records.", 10f, p.textMuted)
            }
        }
    }
}

@Composable
private fun ForecastTile(p: RptPalette, label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp)) {
        RText(label, 10f, p.textSec)
        RText(value, 20f, color, weight = FontWeight.Bold, display = true, tnum = true)
    }
}

// ================================================================ SMART & PDF

enum class SmartTool { PREDICTOR, WHAT_IF, BUNK, RECOVERY, CALENDAR }

@Composable
fun SmartSection(
    p: RptPalette,
    analytics: ReportAnalytics? = null,
    onOpenPdf: (String) -> Unit = {},
    onOpen: (SmartTool) -> Unit
) {
    val tools = listOf(
        Triple(SmartTool.PREDICTOR, "🔮" to "Attendance Predictor", "Two-week estimate from your timetable"),
        Triple(SmartTool.WHAT_IF, "🧪" to "What-If Simulator", "Simulate attending or missing days"),
        Triple(SmartTool.BUNK, "🏖️" to "Bunk Planner", "Top 3 safest full-day breaks"),
        Triple(SmartTool.RECOVERY, "🛠️" to "Recovery Planner", "Hours needed per subject and target"),
        Triple(SmartTool.CALENDAR, "📅" to "Academic Calendar", "Official AKTU holidays")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        com.example.ui.pdf.PdfReportEntryCard(p, analytics, onOpenPdf)
        SectionTitle(p, "Smart Tools", "Opens the real tools built into this app")
        tools.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (tool, titleIcon, sub) ->
                    GlassCard(p, Modifier.weight(1f).pressClickable { onOpen(tool) }, corner = 20.dp) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RText(titleIcon.first, 22f, p.text)
                            RText(titleIcon.second, 13f, p.text, weight = FontWeight.Bold)
                            RText(sub, 10f, p.textSec)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ================================================================ DIALOGS

@Composable
fun RecoveryPlannerDialog(p: RptPalette, a: ReportAnalytics, periodLabel: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(p.copyOpaque(), Modifier.fillMaxWidth().padding(8.dp), corner = 24.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RText("🛠️ Recovery Planner", 17f, p.text, weight = FontWeight.Bold, display = true)
                RText("Consecutive weighted hours you must attend to reach each target · $periodLabel", 11f, p.textSec)
                if (!a.hasData) {
                    RText("No confirmed attendance in this period yet.", 12f, p.textMuted)
                } else {
                    PlannerLine(p, "Overall", a.percentage, a.examTarget, a.stipendTarget, a.recoveryHoursExam, a.recoveryHoursStipend)
                    a.subjects.filter { it.conductedCredits > 0 && !it.subject.isAutoPresent }.sortedBy { it.percentage }.forEach {
                        PlannerLine(p, it.subject.shortName, it.percentage, a.examTarget, a.stipendTarget, it.recoveryHoursExam, it.recoveryHoursStipend)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.indigo).pressClickable { onDismiss() }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { RText("Close", 13f, Color.White, weight = FontWeight.Bold) }
            }
        }
    }
}

/** Dialog surfaces need an opaque card colour so text stays readable over the dim scrim. */
private fun RptPalette.copyOpaque(): RptPalette =
    RptPalette(isDark, bg, if (isDark) Color(0xFF121826) else com.example.ui.theme.LightPearl, cardAlt, border, text, textSec, textMuted, indigo, violet, cyan, emerald, amber, rose, trackBg)

@Composable
private fun PlannerLine(p: RptPalette, name: String, pct: Double, exam: Int, stip: Int, recExam: Int, recStip: Int) {
    fun t(h: Int, met: Boolean) = if (met) "Met" else if (h < 0) "Not reachable" else "$h hr(s)"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.trackBg).padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RText(name, 12f, p.text, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            RText("${fmt1(pct)}%", 12f, p.textSec, tnum = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RText("To $exam%: ${t(recExam, pct >= exam)}", 11f, if (pct >= exam) p.emerald else p.amber, tnum = true)
            RText("To $stip%: ${t(recStip, pct >= stip)}", 11f, if (pct >= stip) p.emerald else p.amber, tnum = true)
        }
    }
}
