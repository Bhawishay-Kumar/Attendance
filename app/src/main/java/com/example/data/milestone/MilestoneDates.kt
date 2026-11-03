package com.example.data.milestone

import com.example.data.model.Milestone
import com.example.data.model.MilestoneRecurrence
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed interface CountdownStatus {
    data class DaysLeft(val days: Long) : CountdownStatus
    data object Today : CountdownStatus
    data class DaysPassed(val days: Long) : CountdownStatus
    data object Completed : CountdownStatus
}

data class ResolvedMilestone(
    val milestone: Milestone,
    /** The occurrence the countdown points at (next occurrence for recurring milestones). */
    val date: LocalDate,
    val status: CountdownStatus,
    /** 0..1, or null when no meaningful start/end pair exists. */
    val progress: Float?
) {
    val id: String get() = milestone.id
}

/** Pure java.time logic (no Android dependencies) so it can be unit-tested on the JVM. */
object MilestoneDates {

    const val REMINDER_HOUR = 9

    fun parse(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(iso.trim()) }.getOrNull()
    }

    /** Valid date of birth: parseable, not in the future, not absurdly old. */
    fun parseDateOfBirth(raw: String?, today: LocalDate = LocalDate.now()): LocalDate? {
        val d = parse(raw) ?: return null
        return d.takeIf { !it.isAfter(today) && it.year >= 1900 }
    }

    /** Feb 29 anniversaries fall on Feb 28 in non-leap years. */
    fun yearlyOn(anchor: LocalDate, year: Int): LocalDate {
        return if (anchor.monthValue == 2 && anchor.dayOfMonth == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            LocalDate.of(year, anchor.month, anchor.dayOfMonth)
        }
    }

    /** Day-of-month is clamped to the month length (31st -> 30th/28th). */
    fun monthlyOn(anchor: LocalDate, ym: YearMonth): LocalDate =
        ym.atDay(minOf(anchor.dayOfMonth, ym.lengthOfMonth()))

    fun occurrence(anchor: LocalDate, recurrence: MilestoneRecurrence, today: LocalDate): LocalDate {
        if (recurrence == MilestoneRecurrence.NONE || !anchor.isBefore(today)) return anchor
        return when (recurrence) {
            MilestoneRecurrence.YEARLY -> {
                val thisYear = yearlyOn(anchor, today.year)
                if (thisYear.isBefore(today)) yearlyOn(anchor, today.year + 1) else thisYear
            }
            MilestoneRecurrence.MONTHLY -> {
                val ym = YearMonth.from(today)
                val thisMonth = monthlyOn(anchor, ym)
                if (thisMonth.isBefore(today)) monthlyOn(anchor, ym.plusMonths(1)) else thisMonth
            }
            MilestoneRecurrence.NONE -> anchor
        }
    }

    fun resolve(m: Milestone, today: LocalDate = LocalDate.now()): ResolvedMilestone? {
        val anchor = parse(m.targetDate) ?: return null
        val occ = occurrence(anchor, m.recurrence, today)

        if (m.completed && m.recurrence == MilestoneRecurrence.NONE) {
            return ResolvedMilestone(m, occ, CountdownStatus.Completed, null)
        }

        val days = ChronoUnit.DAYS.between(today, occ)
        val status = when {
            days > 0 -> CountdownStatus.DaysLeft(days)
            days == 0L -> CountdownStatus.Today
            else -> CountdownStatus.DaysPassed(-days)
        }
        val progress = if (status is CountdownStatus.DaysLeft) progressFor(m, anchor, occ, today) else null
        return ResolvedMilestone(m, occ, status, progress)
    }

    private fun periodStart(m: Milestone, anchor: LocalDate, occ: LocalDate): LocalDate? = when (m.recurrence) {
        MilestoneRecurrence.NONE -> parse(m.startDate)
        MilestoneRecurrence.YEARLY ->
            if (occ.isAfter(anchor)) yearlyOn(anchor, occ.year - 1) else parse(m.startDate)
        MilestoneRecurrence.MONTHLY ->
            if (occ.isAfter(anchor)) monthlyOn(anchor, YearMonth.from(occ).minusMonths(1)) else parse(m.startDate)
    }

    private fun progressFor(m: Milestone, anchor: LocalDate, occ: LocalDate, today: LocalDate): Float? {
        val start = periodStart(m, anchor, occ) ?: return null
        val span = ChronoUnit.DAYS.between(start, occ)
        if (span < 2) return null
        val elapsed = ChronoUnit.DAYS.between(start, today)
        return (elapsed.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    /** Pinned first (stable by pin time), then upcoming/today by date, then passed, then completed. */
    fun sortForDisplay(items: List<ResolvedMilestone>): List<ResolvedMilestone> {
        val pinned = items.filter { it.milestone.pinned }.sortedWith(
            compareBy<ResolvedMilestone> { it.milestone.pinnedAt }
                .thenBy { it.milestone.createdAt }
                .thenBy { it.milestone.id }
        )
        val rest = items.filter { !it.milestone.pinned }.sortedWith(
            compareBy<ResolvedMilestone> { rank(it) }
                .thenBy { if (rank(it) == 0) it.date.toEpochDay() else -it.date.toEpochDay() }
                .thenBy { it.milestone.createdAt }
                .thenBy { it.milestone.id }
        )
        return pinned + rest
    }

    private fun rank(r: ResolvedMilestone): Int = when (r.status) {
        is CountdownStatus.DaysLeft, CountdownStatus.Today -> 0
        is CountdownStatus.DaysPassed -> 1
        CountdownStatus.Completed -> 2
    }

    /**
     * Future reminder instants (daysBefore -> instant) for the milestone's current occurrence.
     * Reminders fire at [REMINDER_HOUR]:00 local time; instants already in the past are skipped.
     */
    fun reminderTimes(
        m: Milestone,
        today: LocalDate,
        zone: ZoneId,
        now: Instant
    ): Map<Int, Instant> {
        if (m.deleted || m.reminderDays.isEmpty()) return emptyMap()
        val r = resolve(m, today) ?: return emptyMap()
        if (r.status is CountdownStatus.Completed) return emptyMap()
        val out = LinkedHashMap<Int, Instant>()
        for (n in m.reminderDays.distinct().sortedDescending()) {
            val at = r.date.minusDays(n.toLong()).atTime(REMINDER_HOUR, 0).atZone(zone).toInstant()
            if (at.isAfter(now)) out[n] = at
        }
        return out
    }

    /** Instant at which a recurring milestone should roll over to its next occurrence (day of event, 09:00). */
    fun rolloverTime(m: Milestone, today: LocalDate, zone: ZoneId, now: Instant): Instant? {
        if (m.deleted || m.recurrence == MilestoneRecurrence.NONE) return null
        val r = resolve(m, today) ?: return null
        val at = r.date.atTime(REMINDER_HOUR, 0).atZone(zone).toInstant()
        return at.takeIf { it.isAfter(now) }
    }
}
