package com.example.data.reports

import androidx.compose.runtime.Immutable
import com.example.data.math.AttendanceMath
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.timetable.TimetableMatrix
import java.time.LocalDate

enum class InsightSeverity { POSITIVE, INFO, WARNING, CRITICAL }

@Immutable
data class Insight(
    val id: String,
    val severity: InsightSeverity,
    val icon: String,
    val title: String,
    val detail: String
)

/**
 * Estimate for the unrecorded, still-to-come scheduled hours between today and the end of the
 * reporting period (max 14 days). Auto-credited sessions are left out. These are what-if
 * numbers; nothing is written to attendance records.
 */
@Immutable
data class ForecastResult(
    val windowStart: String,
    val windowEnd: String,
    val remainingHours: Int,
    val remainingSessions: Int,
    val ifAttendAllPercentage: Double,
    val ifMissAllPercentage: Double,
    val examReachable: Boolean,
    val stipendReachable: Boolean,
    /** Hours of the window you could still miss and finish at/above the target; null when unreachable. */
    val missBudgetExam: Int?,
    val missBudgetStipend: Int?
)

/** Rule-based, explainable insights. Every statement is derived from [ReportAnalytics] numbers. */
object ReportInsightsEngine {

    fun forecast(
        current: ReportAnalytics,
        records: Map<String, Map<Int, PeriodStatus>>,
        isHoliday: (String) -> Boolean,
        profile: StudentProfile,
        today: LocalDate
    ): ForecastResult? {
        if (!current.hasManualData) return null
        if (current.range.end.isBefore(today)) return null // historical period: nothing left to forecast
        val start = if (today.isBefore(current.range.start)) current.range.start else today
        val horizonEnd = today.plusDays(13)
        val end = if (current.range.end.isBefore(horizonEnd)) current.range.end else horizonEnd
        if (end.isBefore(start)) return null

        var hours = 0
        var sessions = 0
        for (date in ReportDateRange(start, end).dates()) {
            val dow = date.dayOfWeek.value
            if (dow !in 1..5) continue
            val iso = date.toString()
            if (isHoliday(iso)) continue
            val day = records[iso]
            for (slot in TimetableMatrix.getScheduleForDay(dow, profile.batchEnum, profile.courseSelectionEnum)) {
                if (slot.subject.isAutoPresent) continue
                val stored = day?.get(slot.periodNumber)
                if (stored == null || stored == PeriodStatus.PENDING) {
                    hours += slot.subject.creditHours
                    sessions++
                }
            }
        }
        if (hours == 0) return null

        val att = current.attendedCredits
        val cond = current.conductedCredits
        fun budget(target: Int): Int? {
            val total = (cond + hours).toLong()
            val need = ceilDiv(target.toLong() * total - 100L * att, 100L).coerceAtLeast(0L)
            return if (need > hours) null else (hours - need).toInt()
        }
        val bExam = budget(current.examTarget)
        val bStip = budget(current.stipendTarget)
        return ForecastResult(
            windowStart = start.toString(),
            windowEnd = end.toString(),
            remainingHours = hours,
            remainingSessions = sessions,
            ifAttendAllPercentage = AttendanceMath.calculatePercentage(att + hours, cond + hours),
            ifMissAllPercentage = AttendanceMath.calculatePercentage(att, cond + hours),
            examReachable = bExam != null,
            stipendReachable = bStip != null,
            missBudgetExam = bExam,
            missBudgetStipend = bStip
        )
    }

