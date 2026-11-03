package com.example.data.pdf

import com.example.data.reports.ReportDateRange
import java.time.LocalDate

/** Period choices offered by the PDF Report feature (daily reports are intentionally excluded). */
enum class PdfPeriodType { WEEKLY, MONTHLY, CUSTOM }

enum class PdfReportType { SUMMARY, DETAILED }

/** What the user asked for. [range] is the full requested range; future days inside it never count. */
data class PdfReportRequest(
    val periodType: PdfPeriodType,
    val reportType: PdfReportType,
    val range: ReportDateRange
)

/** Result of trying to build a report. Only [Ready] leads to a PDF. */
sealed interface PdfReportOutcome {
    data class Ready(val data: PdfReportData) : PdfReportOutcome
    /** Nothing countable happened in the period (no marked, auto-credited or cancelled sessions). */
    data class NoRecords(val message: String) : PdfReportOutcome
    /** Only auto-credited hours exist, so any percentage would be misleading. */
    data class InsufficientData(val message: String) : PdfReportOutcome
    data class Invalid(val message: String) : PdfReportOutcome
}

data class PdfTargetStatus(
    val label: String,
    val targetPercent: Int,
    val met: Boolean,
    val detail: String
)

data class PdfSubjectRow(
    val name: String,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val percentText: String,
    val isAutoCredited: Boolean,
    /** True when the subject is at/above the exam target (exact weighted comparison). */
    val meetsExamTarget: Boolean
)

enum class PdfDetailStatus(val label: String) {
    PRESENT("Present"),
    ABSENT("Absent"),
    NO_CLASS("No Class"),
    HOLIDAY("Holiday")
}

data class PdfDetailRow(
    val dateLabel: String,
    val subject: String,
    val status: PdfDetailStatus
)

/** Everything the renderer needs. Contains no logic and no Android types. */
data class PdfReportData(
    val reportType: PdfReportType,
    val periodType: PdfPeriodType,
    val studentName: String,
    /** Label/value pairs such as "Roll No." -> "123". Blank values are already removed. */
    val studentDetails: List<Pair<String, String>>,
    val periodLabel: String,
    /** Non-null when the period is still running, e.g. "To date (through 02 Nov 2026)". */
    val toDateNote: String?,
    val generatedOn: String,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val percentText: String,
    val meetsExamTarget: Boolean,
    val targets: List<PdfTargetStatus>,
    val assessment: String,
    val subjects: List<PdfSubjectRow>,
    /** Footnotes (auto-credited hours, unmarked past sessions). */
    val footnotes: List<String>,
    /** Null for Summary reports. */
    val detailRows: List<PdfDetailRow>?,
    val fileName: String
)

/** Quick helper so UI code can validate Custom Range before generation. */
object PdfRangeValidator {
    /** Returns an error message, or null when the range is valid. */
    fun validateCustom(start: LocalDate?, end: LocalDate?, today: LocalDate): String? = when {
        start == null || end == null -> "Choose both a start and an end date."
        start.isAfter(end) -> "The start date can't be after the end date."
        end.isAfter(today) -> "The end date can't be in the future."
        start.isAfter(today) -> "The start date can't be in the future."
        else -> null
    }
}
