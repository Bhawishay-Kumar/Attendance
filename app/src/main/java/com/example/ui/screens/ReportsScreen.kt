package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.holidays.AktuHolidays
import com.example.data.model.MonthlyAnalytics
import com.example.data.model.SubjectCategory
import com.example.data.reports.ReportAnalytics
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.reports.ReportInsightsEngine
import com.example.data.reports.ReportTimeframe
import com.example.data.reports.ReportTrendEngine
import com.example.data.reports.TrendWindow
import com.example.ui.reports.EmptyNote
import com.example.ui.reports.InsightsSection
import com.example.ui.reports.MonthArchiveRibbon
import com.example.ui.reports.OverviewSection
import com.example.ui.reports.WeekNav
import com.example.ui.reports.rememberReportHaptics
import com.example.ui.reports.RecoveryPlannerDialog
import com.example.ui.reports.ReportSection
import com.example.ui.reports.SectionTabs
import com.example.ui.reports.SmartSection
import com.example.ui.reports.SmartTool
import com.example.ui.reports.SubjectSort
import com.example.ui.reports.SubjectsSection
import com.example.ui.reports.TimeframeSegmented
import com.example.ui.reports.TrendsSection
import com.example.ui.components.AppScreenHeader
import com.example.ui.reports.insightsNeedAttention
import com.example.ui.reports.rememberReducedMotion
import com.example.ui.reports.rptPalette
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AttendanceViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

private fun periodLabel(tf: ReportTimeframe, r: ReportDateRange): String =
    if (tf == ReportTimeframe.MONTHLY) ReportAnalyticsEngine.monthLabel(YearMonth.from(r.start))
    else "${r.start.format(dayFmt)} – ${r.end.format(dayYearFmt)}"

/**
 * Reports & Analytics. All numbers come from [ReportAnalyticsEngine] (a read-only view over the
 * app's attendance records, holidays and profile targets); this screen only holds UI state.
 */
