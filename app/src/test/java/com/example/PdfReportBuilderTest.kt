package com.example

import com.example.data.math.AttendanceMath
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.pdf.PdfDetailStatus
import com.example.data.pdf.PdfPeriodType
import com.example.data.pdf.PdfRangeValidator
import com.example.data.pdf.PdfReportBuilder
import com.example.data.pdf.PdfReportData
import com.example.data.pdf.PdfReportOutcome
import com.example.data.pdf.PdfReportRequest
import com.example.data.pdf.PdfReportType
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
 * JVM tests for the PDF data layer. Every figure is compared with the existing
 * [ReportAnalyticsEngine] (the single source of truth); nothing here re-implements attendance.
 * Dates: 2026-10-05 is a Monday; "today" is Saturday 2026-10-31 unless a test says otherwise.
 */
class PdfReportBuilderTest {

    private val profile = StudentProfile(
        batch = "BATCH_A", courseSelection = "MAJOR_HONOURS",
        examTarget = 75, stipendTarget = 80, safetyBuffer = 4, name = "Test Student"
    )
    private val today = LocalDate.of(2026, 10, 31)
    private val noHol: (String) -> Boolean = { false }
    private val noName: (String) -> String? = { null }

    private fun fullDay(dow: Int, absent: Set<Int> = emptySet(), cancelled: Set<Int> = emptySet(), skip: Set<Int> = emptySet()) =
        TimetableMatrix.getScheduleForDay(dow, profile.batchEnum, profile.courseSelectionEnum)
            .filter { it.periodNumber !in skip }
            .associate {
                it.periodNumber to when {
                    it.periodNumber in cancelled -> PeriodStatus.NO_CLASS
                    it.subject.isAutoPresent -> PeriodStatus.AUTO_PRESENT
                    it.periodNumber in absent -> PeriodStatus.ABSENT
                    else -> PeriodStatus.PRESENT
                }
            }

    private fun range(a: String, b: String) = ReportDateRange(LocalDate.parse(a), LocalDate.parse(b))

    private fun req(r: ReportDateRange, type: PdfReportType = PdfReportType.DETAILED, period: PdfPeriodType = PdfPeriodType.CUSTOM) =
        PdfReportRequest(period, type, r)

    private fun ready(
        r: ReportDateRange,
        records: Map<String, Map<Int, PeriodStatus>>,
        type: PdfReportType = PdfReportType.DETAILED,
        period: PdfPeriodType = PdfPeriodType.CUSTOM,
        hol: (String) -> Boolean = noHol,
        holName: (String) -> String? = noName,
        t: LocalDate = today,
        p: StudentProfile = profile
    ): PdfReportData {
        val out = PdfReportBuilder.build(req(r, type, period), records, hol, holName, p, t)
        assertTrue("expected Ready but was $out", out is PdfReportOutcome.Ready)
        return (out as PdfReportOutcome.Ready).data
    }

    private val weekRecords = mapOf(
        "2026-10-05" to fullDay(1),
        "2026-10-06" to fullDay(2, absent = setOf(1)),
        "2026-10-07" to fullDay(3, absent = setOf(6)),
        "2026-10-08" to fullDay(4),
        "2026-10-09" to fullDay(5, absent = setOf(2))
    )

    @Test
    fun totalsAndSubjectRowsMatchExistingEngine() {
        val r = range("2026-10-05", "2026-10-11")
        val data = ready(r, weekRecords)
        val a = ReportAnalyticsEngine.analyze(r, weekRecords, noHol, profile, today)
        assertEquals(a.attendedCredits, data.attendedCredits)
        assertEquals(a.conductedCredits, data.conductedCredits)
        // Subject rows reconcile with the overall totals and with the engine per subject.
        assertEquals(a.attendedCredits, data.subjects.sumOf { it.attendedCredits })
        assertEquals(a.conductedCredits, data.subjects.sumOf { it.conductedCredits })
        a.subjects.filter { it.conductedCredits > 0 }.forEach { s ->
            val row = data.subjects.single { it.name == s.subject.fullName }
            assertEquals(s.attendedCredits, row.attendedCredits)
            assertEquals(s.conductedCredits, row.conductedCredits)
            assertEquals(
                String.format("%.1f%%", AttendanceMath.calculatePercentage(s.attendedCredits, s.conductedCredits)),
                row.percentText
            )
        }
    }