    fun generate(
        current: ReportAnalytics,
        previous: ReportAnalytics?,
        forecast: ForecastResult?
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val pct = current.percentage

        if (current.hasManualData) {
            // Overall standing against configured targets
            if (pct < current.examTarget) {
                out += Insight(
                    "below_exam", InsightSeverity.CRITICAL, "🚨",
                    "Below the ${current.examTarget}% exam target",
                    recoveryText("overall", current.recoveryHoursExam, current.examTarget, pct)
                )
            } else if (pct < current.stipendTarget) {
                out += Insight(
                    "below_stipend", InsightSeverity.WARNING, "⚠️",
                    "Below the ${current.stipendTarget}% stipend target",
                    recoveryText("overall", current.recoveryHoursStipend, current.stipendTarget, pct)
                )
            } else {
                out += Insight(
                    "targets_met", InsightSeverity.POSITIVE, "✅",
                    "Both targets met",
                    "At ${fmt(pct)}% you are above the exam (${current.examTarget}%) and stipend " +
                        "(${current.stipendTarget}%) targets. You can miss up to ${current.safeSkipHoursStipend} more " +
                        "weighted hour(s) and stay at or above the stipend target."
                )
            }

            // Target progress
            if (pct < current.stipendTarget) {
                out += Insight(
                    "target_progress", InsightSeverity.INFO, "🎯",
                    "Progress to stipend target",
                    "${fmt(pct)}% of ${current.stipendTarget}% (${fmt(current.stipendTarget - pct)} points to go)."
                )
            }

            // Milestones
            if (pct >= 90.0) {
                out += Insight("milestone_90", InsightSeverity.POSITIVE, "🏆", "90%+ attendance milestone", "Your weighted attendance is ${fmt(pct)}% for this period.")
            } else if (pct >= current.stipendTarget + current.safetyBuffer) {
                out += Insight("milestone_safe", InsightSeverity.POSITIVE, "🛡️", "In the completely safe zone", "You are at least ${current.safetyBuffer} points above the stipend target.")
            }

            // Subject-level rules (auto-credited subjects excluded: they are not manually attended)
            val graded = current.subjects.filter { it.conductedCredits > 0 && !it.subject.isAutoPresent }
            if (graded.size >= 2) {
                val best = graded.maxByOrNull { it.percentage }
                val worst = graded.minByOrNull { it.percentage }
                if (best != null && worst != null) {
                    out += Insight(
                    "best_subject", InsightSeverity.POSITIVE, "🌟",
                    "Best subject: ${best.subject.shortName}",
                    "${fmt(best.percentage)}% (${best.attendedCredits}/${best.conductedCredits} hrs)."
                )
                if (worst.subject.code != best.subject.code) {
                    val sev = if (worst.percentage < current.stipendTarget) InsightSeverity.WARNING else InsightSeverity.INFO
                    out += Insight(
                        "lowest_subject", sev, "📉",
                        "Lowest subject: ${worst.subject.shortName}",
                        "${fmt(worst.percentage)}% (${worst.attendedCredits}/${worst.conductedCredits} hrs)."
                    )
                }
            }
        }
            val belowExam = graded.filter { it.percentage < current.examTarget }
            val belowStipend = graded.filter { it.percentage >= current.examTarget && it.percentage < current.stipendTarget }
            if (belowExam.isNotEmpty()) {
                out += Insight(
                    "subjects_below_exam", InsightSeverity.CRITICAL, "📕",
                    "${belowExam.size} subject(s) below ${current.examTarget}%",
                    nameList(belowExam.map { it.subject.shortName })
                )
            }
            if (belowStipend.isNotEmpty()) {
                out += Insight(
                    "subjects_below_stipend", InsightSeverity.WARNING, "📙",
                    "${belowStipend.size} subject(s) below ${current.stipendTarget}%",
                    nameList(belowStipend.map { it.subject.shortName })
                )
            }

            // Recovery recommendation for the weakest subject that is under a target
            graded.filter { it.percentage < current.stipendTarget }
                .minByOrNull { it.percentage }?.let { s ->
                    val target = if (s.percentage < current.examTarget) current.examTarget else current.stipendTarget
                    val hrs = if (target == current.examTarget) s.recoveryHoursExam else s.recoveryHoursStipend
                    out += Insight(
                        "recovery_subject", InsightSeverity.WARNING, "🛠️",
                        "Recover ${s.subject.shortName}",
                        recoveryText(s.subject.shortName, hrs, target, s.percentage)
                    )
                }

            // Practical weight advice (2-hour, manually attended subjects below the stipend target)
            graded.filter { it.subject.creditHours >= 2 && it.percentage < current.stipendTarget }
                .forEach { s ->
                    out += Insight(
                        "practical_${s.subject.code}", InsightSeverity.INFO, "🧪",
                        "${s.subject.shortName} carries ${s.subject.creditHours}x weight",
                        "Each session counts ${s.subject.creditHours} hours, so attending it lifts weighted attendance faster than a normal lecture."
                    )
                }

            // Change versus the previous comparable period
            if (previous != null) {
                ReportAnalyticsEngine.deltaVersusPrevious(current, previous)?.let { d ->
                    if (d != 0.0) {
                        val up = d > 0
                        out += Insight(
                            "delta", if (up) InsightSeverity.POSITIVE else InsightSeverity.WARNING,
                            if (up) "📈" else "📉",
                            if (up) "Improved vs previous period" else "Dropped vs previous period",
                            "${if (up) "+" else ""}${fmt(d)} percentage points (${fmt(previous.percentage)}% → ${fmt(pct)}%)."
                        )
                    }
                }
            }

            // Forecast (estimate only)
            forecast?.let { f ->
                out += Insight(
                    "forecast", InsightSeverity.INFO, "🔮",
                    "Estimate: next ${f.remainingHours} scheduled hour(s)",
                    "Attending all of them would give about ${fmt(f.ifAttendAllPercentage)}%; missing all would give about ${fmt(f.ifMissAllPercentage)}%."
                )
                out += Insight(
                    "forecast_budget", if (f.stipendReachable) InsightSeverity.INFO else InsightSeverity.CRITICAL, "🧮",
                    "Estimate: how many you can miss",
                    forecastBudgetText(f, current)
                )
            }
        }

        // Data quality (shown even with no confirmed data)
        if (current.counts.pending > 0) {
            val hrs = current.subjects.flatMap { it.sessions }
                .filter { it.status == ReportSessionStatus.PENDING }.sumOf { it.weight }
            out += Insight(
                "pending", InsightSeverity.INFO, "🕒",
                "${current.counts.pending} unrecorded session(s)",
                "$hrs weighted hour(s) in this period have no attendance mark yet. They are not counted as present or absent."
            )
        }
        return out
    }