@Composable
fun ReportsScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val p = remember(glass.isDark) { rptPalette(glass.isDark) }
    val reduced = rememberReducedMotion()
    val repo = viewModel.repository

    val records by repo.recordsMap.collectAsState()
    val holidays by repo.holidayOverrides.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val selectedMonthId by viewModel.selectedMonthId.collectAsState()
    val showMonthEnd by viewModel.showMonthEndSummaryDialog.collectAsState()
    val today = AttendanceViewModel.liveToday()

    var timeframe by rememberSaveable { mutableStateOf(ReportTimeframe.MONTHLY) }
    var section by rememberSaveable { mutableStateOf(ReportSection.OVERVIEW) }
    var weekAnchor by rememberSaveable { mutableStateOf(ReportDateRange.weekContaining(today).start) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var subjectFilter by rememberSaveable { mutableStateOf(SubjectCategory.ALL) }
    var subjectSort by rememberSaveable { mutableStateOf(SubjectSort.LOWEST) }
    var expandedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var trendWindow by rememberSaveable { mutableStateOf(TrendWindow.THIS_PERIOD) }
    var historyAll by remember { mutableStateOf(setOf<String>()) }
    var activeTool by remember { mutableStateOf<SmartTool?>(null) }

    val currentMonthRange = remember(today) {
        ReportDateRange.ofMonth(YearMonth.from(today).toString()) ?: ReportDateRange.weekContaining(today)
    }

    val isHoliday: (String) -> Boolean = remember(repo) { repo::isDateHoliday }
    // Only months with real attendance (plus the current month) are ever offered.
    val archive = remember(records, holidays, profile, today) {
        ReportAnalyticsEngine.archiveMonths(records, isHoliday, profile, today)
    }
    // A previously selected month that has no data (and is not the current month) is never shown.
    val effectiveMonthId = if (archive.any { it.monthId == selectedMonthId }) selectedMonthId else currentMonthRange.start.toString().take(7)
    val range: ReportDateRange = when (timeframe) {
        ReportTimeframe.MONTHLY -> ReportDateRange.ofMonth(effectiveMonthId) ?: currentMonthRange
        ReportTimeframe.WEEKLY -> ReportDateRange.weekContaining(weekAnchor)
    }
    val holidayName: (String) -> String? = remember { { AktuHolidays.getHoliday(it)?.name } }
    val earliestWeek = remember(records, today) {
        val first = records.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(today) }.minOrNull() ?: today
        ReportDateRange.weekContaining(first).start
    }
    val latestWeek = remember(today) { ReportDateRange.weekContaining(today).start }

    val effRange = range
    val analytics: ReportAnalytics = remember(effRange, records, holidays, profile, today) {
        ReportAnalyticsEngine.analyze(effRange, records, isHoliday, profile, today)
    }
    val previous: ReportAnalytics = remember(effRange, records, holidays, profile, today) {
        ReportAnalyticsEngine.analyze(effRange.previousComparable(), records, isHoliday, profile, today)
    }
    val forecast = remember(analytics, records, holidays, profile, today) {
        ReportInsightsEngine.forecast(analytics, records, isHoliday, profile, today)
    }
    val insights = remember(analytics, previous, forecast) {
        ReportInsightsEngine.generate(analytics, previous, forecast)
    }
    val actionPlan = remember(analytics, forecast) {
        ReportInsightsEngine.actionPlan(analytics, forecast)
    }

    val windowRange = remember(trendWindow, effRange, today) {
        ReportTrendEngine.windowRange(trendWindow, effRange, today)
    }
    val windowAnalytics: ReportAnalytics = remember(windowRange, records, holidays, profile, today) {
        ReportAnalyticsEngine.analyze(windowRange, records, isHoliday, profile, today)
    }
    val anchor = if (effRange.end.isAfter(today)) today else effRange.end
    val semesterPoints = remember(anchor, records, holidays, profile, today) {
        ReportTrendEngine.semesterRange(records, anchor)?.let {
            ReportTrendEngine.trajectory(ReportAnalyticsEngine.analyze(it, records, isHoliday, profile, today))
        } ?: emptyList()
    }

    val currentMonthAnalytics = remember(records, holidays, profile, today) {
        repo.getMonthlyAnalytics(YearMonth.from(today).toString())
    }
    val label = periodLabel(timeframe, range)

    LaunchedEffect(range, today) {
        selectedDate = today.toString().takeIf { today in range }
    }

    val haptic = rememberReportHaptics(profile.hapticFeedbackEnabled)
    val weekNav = if (timeframe == ReportTimeframe.WEEKLY) {
        WeekNav(
            canPrev = weekAnchor.isAfter(earliestWeek),
            canNext = weekAnchor.isBefore(latestWeek),
            isCurrentWeek = weekAnchor == latestWeek,
            onPrev = { haptic(); weekAnchor = weekAnchor.minusWeeks(1) },
            onNext = { haptic(); weekAnchor = weekAnchor.plusWeeks(1) },
            onThisWeek = { haptic(); weekAnchor = latestWeek }
        )
    } else null
    // Weekly mode lights up the ribbon chip of the month the week starts in (if that month has data).
    val ribbonMonthId = if (timeframe == ReportTimeframe.MONTHLY) effectiveMonthId
        else range.start.toString().take(7).takeIf { id -> archive.any { it.monthId == id } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shared header: same component, top offset and toggle as Attendance / Timetable / Settings
            item(key = "report_header") {
                AppScreenHeader(
                    title = "Reports & Analytics",
                    subtitle = "Monthly & Subject-Wise Breakdown",
                    isDark = glass.isDark,
                    onToggleTheme = { viewModel.toggleDarkMode() },
                    modifier = Modifier.padding(horizontal = 4.dp) // list has 16dp -> title sits 20dp from the edge
                )
            }
            item(key = "month_archive") {
                MonthArchiveRibbon(
                    p, archive,
                    selectedMonthId = ribbonMonthId,
                    dimmed = timeframe == ReportTimeframe.WEEKLY,
                    reduced = reduced,
                    onSelect = { id -> haptic(); viewModel.selectMonth(id); timeframe = ReportTimeframe.MONTHLY }
                )
            }
            item(key = "timeframe_toggle") {
                TimeframeSegmented(p, timeframe) { tf -> if (tf != timeframe) haptic(); timeframe = tf }
            }
            item(key = "section_tabs") {
                SectionTabs(
                    p, section,
                    subjectCount = analytics.subjects.size,
                    insightsNeedAttention = insightsNeedAttention(insights),
                    onSelect = { haptic(); section = it }
                )
            }
            item(key = "section_content") {
              Box(Modifier.animateContentSize(tween(if (reduced) 0 else 300))) {
                AnimatedContent(
                    targetState = section,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                            (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { dir * it / 6 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -dir * it / 8 })
                        }
                    },
                    label = "reportSection"
                ) { s ->
                    when (s) {
                        ReportSection.OVERVIEW -> OverviewSection(
                            p, analytics, timeframe, reduced, today,
                            selectedDate = selectedDate,
                            onSelectDate = { haptic(); selectedDate = it },
                            holidayName = holidayName,
                            weekNav = weekNav,
                            onViewAll = { section = ReportSection.SUBJECTS },
                            onOpenSubject = { code -> expandedCode = code; section = ReportSection.SUBJECTS },
                            onOpenMonthSummary = if (timeframe == ReportTimeframe.MONTHLY) ({ viewModel.showMonthEndSummary(true) }) else null,
                            onOpenPdf = { mode -> viewModel.showPdfPreview(true, mode) }
                        )
                        ReportSection.SUBJECTS -> SubjectsSection(
                            p, analytics, label,
                            subjectFilter, { subjectFilter = it },
                            subjectSort, { subjectSort = it },
                            expandedCode, { expandedCode = it },
                            historyAll, { c -> historyAll = if (c in historyAll) historyAll - c else historyAll + c },
                            reduced
                        )
                        ReportSection.TRENDS -> TrendsSection(
                            p, trendWindow, { trendWindow = it },
                            "${windowRange.start.format(dayFmt)} – ${windowRange.end.format(dayYearFmt)}",
                            windowAnalytics, semesterPoints,
                            if (trendWindow == TrendWindow.THIS_PERIOD)
                                ReportAnalyticsEngine.deltaVersusPrevious(analytics, previous) else null,
                            if (timeframe == ReportTimeframe.MONTHLY)
                                ReportAnalyticsEngine.monthLabel(YearMonth.from(effRange.previousComparable().start))
                            else "the previous week",
                            reduced
                        )
                        ReportSection.INSIGHTS -> InsightsSection(p, analytics, insights, actionPlan, forecast, reduced)
                        ReportSection.SMART -> SmartSection(
                            p = p,
                            analytics = analytics,
                            onOpenPdf = { mode -> viewModel.showPdfPreview(true, mode) }
                        ) { tool ->
                            when (tool) {
                                SmartTool.PREDICTOR -> section = ReportSection.INSIGHTS
                                SmartTool.BUNK -> viewModel.openBunkPlanner()
                                else -> activeTool = tool
                            }
                        }
                    }
                }
              }
            }
            item { Spacer(Modifier.height(150.dp)) }
        }
    }

    when (activeTool) {
        SmartTool.WHAT_IF -> androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.whatIf.open()
            activeTool = null
        }
        SmartTool.RECOVERY -> RecoveryPlannerDialog(p, analytics, label) { activeTool = null }
        SmartTool.CALENDAR -> AktuAcademicCalendarDialog(isDark = glass.isDark, onDismiss = { activeTool = null })
        else -> Unit
    }
    if (showMonthEnd) {
        MonthEndSummaryDialog(
            analytics = repo.getMonthlyAnalytics(effectiveMonthId),
            onDismiss = { viewModel.showMonthEndSummary(false) }
        )
    }
}