    @Test
    fun lecturesAndTutorialsOfOneSubjectAreOneRow() {
        val r = range("2026-10-05", "2026-10-11")
        val data = ready(r, mapOf("2026-10-05" to fullDay(1), "2026-10-08" to fullDay(4)))
        val cpt = data.subjects.filter { it.name == TimetableMatrix.CPT_II.fullName }
        assertEquals(1, cpt.size)
        // Monday lecture (period 6) + Thursday tutorial (period 5), 1 credit each, counted together.
        assertEquals(2, cpt.single().conductedCredits)
        // The lab is a different subject (own code) and is never merged into the lecture row.
        assertTrue(data.subjects.none { it.name == TimetableMatrix.CPT_II_LAB.fullName })
    }

    @Test
    fun labCreditIsAppliedExactlyOnce() {
        val r = range("2026-10-07", "2026-10-07")
        val data = ready(r, mapOf("2026-10-07" to fullDay(3)))
        val lab = data.subjects.single { it.name == TimetableMatrix.QUALITY_LAB.fullName }
        assertEquals(2, lab.attendedCredits)
        assertEquals(2, lab.conductedCredits)
        assertEquals(7, data.conductedCredits) // 5 lectures x1 + lab x2
    }

    @Test
    fun eligibilityUsesWeightedCreditsNotSessionCounts() {
        // Wed: five 1-credit classes present, the 2-credit lab absent.
        // Weighted 5/7 = 71.4% (below 75). Session count would be 5/6 = 83% (above 75).
        val r = range("2026-10-07", "2026-10-07")
        val data = ready(r, mapOf("2026-10-07" to fullDay(3, absent = setOf(6))))
        assertEquals(5, data.attendedCredits)
        assertEquals(7, data.conductedCredits)
        assertFalse(data.meetsExamTarget)
        assertFalse(data.targets.first().met)
        assertFalse(data.targets.last().met)
    }

    @Test
    fun eligibilityIsExactNotRounded() {
        assertFalse(PdfReportBuilder.meetsTarget(749, 999, 75))
        assertTrue(PdfReportBuilder.meetsTarget(750, 1000, 75))
        assertTrue(PdfReportBuilder.meetsTarget(3, 4, 75))
        assertFalse(PdfReportBuilder.meetsTarget(0, 0, 75))
        // App rounding would show 75.0; the PDF must not.
        assertEquals(75.0, AttendanceMath.calculatePercentage(749, 999), 0.0)
        assertEquals("74.97%", PdfReportBuilder.percentText(749, 999, listOf(75, 80)))
        assertEquals("75.0%", PdfReportBuilder.percentText(75, 100, listOf(75, 80)))
        assertEquals("—", PdfReportBuilder.percentText(0, 0, listOf(75)))
    }

    @Test
    fun targetsComeFromProfileNotHardcoded() {
        val custom = profile.copy(examTarget = 65, stipendTarget = 90)
        val data = ready(range("2026-10-05", "2026-10-11"), weekRecords, p = custom)
        assertEquals(listOf(65, 90), data.targets.map { it.targetPercent })
    }

    @Test
    fun autoCreditedOnlyPeriodIsInsufficientData() {
        // Monday has an auto-present Library slot but nothing is marked.
        val out = PdfReportBuilder.build(req(range("2026-10-05", "2026-10-05")), emptyMap(), noHol, noName, profile, today)
        assertTrue(out is PdfReportOutcome.InsufficientData)
    }

    @Test
    fun periodWithNoSessionsIsNoRecords() {
        val out = PdfReportBuilder.build(req(range("2026-10-10", "2026-10-11")), emptyMap(), noHol, noName, profile, today)
        assertTrue(out is PdfReportOutcome.NoRecords)
    }