    /** Ordered, concrete next steps derived from the same numbers. */
    fun actionPlan(current: ReportAnalytics, forecast: ForecastResult?): List<String> {
        if (!current.hasManualData) return emptyList()
        val steps = mutableListOf<String>()
        if (current.counts.pending > 0) steps += "Mark the ${current.counts.pending} unrecorded session(s) so your numbers are complete."
        if (current.percentage < current.examTarget && current.recoveryHoursExam > 0) {
            steps += "Attend the next ${current.recoveryHoursExam} weighted hour(s) without a miss to reach ${current.examTarget}%."
        } else if (current.percentage < current.stipendTarget && current.recoveryHoursStipend > 0) {
            steps += "Attend the next ${current.recoveryHoursStipend} weighted hour(s) without a miss to reach ${current.stipendTarget}%."
        }
        current.subjects
            .filter { it.conductedCredits > 0 && !it.subject.isAutoPresent && it.percentage < current.stipendTarget }
            .sortedBy { it.percentage }
            .take(2)
            .forEach { s ->
                val target = if (s.percentage < current.examTarget) current.examTarget else current.stipendTarget
                val hrs = if (target == current.examTarget) s.recoveryHoursExam else s.recoveryHoursStipend
                if (hrs > 0) steps += "Prioritise ${s.subject.shortName}: ${hrs} hour(s) needed to reach $target%."
            }
        if (steps.none { it.startsWith("Attend") } && current.percentage >= current.stipendTarget) {
            steps += "You are on target. Keep skipping within your ${current.safeSkipHoursStipend}-hour safe limit."
        }
        forecast?.let { if (!it.stipendReachable) steps += "Stipend target cannot be reached within the next ${it.remainingHours} scheduled hour(s); plan for a longer recovery." }
        return steps
    }

    private fun recoveryText(who: String, hours: Int, target: Int, pct: Double): String = when {
        hours < 0 -> "A $target% target can no longer be reached for $who (currently ${fmt(pct)}%)."
        hours == 0 -> "$who is at ${fmt(pct)}%."
        else -> "Attend the next $hours weighted hour(s) in a row to bring $who from ${fmt(pct)}% up to $target%."
    }

    private fun forecastBudgetText(f: ForecastResult, a: ReportAnalytics): String {
        val e = f.missBudgetExam?.let { "$it hr(s) and stay at ${a.examTarget}%" } ?: "none (${a.examTarget}% unreachable in this window)"
        val s = f.missBudgetStipend?.let { "$it hr(s) and stay at ${a.stipendTarget}%" } ?: "none (${a.stipendTarget}% unreachable in this window)"
        return "Of the next ${f.remainingHours} scheduled hour(s) you could miss up to $s; for the exam target: $e."
    }

    private fun nameList(names: List<String>): String =
        if (names.size <= 4) names.joinToString(", ") else names.take(4).joinToString(", ") + " +${names.size - 4} more"

    private fun fmt(d: Double): String = String.format(java.util.Locale.US, "%.1f", d)

    private fun ceilDiv(a: Long, b: Long): Long = if (a <= 0L) 0L else (a + b - 1L) / b
}
