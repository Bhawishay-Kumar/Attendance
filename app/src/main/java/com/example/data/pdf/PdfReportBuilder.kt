package com.example.data.pdf

import com.example.data.math.AttendanceMath
import com.example.data.model.StudentProfile
import com.example.data.model.PeriodStatus
import com.example.data.reports.ReportAnalytics
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.reports.ReportSessionStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure adapter from [ReportAnalytics] (computed by the existing [ReportAnalyticsEngine]) to the
 * rows printed in the PDF. It never recalculates attendance: every attended / conducted figure
 * is read from the engine output. The only logic added here is:
 *  - exact (unrounded) weighted target comparison, and
 *  - choosing which rows/footnotes to show.
 * It is read-only: inputs are never modified.
 */
object PdfReportBuilder {

    private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val rowDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)
    private val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

    /**
     * Exact weighted comparison: attended/conducted >= target/100, done with integers so no
     * rounding can ever flip the result. Needs conducted > 0.
     */
    fun meetsTarget(attendedCredits: Int, conductedCredits: Int, targetPercent: Int): Boolean =
        conductedCredits > 0 &&
            attendedCredits.toLong() * 100L >= targetPercent.toLong() * conductedCredits.toLong()

    /**
     * Display text for a percentage. Uses the app's own rounding ([AttendanceMath], one decimal).
     * If that rounding would make a student look like they reached a target they have not (e.g.
     * 74.96% shown as 75.0%), it shows two truncated decimals instead so the text never
     * contradicts the exact eligibility result.
     */
    fun percentText(attendedCredits: Int, conductedCredits: Int, targets: List<Int>): String {
        if (conductedCredits <= 0) return "—"
        val rounded = AttendanceMath.calculatePercentage(attendedCredits, conductedCredits)
        val exact = attendedCredits.toDouble() * 100.0 / conductedCredits.toDouble()
        val crossesTarget = targets.any { t -> !meetsTarget(attendedCredits, conductedCredits, t) && rounded >= t }
        return if (crossesTarget) {
            val floored = Math.floor(exact * 100.0 + 1e-9) / 100.0
            String.format(Locale.ENGLISH, "%.2f%%", floored)
        } else {
            String.format(Locale.ENGLISH, "%.1f%%", rounded)
        }
    }

    fun build(
        request: PdfReportRequest,
        records: Map<String, Map<Int, PeriodStatus>>,
        isHoliday: (String) -> Boolean,
        holidayName: (String) -> String?,
        profile: StudentProfile,
        today: LocalDate
    ): PdfReportOutcome {
        val range = request.range
        if (request.periodType == PdfPeriodType.CUSTOM) {
            PdfRangeValidator.validateCustom(range.start, range.end, today)?.let { return PdfReportOutcome.Invalid(it) }
        } else if (range.start.isAfter(today)) {
            return PdfReportOutcome.Invalid("This period hasn't started yet, so there is nothing to report.")
        }

        val a = ReportAnalyticsEngine.analyze(range, records, isHoliday, profile, today)

        if (!a.hasManualData) {
            return if (a.counts.autoPresent > 0) {
                PdfReportOutcome.InsufficientData(
                    "This period only contains auto-credited sessions (such as Library or Seminar). " +
                        "Mark at least one Present or Absent class to generate a meaningful report."
                )
            } else {
                PdfReportOutcome.NoRecords("No attendance has been recorded in this period.")
            }
        }

        val exam = profile.examTarget
        val stipend = profile.stipendTarget
        val att = a.attendedCredits
        val cond = a.conductedCredits
        val examMet = meetsTarget(att, cond, exam)
        val stipendMet = meetsTarget(att, cond, stipend)
        val targetList = listOf(exam, stipend)

        // Recovery / safe-skip figures come from the existing engine (exact integer maths).
        val targets = listOf(
            targetStatus("Exam target", exam, examMet, a.safeSkipHoursExam, a.recoveryHoursExam),
            targetStatus("Stipend target", stipend, stipendMet, a.safeSkipHoursStipend, a.recoveryHoursStipend)
        )

        val subjects = a.subjects
            .filter { it.conductedCredits > 0 }
            .map {
                PdfSubjectRow(
                    name = it.subject.fullName,
                    attendedCredits = it.attendedCredits,
                    conductedCredits = it.conductedCredits,
                    percentText = percentText(it.attendedCredits, it.conductedCredits, targetList),
                    isAutoCredited = it.subject.isAutoPresent,
                    meetsExamTarget = meetsTarget(it.attendedCredits, it.conductedCredits, exam)
                )
            }

        val footnotes = buildFootnotes(a)
        val toDate = request.periodType != PdfPeriodType.CUSTOM && !range.end.isBefore(today)
        val toDateNote = if (toDate) "To date (through ${today.format(longDate)})" else null

        val details = if (request.reportType == PdfReportType.DETAILED) detailRows(a, holidayName) else null

        val name = profile.name.trim().ifEmpty { "Student" }
        val studentDetails = listOfNotNull(
            profile.rollNumber.trim().takeIf { it.isNotEmpty() }?.let { "Roll No." to it },
            profile.academicSession.trim().takeIf { it.isNotEmpty() }?.let { "Session" to it },
            "Batch" to profile.batchEnum.displayName,
            "Course" to "${profile.courseSelectionEnum.shortTitle} · ${profile.courseSelectionEnum.subtitle}"
        )

        return PdfReportOutcome.Ready(
            PdfReportData(
                reportType = request.reportType,
                periodType = request.periodType,
                studentName = name,
                studentDetails = studentDetails,
                periodLabel = periodLabel(request.periodType, range),
                toDateNote = toDateNote,
                generatedOn = today.format(longDate),
                attendedCredits = att,
                conductedCredits = cond,
                percentText = percentText(att, cond, targetList),
                meetsExamTarget = examMet,
                targets = targets,
                assessment = assessment(examMet, stipendMet, exam, stipend, a),
                subjects = subjects,
                footnotes = footnotes,
                detailRows = details,
                fileName = fileName(request)
            )
        )
    }

    private fun targetStatus(label: String, target: Int, met: Boolean, safeSkip: Int, recovery: Int): PdfTargetStatus {
        val detail = when {
            met && safeSkip > 0 -> "Can miss up to $safeSkip more credit hr${if (safeSkip == 1) "" else "s"} and stay on target"
            met -> "On target — no spare credit hours"
            recovery < 0 -> "Cannot be reached in this period"
            else -> "Needs $recovery more credit hr${if (recovery == 1) "" else "s"} attended"
        }
        return PdfTargetStatus(label, target, met, detail)
    }

    private fun assessment(examMet: Boolean, stipendMet: Boolean, exam: Int, stipend: Int, a: ReportAnalytics): String = when {
        examMet && stipendMet -> "Meets both the exam ($exam%) and stipend ($stipend%) targets for this period."
        examMet -> "Meets the exam target ($exam%) but is below the stipend target ($stipend%)."
        else -> "Below the exam target ($exam%) and the stipend target ($stipend%) for this period."
    }

    private fun buildFootnotes(a: ReportAnalytics): List<String> {
        val notes = mutableListOf<String>()
        notes += "Attended and conducted figures are weighted credit hours, as used in the app."
        // Auto-credited = sessions of auto-present subjects that were credited (Library, Seminar...).
        val auto = a.subjects.flatMap { it.sessions }.filter {
            it.subject.isAutoPresent &&
                (it.status == ReportSessionStatus.AUTO_PRESENT || it.status == ReportSessionStatus.PRESENT)
        }
        if (auto.isNotEmpty()) {
            val credits = auto.sumOf { it.weight }
            notes += "Includes ${auto.size} auto-credited session${if (auto.size == 1) "" else "s"} " +
                "($credits credit hr${if (credits == 1) "" else "s"}), such as Library and Seminar. " +
                "They are counted in the totals but not listed individually."
        }
        if (a.counts.pending > 0) {
            notes += "${a.counts.pending} past session${if (a.counts.pending == 1) " is" else "s are"} not marked yet " +
                "and ${if (a.counts.pending == 1) "is" else "are"} not counted."
        }
        return notes
    }

    private fun detailRows(a: ReportAnalytics, holidayName: (String) -> String?): List<PdfDetailRow> {
        val rows = mutableListOf<PdfDetailRow>()
        for (day in a.days) {
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: continue
            val label = date.format(rowDate)
            if (day.status == com.example.data.reports.DayStatus.HOLIDAY) {
                if (day.dayOfWeek in 1..5) {
                    rows += PdfDetailRow(label, holidayName(day.date) ?: "Holiday", PdfDetailStatus.HOLIDAY)
                }
                continue
            }
            val manual = day.sessions.filter { !it.subject.isAutoPresent }
            if (manual.isNotEmpty() && manual.all { it.status == ReportSessionStatus.NO_CLASS }) {
                rows += PdfDetailRow(label, "All classes", PdfDetailStatus.NO_CLASS)
                continue
            }
            for (s in manual) {
                val status = when (s.status) {
                    ReportSessionStatus.PRESENT -> PdfDetailStatus.PRESENT
                    ReportSessionStatus.ABSENT -> PdfDetailStatus.ABSENT
                    ReportSessionStatus.NO_CLASS -> PdfDetailStatus.NO_CLASS
                    // Pending / upcoming never appear; auto rows are excluded above.
                    else -> continue
                }
                rows += PdfDetailRow(label, s.subject.fullName, status)
            }
        }
        return rows
    }

    private fun periodLabel(type: PdfPeriodType, range: ReportDateRange): String = when (type) {
        PdfPeriodType.MONTHLY -> ReportAnalyticsEngine.monthLabel(YearMonth.from(range.start))
        PdfPeriodType.WEEKLY -> "Week of ${range.start.format(shortDay)} – ${range.end.format(longDate)}"
        PdfPeriodType.CUSTOM -> "${range.start.format(longDate)} – ${range.end.format(longDate)}"
    }

    internal fun fileName(request: PdfReportRequest): String {
        val kind = if (request.reportType == PdfReportType.SUMMARY) "Summary" else "Detailed"
        val span = when (request.periodType) {
            PdfPeriodType.MONTHLY -> YearMonth.from(request.range.start).toString()
            PdfPeriodType.WEEKLY -> "Week_${request.range.startIso}"
            PdfPeriodType.CUSTOM -> "${request.range.startIso}_to_${request.range.endIso}"
        }
        return "BHTT_Attendance_${kind}_$span.pdf".replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
