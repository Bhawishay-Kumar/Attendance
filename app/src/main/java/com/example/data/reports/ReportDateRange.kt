package com.example.data.reports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The reporting modes exposed by the Reports screen. */
enum class ReportTimeframe { MONTHLY, WEEKLY }

/**
 * An inclusive calendar date range used by every Reports calculation.
 * Pure Kotlin / java.time so it can be unit-tested on the JVM.
 */
data class ReportDateRange(val start: LocalDate, val end: LocalDate) {

    init {
        require(!end.isBefore(start)) { "Report range end ($end) is before start ($start)" }
    }

    val startIso: String get() = start.toString()
    val endIso: String get() = end.toString()
    val dayCount: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun dates(): List<LocalDate> = List(dayCount) { start.plusDays(it.toLong()) }

    /** True when this range is exactly one whole calendar month. */
    val isWholeMonth: Boolean
        get() = start.dayOfMonth == 1 && end == YearMonth.from(start).atEndOfMonth()

    /**
     * The period immediately before this one, used for "vs previous period" comparisons.
     * A whole month compares with the previous whole month; anything else compares with the
     * preceding range of the same length.
     */
    fun previousComparable(): ReportDateRange {
        if (isWholeMonth) {
            val prev = YearMonth.from(start).minusMonths(1)
            return ReportDateRange(prev.atDay(1), prev.atEndOfMonth())
        }
        val len = dayCount.toLong()
        return ReportDateRange(start.minusDays(len), start.minusDays(1))
    }

    companion object {
        /** monthId is "YYYY-MM". Returns null for malformed ids. */
        fun ofMonth(monthId: String): ReportDateRange? =
            runCatching { YearMonth.parse(monthId) }.getOrNull()
                ?.let { ReportDateRange(it.atDay(1), it.atEndOfMonth()) }

        /** Monday..Sunday week containing [date]. */
        fun weekContaining(date: LocalDate): ReportDateRange {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return ReportDateRange(monday, monday.plusDays(6))
        }

        /** Returns null when the range is invalid (end before start). */
        fun custom(start: LocalDate, end: LocalDate): ReportDateRange? =
            if (end.isBefore(start)) null else ReportDateRange(start, end)

        /** The last [days] calendar days ending today (inclusive). Never includes future dates. */
        fun lastDays(days: Int, today: LocalDate): ReportDateRange =
            ReportDateRange(today.minusDays((days.coerceAtLeast(1) - 1).toLong()), today)

        /** First day of the month containing [monthId] through min(month end, today). */
        fun monthToDate(monthId: String, today: LocalDate): ReportDateRange? {
            val full = ofMonth(monthId) ?: return null
            val end = if (today.isBefore(full.start)) full.start
            else if (today.isAfter(full.end)) full.end else today
            return ReportDateRange(full.start, end)
        }
    }
}