    @Test
    fun futureSessionsAreNeverCounted() {
        val t = LocalDate.of(2026, 10, 7) // Wednesday
        val recs = weekRecords // Thu/Fri are marked present but lie in the future
        val r = range("2026-10-05", "2026-10-11")
        val data = ready(r, recs, period = PdfPeriodType.WEEKLY, t = t)
        val a = ReportAnalyticsEngine.analyze(r, recs, noHol, profile, t)
        assertEquals(a.conductedCredits, data.conductedCredits)
        assertEquals(a.attendedCredits, data.attendedCredits)
        // Detailed rows contain nothing after today.
        assertTrue(data.detailRows!!.all { LocalDate.parse(rowIso(it.dateLabel)) <= t })
    }

    private fun rowIso(label: String): String =
        LocalDate.parse(label.substringAfter(", "), java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)).toString()

    @Test
    fun detailedRowsListPresentAbsentOnlyAndExcludePendingAndAutoRows() {
        // Monday: period 1 present, period 2 absent, everything else unmarked (pending).
        val recs = mapOf("2026-10-05" to mapOf(1 to PeriodStatus.PRESENT, 2 to PeriodStatus.ABSENT))
        val data = ready(range("2026-10-05", "2026-10-05"), recs)
        val rows = data.detailRows!!
        assertEquals(2, rows.size)
        assertEquals(PdfDetailStatus.PRESENT, rows[0].status)
        assertEquals(PdfDetailStatus.ABSENT, rows[1].status)
        assertTrue(rows.none { it.subject.contains("Library") || it.subject.contains("Seminar") })
        assertTrue(data.footnotes.any { it.contains("auto-credited") && it.contains("credit hr") })
        assertTrue(data.footnotes.any { it.contains("not marked") })
    }

    @Test
    fun noClassRowsAppearOnlyAsContext() {
        val recs = mapOf(
            "2026-10-05" to fullDay(1, cancelled = setOf(3)),           // one class cancelled
            "2026-10-06" to fullDay(2, cancelled = setOf(1, 3, 4, 5, 7)) // all manual classes cancelled
        )
        val data = ready(range("2026-10-05", "2026-10-06"), recs)
        val rows = data.detailRows!!
        val noClass = rows.filter { it.status == PdfDetailStatus.NO_CLASS }
        assertEquals(2, noClass.size)
        assertTrue(noClass.any { it.subject == "All classes" })
        // Cancelled sessions add nothing to conducted credits.
        val a = ReportAnalyticsEngine.analyze(range("2026-10-05", "2026-10-06"), recs, noHol, profile, today)
        assertEquals(a.conductedCredits, data.conductedCredits)
    }

    @Test
    fun holidaysAreContextRowsAndNeverCounted() {
        val hol: (String) -> Boolean = { it == "2026-10-06" }
        val recs = mapOf("2026-10-05" to fullDay(1), "2026-10-06" to fullDay(2))
        val r = range("2026-10-05", "2026-10-06")
        val data = ready(r, recs, hol = hol, holName = { if (it == "2026-10-06") "Test Holiday" else null })
        val h = data.detailRows!!.single { it.status == PdfDetailStatus.HOLIDAY }
        assertEquals("Test Holiday", h.subject)
        val a = ReportAnalyticsEngine.analyze(r, recs, hol, profile, today)
        assertEquals(a.conductedCredits, data.conductedCredits)
    }

    @Test
    fun summaryReportHasNoDetailRows() {
        val data = ready(range("2026-10-05", "2026-10-11"), weekRecords, type = PdfReportType.SUMMARY)
        assertNull(data.detailRows)
    }

    @Test
    fun toDateLabelOnlyForRunningWeeklyOrMonthlyPeriods() {
        val running = ready(range("2026-10-01", "2026-10-31"), weekRecords, period = PdfPeriodType.MONTHLY, t = LocalDate.of(2026, 10, 20))
        assertNotNull(running.toDateNote)
        val done = ready(range("2026-10-01", "2026-10-31"), weekRecords, period = PdfPeriodType.MONTHLY, t = LocalDate.of(2026, 11, 3))
        assertNull(done.toDateNote)
        val custom = ready(range("2026-10-05", "2026-10-09"), weekRecords, period = PdfPeriodType.CUSTOM)
        assertNull(custom.toDateNote)
    }

    @Test
    fun customRangeValidation() {
        val d = LocalDate.of(2026, 10, 10)
        assertNotNull(PdfRangeValidator.validateCustom(null, d, today))
        assertNotNull(PdfRangeValidator.validateCustom(d, d.minusDays(1), today))
        assertNotNull(PdfRangeValidator.validateCustom(d, today.plusDays(1), today))
        assertNull(PdfRangeValidator.validateCustom(d, today, today))
        val out = PdfReportBuilder.build(req(ReportDateRange(d, today.plusDays(2))), weekRecords, noHol, noName, profile, today)
        assertTrue(out is PdfReportOutcome.Invalid)
    }

    @Test
    fun buildingAReportDoesNotModifyRecords() {
        val copy = weekRecords.mapValues { it.value.toMap() }
        ready(range("2026-10-05", "2026-10-11"), weekRecords)
        assertEquals(copy, weekRecords)
    }

    @Test
    fun fileNamesAreSafe() {
        val n = PdfReportBuilder.fileName(req(range("2026-10-05", "2026-10-09"), PdfReportType.SUMMARY, PdfPeriodType.CUSTOM))
        assertEquals("BHTT_Attendance_Summary_2026-10-05_to_2026-10-09.pdf", n)
        assertTrue(Regex("[A-Za-z0-9._-]+").matches(n))
    }

    @Test
    fun studentDetailsOmitBlankFieldsAndNeverIncludeSemester() {
        val data = ready(range("2026-10-05", "2026-10-11"), weekRecords)
        assertEquals("Test Student", data.studentName)
        assertTrue(data.studentDetails.none { it.first.contains("Semester", ignoreCase = true) })
        assertTrue(data.studentDetails.none { it.second.isBlank() })
    }

    @Test
    fun configResolverBuildsWeeklyMonthlyAndCustomRanges() {
        val wk = com.example.data.pdf.PdfConfigResolver.resolve(
            com.example.data.pdf.PdfConfig(PdfPeriodType.WEEKLY, weekStart = LocalDate.parse("2026-10-07")), today
        )
        assertEquals(range("2026-10-05", "2026-10-11"), wk.first) // Monday..Sunday containing the date
        val mo = com.example.data.pdf.PdfConfigResolver.resolve(
            com.example.data.pdf.PdfConfig(PdfPeriodType.MONTHLY, monthId = "2026-10"), today
        )
        assertEquals(range("2026-10-01", "2026-10-31"), mo.first)
        val futureMonth = com.example.data.pdf.PdfConfigResolver.resolve(
            com.example.data.pdf.PdfConfig(PdfPeriodType.MONTHLY, monthId = "2026-12"), today
        )
        assertNull(futureMonth.first); assertNotNull(futureMonth.second)
        val futureWeek = com.example.data.pdf.PdfConfigResolver.resolve(
            com.example.data.pdf.PdfConfig(PdfPeriodType.WEEKLY, weekStart = LocalDate.parse("2026-11-02")), today
        )
        assertNull(futureWeek.first)
        val badCustom = com.example.data.pdf.PdfConfigResolver.resolve(
            com.example.data.pdf.PdfConfig(PdfPeriodType.CUSTOM, customStart = LocalDate.parse("2026-10-20"), customEnd = LocalDate.parse("2026-10-10")), today
        )
        assertNull(badCustom.first); assertNotNull(badCustom.second)
        val okCustom = com.example.data.pdf.PdfConfigResolver.resolve(
            com.example.data.pdf.PdfConfig(PdfPeriodType.CUSTOM, customStart = LocalDate.parse("2026-10-10"), customEnd = today), today
        )
        assertEquals(range("2026-10-10", "2026-10-31"), okCustom.first)
    }

    @Test
    fun monthlyReportForTheRealCurrentMonthMatchesReportsScreenCalculation() {
        // Same range + same engine call the Reports screen uses for a month -> identical numbers.
        val month = ReportDateRange.ofMonth("2026-10")!!
        val data = ready(month, weekRecords, period = PdfPeriodType.MONTHLY, t = LocalDate.of(2026, 10, 20))
        val a = ReportAnalyticsEngine.analyze(month, weekRecords, noHol, profile, LocalDate.of(2026, 10, 20))
        assertEquals(a.attendedCredits, data.attendedCredits)
        assertEquals(a.conductedCredits, data.conductedCredits)
        assertEquals(a.counts.autoPresent > 0, data.footnotes.any { it.contains("auto-credited") })
    }
}
