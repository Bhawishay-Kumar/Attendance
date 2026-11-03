package com.example

import com.example.data.holidays.AktuHolidays
import com.example.data.math.AttendanceMath
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.reports.DayStatus
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.timetable.TimetableMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * JVM tests for the Reports engine. Rules mirror AttendanceRepository:
 * future dates never count, auto-present slots are credited on past/today working days without a
 * stored record, PENDING / NO_CLASS count for nothing.
 * 2026-10-05 is a Monday (QE, Knitting, Garment, Woven, Elective, CPT-II, Library(auto) = 7 hrs).
 */
class ReportAnalyticsEngineTest {

    private val profile = StudentProfile(
        batch = "BATCH_A",
        courseSelection = "MAJOR_HONOURS",
        examTarget = 75,
        stipendTarget = 80,
        safetyBuffer = 4
    )
    private val noHolidays: (String) -> Boolean = { false }
    private val officialHolidays: (String) -> Boolean = { AktuHolidays.isHoliday(it) }

    private val monday = "2026-10-05"
    private val today = LocalDate.of(2026, 10, 31)
    private val october = ReportDateRange.ofMonth("2026-10")!!

    private fun one(iso: String) = ReportDateRange(LocalDate.parse(iso), LocalDate.parse(iso))

    /** Full-day record for weekday [dow]; auto-present slots stored as AUTO_PRESENT. */
    private fun day(dow: Int, absent: Set<Int> = emptySet()): Map<Int, PeriodStatus> =
        TimetableMatrix.getScheduleForDay(dow, profile.batchEnum, profile.courseSelectionEnum).associate {
            it.periodNumber to when {
                it.subject.isAutoPresent -> PeriodStatus.AUTO_PRESENT
                it.periodNumber in absent -> PeriodStatus.ABSENT
                else -> PeriodStatus.PRESENT
            }
        }

    private fun analyzeOne(iso: String, rec: Map<Int, PeriodStatus>?, t: LocalDate = today, hol: (String) -> Boolean = noHolidays) =
        ReportAnalyticsEngine.analyze(one(iso), if (rec == null) emptyMap() else mapOf(iso to rec), hol, profile, t)

    @Test
    fun fullyPresentDayIsHundredPercentAndAutoBoostCounted() {
        val a = analyzeOne(monday, day(1))
        assertEquals(7, a.attendedCredits)
        assertEquals(7, a.conductedCredits)
        assertEquals(100.0, a.percentage, 0.001)
        assertEquals(1, a.autoPresentBoostHours) // Library, 1 credit hour
        assertTrue(a.hasManualData)
    }

    @Test
    fun absentCountsAsConductedButNotAttended() {
        val a = analyzeOne(monday, day(1, setOf(4))) // Woven Fabric
        assertEquals(6, a.attendedCredits)
        assertEquals(7, a.conductedCredits)
        val woven = a.subjects.first { it.subject == TimetableMatrix.WOVEN_FABRIC }
        assertEquals(0, woven.attendedCredits)
        assertEquals(1, woven.conductedCredits)
        assertEquals(1, woven.counts.absent)
        assertEquals(1, a.missedCredits)
    }

    @Test
    fun noClassIsExcludedAndReportedSeparately() {
        val rec = day(1).toMutableMap().apply { put(2, PeriodStatus.NO_CLASS) }
        val a = analyzeOne(monday, rec)
        assertEquals(6, a.attendedCredits)
        assertEquals(6, a.conductedCredits)
        assertEquals(100.0, a.percentage, 0.001)
        assertEquals(1, a.counts.noClass)
        assertEquals(0, a.counts.absent)
        assertEquals(1, a.cancelledCredits)
    }

    @Test
    fun unmarkedPeriodInSavedDayIsPendingAndNeverCountedAsPresent() {
        val rec = day(1).toMutableMap().apply { remove(3) }
        val a = analyzeOne(monday, rec)
        assertEquals(6, a.conductedCredits)
        assertEquals(6, a.attendedCredits)
        assertEquals(1, a.counts.pending)
    }

    @Test
    fun storedPendingIsPendingAndNeverCounted() {
        val rec = day(1).toMutableMap().apply { put(3, PeriodStatus.PENDING) }
        val a = analyzeOne(monday, rec)
        assertEquals(6, a.conductedCredits)
        assertEquals(1, a.counts.pending)
    }

    @Test
    fun unrecordedPastDayCreditsOnlyAutoPresentAndLeavesRestPending() {
        val a = analyzeOne("2026-10-12", null) // Monday, no record
        assertEquals(1, a.conductedCredits)    // Library only
        assertEquals(1, a.attendedCredits)
        assertEquals(6, a.counts.pending)
        assertFalse(a.hasManualData)
        assertTrue(a.hasData)
    }

