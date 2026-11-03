package com.example.data.pdf

import com.example.data.reports.ReportDateRange
import java.time.LocalDate

/** The user's current choices on the configuration screen. Survives previewing and rotation. */
data class PdfConfig(
    val periodType: PdfPeriodType = PdfPeriodType.MONTHLY,
    val reportType: PdfReportType = PdfReportType.SUMMARY,
    /** "YYYY-MM" for Monthly. */
    val monthId: String? = null,
    /** Monday of the chosen week for Weekly. */
    val weekStart: LocalDate? = null,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null
)

/** Turns a [PdfConfig] into a validated range, reusing the app's [ReportDateRange] conventions. */
object PdfConfigResolver {
    /** Returns (range, null) when valid, otherwise (null, user-facing message). */
    fun resolve(cfg: PdfConfig, today: LocalDate): Pair<ReportDateRange?, String?> = when (cfg.periodType) {
        PdfPeriodType.WEEKLY -> {
            val ws = cfg.weekStart
            when {
                ws == null -> null to "Choose a week."
                ws.isAfter(ReportDateRange.weekContaining(today).start) -> null to "That week hasn't started yet."
                else -> ReportDateRange.weekContaining(ws) to null
            }
        }
        PdfPeriodType.MONTHLY -> {
            val r = cfg.monthId?.let { ReportDateRange.ofMonth(it) }
            when {
                r == null -> null to "Choose a month."
                r.start.isAfter(today) -> null to "That month hasn't started yet."
                else -> r to null
            }
        }
        PdfPeriodType.CUSTOM -> {
            val err = PdfRangeValidator.validateCustom(cfg.customStart, cfg.customEnd, today)
            if (err != null) null to err else ReportDateRange.custom(cfg.customStart!!, cfg.customEnd!!) to null
        }
    }
}
