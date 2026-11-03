package com.example.data.reports

import androidx.compose.runtime.Immutable
import com.example.data.model.PeriodStatus
import java.time.LocalDate

/** Window choices on the Trends tab. All are anchored to the reporting period, never to future dates. */
enum class TrendWindow { LAST_7_DAYS, LAST_30_DAYS, THIS_PERIOD }

@Immutable
data class TrendPoint(
    val date: String,
    val dayPercentage: Double?,
    val cumulativePercentage: Double,
    val cumulativeAttended: Int,
    val cumulativeConducted: Int
)

@Immutable
data class BreakdownSlice(val status: ReportSessionStatus, val sessions: Int, val hours: Int)

@Immutable
data class SubjectComparison(
    val code: String,
    val name: String,
    val percentage: Double,
    val attended: Int,
    val conducted: Int,
    val isAutoPresent: Boolean
)

/** Pure aggregation helpers for the Trends tab. Reads analytics output only; never mutates records. */
object ReportTrendEngine {

    /**
     * The window's date range. The anchor is the reporting range end, clipped to today so future
     * dates are never included. 7/30 days look back from the anchor; THIS_PERIOD is the whole
     * reporting range up to the anchor.
     */
    fun windowRange(window: TrendWindow, reporting: ReportDateRange, today: LocalDate): ReportDateRange {
        val clipped = if (reporting.end.isAfter(today)) today else reporting.end
        val anchor = if (clipped.isBefore(reporting.start)) reporting.start else clipped
        return when (window) {
            TrendWindow.LAST_7_DAYS -> ReportDateRange(anchor.minusDays(6), anchor)
            TrendWindow.LAST_30_DAYS -> ReportDateRange(anchor.minusDays(29), anchor)
            TrendWindow.THIS_PERIOD -> ReportDateRange(reporting.start, anchor)
        }
    }

    /** Running attendance percentage per recorded day inside the analysed range. */
    fun trajectory(analytics: ReportAnalytics): List<TrendPoint> =
        analytics.daily.mapNotNull { d ->
            val cum = d.cumulativePercentage ?: return@mapNotNull null
            TrendPoint(d.date, d.dayPercentage, cum, d.cumulativeAttended, d.cumulativeConducted)
        }

    /**
     * From the first date that has any stored record (on or before [anchor]) up to [anchor];
     * null when there is no history yet.
     */
    fun semesterRange(records: Map<String, Map<Int, PeriodStatus>>, anchor: LocalDate): ReportDateRange? {
        val first = records.keys
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(anchor) }
            .minOrNull() ?: return null
        return ReportDateRange(first, anchor)
    }

    fun breakdown(analytics: ReportAnalytics): List<BreakdownSlice> {
        val all = analytics.subjects.flatMap { it.sessions }
        return listOf(
            ReportSessionStatus.PRESENT,
            ReportSessionStatus.AUTO_PRESENT,
            ReportSessionStatus.ABSENT,
            ReportSessionStatus.NO_CLASS,
            ReportSessionStatus.PENDING
        ).map { st ->
            val of = all.filter { it.status == st }
            BreakdownSlice(st, of.size, of.sumOf { it.weight })
        }
    }

    fun subjectComparison(analytics: ReportAnalytics): List<SubjectComparison> =
        analytics.subjects
            .filter { it.conductedCredits > 0 }
            .map {
                SubjectComparison(
                    code = it.subject.code,
                    name = it.subject.shortName,
                    percentage = it.percentage,
                    attended = it.attendedCredits,
                    conducted = it.conductedCredits,
                    isAutoPresent = it.subject.isAutoPresent
                )
            }
            .sortedByDescending { it.percentage }

    /**
     * Current run of fully-present working days, counted back from the latest day that is not
     * upcoming. Weekends, holidays and cancelled days are skipped (they neither extend nor break
     * the run); an absence or an incomplete day ends it. A not-yet-marked "today" is skipped once
     * so an evening before marking does not erase the streak.
     */
    fun presentStreak(days: List<DayReport>): Int {
        var streak = 0
        var skippedPending = false
        for (d in days.asReversed()) {
            when (d.status) {
                DayStatus.UPCOMING, DayStatus.WEEKEND, DayStatus.HOLIDAY,
                DayStatus.CANCELLED, DayStatus.NO_SCHEDULE -> Unit
                DayStatus.PRESENT -> streak++
                DayStatus.PENDING -> {
                    if (streak == 0 && !skippedPending) skippedPending = true else return streak
                }
                else -> return streak
            }
        }
        return streak
    }
}