    @Test
    fun futureDatesNeverCountEvenWhenMarked() {
        val a = analyzeOne(monday, day(1), t = LocalDate.of(2026, 10, 1))
        assertEquals(0, a.conductedCredits)
        assertEquals(0, a.attendedCredits)
        assertEquals(7, a.counts.upcoming)
        assertFalse(a.hasData)
    }

    @Test
    fun autoPresentSlotsAreCreditedUnlessCancelled() {
        // Tuesday has Library at periods 2 and 6 (1 hr each).
        val none = analyzeOne("2026-10-06", null)
        assertEquals(2, none.conductedCredits)
        assertEquals(2, none.autoPresentBoostHours)
        assertEquals(2, none.counts.autoPresent)

        val storedAbsent = analyzeOne("2026-10-06", mapOf(2 to PeriodStatus.ABSENT, 6 to PeriodStatus.ABSENT))
        assertEquals(2, storedAbsent.attendedCredits) // an absent mark can never remove auto credit
        assertEquals(0, storedAbsent.counts.absent)

        val cancelled = analyzeOne("2026-10-06", mapOf(2 to PeriodStatus.NO_CLASS))
        assertEquals(1, cancelled.conductedCredits)
        assertEquals(1, cancelled.counts.noClass)
    }

    @Test
    fun holidaysAreExcludedFromWorkingDaysAndNeverAbsences() {
        // 2026-10-02 (Fri) is Gandhi Jayanti; 2026-10-01 is a Thursday.
        val range = ReportDateRange.custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2))!!
        val a = ReportAnalyticsEngine.analyze(range, emptyMap(), officialHolidays, profile, today)
        assertEquals(1, a.holidayDays)
        assertEquals(1, a.workingDays)
        assertEquals(0, a.counts.absent)
    }

    @Test
    fun practicalLabCarriesItsTwoHourWeight() {
        // Thursday, Batch A: Woven 1, Elective 1, Woven Lab 2 (p3), CPT tutorial 1 (p5), Seminar 2 (auto) = 7.
        val thursday = "2026-10-08"
        val slots = TimetableMatrix.getScheduleForDay(4, profile.batchEnum, profile.courseSelectionEnum)
        val a = analyzeOne(thursday, day(4, setOf(3)))
        val lab = a.subjects.first { it.subject == TimetableMatrix.WOVEN_LAB }
        assertEquals(2, lab.conductedCredits)
        assertEquals(0, lab.attendedCredits)
        assertEquals(slots.sumOf { it.subject.creditHours }, a.conductedCredits)
        assertEquals(a.conductedCredits - 2, a.attendedCredits)
    }

    @Test
    fun recoveryAndSafeSkipReuseAttendanceMath() {
        val a = analyzeOne(monday, day(1, setOf(4, 5)))
        assertEquals(5, a.attendedCredits)
        assertEquals(7, a.conductedCredits)
        assertEquals(AttendanceMath.calculateRecoveryHours(5, 7, 75), a.recoveryHoursExam)
        assertEquals(AttendanceMath.calculateRecoveryHours(5, 7, 80), a.recoveryHoursStipend)
        assertEquals(AttendanceMath.calculateSafeSkipHours(5, 7, 80), a.safeSkipHoursStipend)
    }

    @Test
    fun periodWithNothingCountableHasNoDataAndNoNaN() {
        // "Today" is before the month: every session is upcoming.
        val a = ReportAnalyticsEngine.analyze(october, emptyMap(), noHolidays, profile, LocalDate.of(2026, 9, 30))
        assertFalse(a.hasData)
        assertEquals(0.0, a.percentage, 0.0)
        assertFalse(a.isExamEligible)
        assertFalse(a.percentage.isNaN())
        assertTrue(a.daily.isEmpty())
    }

    @Test
    fun archiveOnlyContainsMonthsWithRealMarkedAttendancePlusCurrentMonth() {
        val oct = mapOf(monday to day(1))
        val m1 = ReportAnalyticsEngine.archiveMonths(oct, noHolidays, profile, today)
        assertEquals(listOf("2026-10"), m1.map { it.monthId })
        assertEquals(100.0, m1.single().percentage!!, 0.001)

        val withSeptember = oct + ("2026-09-07" to day(1))
        val m2 = ReportAnalyticsEngine.archiveMonths(withSeptember, noHolidays, profile, today)
        assertEquals(listOf("2026-09", "2026-10"), m2.map { it.monthId })

        // A month with only cancelled classes has nothing the student actually attended.
        val noClassOnly = mapOf("2026-08-03" to (1..7).associateWith { PeriodStatus.NO_CLASS })
        val m3 = ReportAnalyticsEngine.archiveMonths(noClassOnly, noHolidays, profile, today)
        assertEquals(listOf("2026-10"), m3.map { it.monthId })
    }

    @Test
    fun futureMonthsAreNeverArchived() {
        val m = ReportAnalyticsEngine.archiveMonths(mapOf("2026-12-07" to day(1)), noHolidays, profile, today)
        assertEquals(listOf("2026-10"), m.map { it.monthId })
    }

    @Test
    fun deltaVersusPreviousNeedsMarkedAttendanceInBothPeriods() {
        val onlyOct = mapOf(monday to day(1))
        val cur = ReportAnalyticsEngine.analyze(october, onlyOct, noHolidays, profile, today)
        val prevEmpty = ReportAnalyticsEngine.analyze(october.previousComparable(), onlyOct, noHolidays, profile, today)
        assertEquals(ReportDateRange.ofMonth("2026-09"), october.previousComparable())
        assertNull(ReportAnalyticsEngine.deltaVersusPrevious(cur, prevEmpty)) // September has no marked data

        val both = onlyOct + ("2026-09-07" to day(1, setOf(4)))
        val cur2 = ReportAnalyticsEngine.analyze(october, both, noHolidays, profile, today)
        val prev2 = ReportAnalyticsEngine.analyze(october.previousComparable(), both, noHolidays, profile, today)
        val delta = ReportAnalyticsEngine.deltaVersusPrevious(cur2, prev2)
        assertNotNull(delta)
        assertTrue(delta!! > 0.0)
    }

    @Test
    fun dateRangeHelpers() {
        val week = ReportDateRange.weekContaining(LocalDate.of(2026, 10, 7))
        assertEquals(LocalDate.of(2026, 10, 5), week.start)
        assertEquals(LocalDate.of(2026, 10, 11), week.end)
        assertNull(ReportDateRange.custom(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1)))
        val last7 = ReportDateRange.lastDays(7, today)
        assertEquals(7, last7.dayCount)
        assertEquals(today, last7.end)
        assertEquals(LocalDate.of(2026, 10, 12), ReportDateRange.monthToDate("2026-10", LocalDate.of(2026, 10, 12))!!.end)
    }

    @Test
    fun lectureAndTutorialOfOneSubjectAreAggregatedTogether() {
        val thursday = "2026-10-08"
        val records = mapOf(monday to day(1), thursday to day(4))
        val a = ReportAnalyticsEngine.analyze(october, records, noHolidays, profile, today)
        val cpt = a.subjects.filter { it.subject.code == TimetableMatrix.CPT_II.code }
        assertEquals(1, cpt.size)
        // Monday lecture (1) + Thursday tutorial (1); other CPT-II days are unmarked and pending.
        assertEquals(2, cpt.single().conductedCredits)
    }

    @Test
    fun holidayOverridesSavedRecordsWithoutDeletingThem() {
        val records = mapOf(monday to day(1))
        val a = ReportAnalyticsEngine.analyze(one(monday), records, { it == monday }, profile, today)
        assertEquals(0, a.conductedCredits)
        assertEquals(1, a.holidayDays)
        assertEquals(1, records.size)
    }

    @Test
    fun calendarDayStatusesFollowTheDocumentedColourRules() {
        val records = mapOf(
            "2026-10-05" to day(1),
            "2026-10-06" to day(2, setOf(1)),
            "2026-10-07" to (1..6).associateWith { PeriodStatus.NO_CLASS },
            "2026-10-08" to day(4).toMutableMap().apply { remove(1) }
        )
        val a = ReportAnalyticsEngine.analyze(october, records, officialHolidays, profile, today)
        val by = a.days.associateBy { it.date }
        assertEquals(31, a.days.size) // every date, weekends and holidays included
        assertEquals(DayStatus.PRESENT, by.getValue("2026-10-05").status)
        assertEquals(DayStatus.ABSENT, by.getValue("2026-10-06").status)
        assertEquals(DayStatus.CANCELLED, by.getValue("2026-10-07").status)
        assertEquals(DayStatus.INCOMPLETE, by.getValue("2026-10-08").status)
        assertEquals(DayStatus.HOLIDAY, by.getValue("2026-10-02").status)
        assertEquals(DayStatus.WEEKEND, by.getValue("2026-10-03").status)
        assertEquals(DayStatus.PENDING, by.getValue("2026-10-12").status)
        val early = ReportAnalyticsEngine.analyze(october, records, officialHolidays, profile, LocalDate.of(2026, 10, 9))
        assertEquals(DayStatus.UPCOMING, early.days.first { it.date == "2026-10-13" }.status)
    }

    @Test
    fun statTileTotalsMatchHeroNumbers() {
        val rec = day(1).toMutableMap().apply { put(2, PeriodStatus.NO_CLASS); put(4, PeriodStatus.ABSENT) }
        val a = analyzeOne(monday, rec)
        assertEquals(5, a.attendedCredits)
        assertEquals(6, a.conductedCredits)
        assertEquals(a.conductedCredits - a.attendedCredits, a.missedCredits)
        assertEquals(1, a.missedCredits)
        assertEquals(1, a.cancelledCredits)
    }
}
