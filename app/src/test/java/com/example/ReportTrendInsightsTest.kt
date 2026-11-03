package com.example

import com.example.data.holidays.AktuHolidays
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.reports.DayStatus
import com.example.data.reports.InsightSeverity
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.reports.ReportInsightsEngine
import com.example.data.reports.ReportSessionStatus
import com.example.data.reports.ReportTrendEngine
import com.example.data.reports.TrendWindow
import com.example.data.timetable.TimetableMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportTrendInsightsTest {
    private val profile = StudentProfile(
        batch = "BATCH_A", courseSelection = "MAJOR_HONOURS",
        examTarget = 75, stipendTarget = 80, safetyBuffer = 4
    )
    private val hol: (String) -> Boolean = { AktuHolidays.isHoliday(it) }
    private val october = ReportDateRange.ofMonth("2026-10")!!

    private fun day(dow: Int, absentPeriods: Set<Int> = emptySet()): Map<Int, PeriodStatus> =
        TimetableMatrix.getScheduleForDay(dow, profile.batchEnum, profile.courseSelectionEnum).associate {
            it.periodNumber to when {
                it.subject.isAutoPresent -> PeriodStatus.AUTO_PRESENT
                it.periodNumber in absentPeriods -> PeriodStatus.ABSENT
                else -> PeriodStatus.PRESENT
            }
        }

    @Test
    fun windowsNeverIncludeFutureDatesAndAnchorToReportingRange() {
        val today = LocalDate.of(2026, 10, 15)
        val w7 = ReportTrendEngine.windowRange(TrendWindow.LAST_7_DAYS, october, today)
        assertEquals(LocalDate.of(2026, 10, 9), w7.start)
        assertEquals(today, w7.end)
        val w30 = ReportTrendEngine.windowRange(TrendWindow.LAST_30_DAYS, october, today)
        assertEquals(30, w30.dayCount)
        val period = ReportTrendEngine.windowRange(TrendWindow.THIS_PERIOD, october, today)
        assertEquals(LocalDate.of(2026, 10, 1), period.start)
        assertEquals(today, period.end)
        // Archived month: anchored to its own end, not today.
        val sep = ReportDateRange.ofMonth("2026-09")!!
        assertEquals(LocalDate.of(2026, 9, 30), ReportTrendEngine.windowRange(TrendWindow.THIS_PERIOD, sep, today).end)
    }

    @Test
    fun trajectoryIsCumulativeAndEndsAtThePeriodPercentage() {
        val records = mapOf("2026-10-05" to day(1), "2026-10-06" to day(2, setOf(1, 2)))
        val a = ReportAnalyticsEngine.analyze(october, records, hol, profile, LocalDate.of(2026, 10, 31))
        val pts = ReportTrendEngine.trajectory(a)
        assertTrue(pts.size >= 2)
        assertEquals(pts.map { it.date }, pts.map { it.date }.sorted())
        assertTrue(pts.all { it.cumulativePercentage in 0.0..100.0 })
        assertEquals(a.percentage, pts.last().cumulativePercentage, 0.001)
        assertTrue(pts.last().cumulativePercentage < 100.0) // the Tuesday absence lowers it
    }

    @Test
    fun breakdownKeepsCancelledSeparateFromAbsent() {
        val rec = day(1).toMutableMap().apply { put(2, PeriodStatus.NO_CLASS); put(4, PeriodStatus.ABSENT) }
        val a = ReportAnalyticsEngine.analyze(october, mapOf("2026-10-05" to rec), hol, profile, LocalDate.of(2026, 10, 31))
        val b = ReportTrendEngine.breakdown(a).associateBy { it.status }
        assertEquals(1, b.getValue(ReportSessionStatus.NO_CLASS).sessions)
        assertEquals(1, b.getValue(ReportSessionStatus.ABSENT).sessions)
    }

    @Test
    fun insightsAreEmptyWithoutDataAndForecastIsNull() {
        val a = ReportAnalyticsEngine.analyze(october, emptyMap(), hol, profile, LocalDate.of(2026, 10, 15))
        assertNull(ReportInsightsEngine.forecast(a, emptyMap(), hol, profile, LocalDate.of(2026, 10, 15)))
        assertTrue(ReportInsightsEngine.generate(a, null, null).none { it.severity == InsightSeverity.CRITICAL })
    }

    @Test
    fun belowExamTargetProducesCriticalRecoveryInsight() {
        // 1 present day then a day with most periods absent -> well below 75%
        val records = mapOf("2026-10-05" to day(1), "2026-10-06" to day(2, setOf(1, 3, 4, 5, 7)))
        val a = ReportAnalyticsEngine.analyze(october, records, hol, profile, LocalDate.of(2026, 10, 6))
        assertTrue(a.percentage < 75.0)
        val insights = ReportInsightsEngine.generate(a, null, null)
        val crit = insights.first { it.id == "below_exam" }
        assertEquals(InsightSeverity.CRITICAL, crit.severity)
        assertTrue(crit.detail.contains("${a.recoveryHoursExam}"))
    }

    @Test
    fun forecastBudgetUsesOnlyUnrecordedNonAutoHoursAndIsAnEstimate() {
        val today = LocalDate.of(2026, 10, 7) // Wednesday
        val records = mapOf("2026-10-05" to day(1), "2026-10-06" to day(2))
        val a = ReportAnalyticsEngine.analyze(october, records, hol, profile, today)
        val f = ReportInsightsEngine.forecast(a, records, hol, profile, today)
        assertNotNull(f)
        f!!
        assertTrue(f.remainingHours > 0)
        assertEquals(100.0, f.ifAttendAllPercentage, 0.001) // already 100% and attending all keeps it
        assertTrue(f.ifMissAllPercentage < f.ifAttendAllPercentage)
        // Records must be untouched by forecasting
        assertEquals(2, records.size)
    }

    @Test
    fun forecastIsNullForHistoricalPeriods() {
        val sep = ReportDateRange.ofMonth("2026-09")!!
        val records = mapOf("2026-09-07" to day(1))
        val today = LocalDate.of(2026, 10, 7)
        val a = ReportAnalyticsEngine.analyze(sep, records, hol, profile, today)
        assertNull(ReportInsightsEngine.forecast(a, records, hol, profile, today))
    }

    @Test
    fun semesterRangeStartsAtFirstRecordAndIgnoresFutureOnes() {
        val records = mapOf("2026-09-07" to day(1), "2026-12-07" to day(1))
        val r = ReportTrendEngine.semesterRange(records, LocalDate.of(2026, 10, 31))!!
        assertEquals(LocalDate.of(2026, 9, 7), r.start)
        assertNull(ReportTrendEngine.semesterRange(emptyMap(), LocalDate.of(2026, 10, 31)))
    }

    private fun d(date: String, status: DayStatus) =
        com.example.data.reports.DayReport(date, LocalDate.parse(date).dayOfWeek.value, status, 0, 0, emptyList())

    @Test
    fun presentStreakSkipsWeekendsHolidaysAndAnUnmarkedToday() {
        val days = listOf(
            d("2026-10-05", DayStatus.PRESENT), d("2026-10-06", DayStatus.PRESENT), d("2026-10-07", DayStatus.HOLIDAY),
            d("2026-10-08", DayStatus.PRESENT), d("2026-10-09", DayStatus.PENDING), d("2026-10-10", DayStatus.WEEKEND), d("2026-10-11", DayStatus.UPCOMING)
        )
        assertEquals(3, ReportTrendEngine.presentStreak(days))
    }

    @Test
    fun presentStreakEndsAtAbsenceOrIncompleteDay() {
        assertEquals(2, ReportTrendEngine.presentStreak(listOf(d("2026-10-05", DayStatus.PRESENT), d("2026-10-06", DayStatus.ABSENT), d("2026-10-07", DayStatus.PRESENT), d("2026-10-08", DayStatus.PRESENT))))
        assertEquals(1, ReportTrendEngine.presentStreak(listOf(d("2026-10-05", DayStatus.PENDING), d("2026-10-06", DayStatus.PRESENT))))
        assertEquals(0, ReportTrendEngine.presentStreak(listOf(d("2026-10-05", DayStatus.PRESENT), d("2026-10-06", DayStatus.INCOMPLETE))))
    }

    @Test
    fun fixedNationalHolidaysApplyOnlyToYearsWithoutAnOfficialList() {
        assertTrue(com.example.data.holidays.AktuHolidays.isHoliday("2027-01-26"))
        assertTrue(com.example.data.holidays.AktuHolidays.isHoliday("2027-08-15"))
        assertEquals("Mahatma Gandhi Jayanti", com.example.data.holidays.AktuHolidays.getHoliday("2027-10-02")?.name)
        assertTrue(!com.example.data.holidays.AktuHolidays.isHoliday("2027-03-05"))
    }
}