// --- Month-end certificate dialog (existing feature, preserved unchanged) ---

@Composable
fun MonthEndSummaryDialog(
    analytics: MonthlyAnalytics,
    onDismiss: () -> Unit
) {
    val glass = LocalGlassTheme.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (glass.isDark) Color(0xFF151D2E) else com.example.ui.theme.LightPearl),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🎉 Month-End Attendance Certificate",
                    fontFamily = OutfitFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = glass.textPrimary
                )
                Text(
                    text = "${analytics.monthName} Official Summary",
                    fontSize = 12.sp,
                    color = glass.textSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glass.royalIndigo.copy(alpha = 0.12f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Conducted Credits:", fontSize = 12.sp, color = glass.textMuted)
                            Text("${analytics.totalConductedCredits} Hrs", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Attended Credits:", fontSize = 12.sp, color = glass.textMuted)
                            Text("${analytics.totalAttendedCredits} Hrs", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Monthly Percentage:", fontSize = 12.sp, color = glass.textMuted)
                            Text("${analytics.percentage}%", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, color = glass.emeraldNeon)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Exam Eligibility (75%):", fontSize = 12.sp, color = glass.textMuted)
                            Text(if (analytics.isExamEligible) "SAFE" else "SHORT", fontWeight = FontWeight.Bold, color = if (analytics.isExamEligible) glass.emeraldNeon else glass.crimsonRose)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stipend Eligibility (80%):", fontSize = 12.sp, color = glass.textMuted)
                            Text(if (analytics.isStipendEligible) "ELIGIBLE" else "NOT ELIGIBLE", fontWeight = FontWeight.Bold, color = if (analytics.isStipendEligible) glass.emeraldNeon else glass.crimsonRose)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.royalIndigo)
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close Certificate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

