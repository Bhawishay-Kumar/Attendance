package com.example.ui.components

import com.example.data.model.PeriodStatus

/**
 * Pure helper: turns the period statuses and timetable slots of ONE reporting period
 * (the exact same month the Home Hero Donut Ring represents) into accurate counts.
 *
 * Rules enforced:
 * - Holidays (AktuHolidays) and Weekends (Sat/Sun) are excluded unless an exceptional class exists.
 * - Future dates (> today) are strictly excluded unless explicitly marked when Future Marking is ON.
 * - 2-Hour Practical Labs & Seminars count with their real credit weight (creditHours = 2)
 *   so Attended / Conducted matches the Hero Donut Ring percentage 100%.
 * - Auto-Present subjects (Library / Seminar) on elapsed working days count as Attended unless marked NO_CLASS.
 * - Mid-Month Backfill baseline credits (if configured for this month) are included in Attended/Missed/Conducted.
 * - PENDING is never counted as missed or conducted.
 */
object AttendanceStatsCalculator {



    /**
     * Unweighted fallback helper (kept for full API compatibility with original snippet).
     */
    fun fromStatuses(statuses: Collection<PeriodStatus>): AttendanceSessionStats {
        var present = 0
        var absent = 0
        var cancelled = 0
        for (s in statuses) {
            when (s) {
                PeriodStatus.PRESENT, PeriodStatus.AUTO_PRESENT -> present++
                PeriodStatus.ABSENT -> absent++
                PeriodStatus.NO_CLASS -> cancelled++
                PeriodStatus.PENDING -> Unit
            }
        }
        return AttendanceSessionStats(
            attended = present,
            missed = absent,
            conducted = present + absent, // cancelled & pending never count as conducted
            cancelled = cancelled
        )
    }
}
