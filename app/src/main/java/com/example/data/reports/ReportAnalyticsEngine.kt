package com.example.data.reports

import androidx.compose.runtime.Immutable
import com.example.data.math.AttendanceMath
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.model.Subject
import com.example.data.model.TierStatus
import com.example.data.timetable.TimetableMatrix
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Per-session status as seen by Reports. PENDING / UPCOMING never contribute to totals. */
enum class ReportSessionStatus { PRESENT, AUTO_PRESENT, ABSENT, NO_CLASS, PENDING, UPCOMING }

@Immutable
data class SessionEntry(
    val date: String,
    val periodNumber: Int,
    val periodLabel: String,
    val subject: Subject,
    val status: ReportSessionStatus,
    val weight: Int,
    val isTutorial: Boolean,
    val isTwoHourBlock: Boolean
)

/** Session counts (not weighted). */
@Immutable
data class StatusCounts(
    val present: Int = 0,
    val autoPresent: Int = 0,
    val absent: Int = 0,
    val noClass: Int = 0,
    val pending: Int = 0,
    val upcoming: Int = 0
)

/** Calendar status of one date. */
enum class DayStatus { HOLIDAY, WEEKEND, CANCELLED, ABSENT, PRESENT, INCOMPLETE, PENDING, UPCOMING, NO_SCHEDULE }

/** Everything the calendars need to draw and explain one date. */
@Immutable
data class DayReport(
    val date: String,
    /** 1 = Monday .. 7 = Sunday */
    val dayOfWeek: Int,
    val status: DayStatus,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val sessions: List<SessionEntry>
)

@Immutable
data class SubjectReport(
    val subject: Subject,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val percentage: Double,
    /** Hours still needed to reach the target; -1 means mathematically unreachable (100% target after a miss). */
    val recoveryHoursExam: Int,
    val recoveryHoursStipend: Int,
    val safeSkipHoursExam: Int,
    val safeSkipHoursStipend: Int,
    val tierStatus: TierStatus,
    val counts: StatusCounts,
    val sessions: List<SessionEntry>
)

/** One day with at least one confirmed session, plus running totals up to and including it. */
@Immutable
data class DailyPoint(
    val date: String,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val cumulativeAttended: Int,
    val cumulativeConducted: Int
) {
    val dayPercentage: Double?
        get() = if (conductedCredits > 0) AttendanceMath.calculatePercentage(attendedCredits, conductedCredits) else null
    val cumulativePercentage: Double?
        get() = if (cumulativeConducted > 0) AttendanceMath.calculatePercentage(cumulativeAttended, cumulativeConducted) else null
}

@Immutable
data class ReportAnalytics(
    val range: ReportDateRange,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val percentage: Double,
    val examTarget: Int,
    val stipendTarget: Int,
    val safetyBuffer: Int,
    val isExamEligible: Boolean,
    val isStipendEligible: Boolean,
    val tierStatus: TierStatus,
    val recoveryHoursExam: Int,
    val recoveryHoursStipend: Int,
    val safeSkipHoursExam: Int,
    val safeSkipHoursStipend: Int,
    val autoPresentBoostHours: Int,
    val counts: StatusCounts,
    /** Mon-Fri days in range that are not holidays. */
    val workingDays: Int,
    /** Mon-Fri holidays in range. */
    val holidayDays: Int,
    val holidayDates: List<String>,
    /** Working days with at least one confirmed (Present/Absent/Auto) session. */
    val recordedDays: Int,
    val subjects: List<SubjectReport>,
    val daily: List<DailyPoint>,
    /** One entry per calendar date in the range (weekends and holidays included). */
    val days: List<DayReport>
) {
    /** True when there is at least one confirmed session, i.e. a percentage is meaningful. */
    val hasData: Boolean get() = conductedCredits > 0

    /**
     * True when the student actually marked at least one Present/Absent session. Auto-credited
     * hours alone do not count, so comparisons, forecasts and archive months never rest on them.
     */
    val hasManualData: Boolean get() = counts.present + counts.absent > 0

    /** Weighted hours missed (conducted but not attended). */
    val missedCredits: Int = (conductedCredits - attendedCredits).coerceAtLeast(0)

    /** Weighted hours of sessions marked No Class (excluded from attended and conducted). */
    val cancelledCredits: Int =
        subjects.sumOf { s -> s.sessions.filter { it.status == ReportSessionStatus.NO_CLASS }.sumOf { it.weight } }
}

@Immutable
data class ArchiveEntry(
    val monthId: String,
    val label: String,
    val percentage: Double?,
    val conductedCredits: Int,
    val isCurrentMonth: Boolean
)

/**
 * The single place where Reports numbers are computed. It reuses [AttendanceMath] for every
 * percentage / recovery / safe-skip figure and [TimetableMatrix] for the schedule, and never
 * mutates attendance records.
 *
 * Status rules (kept identical to AttendanceRepository):
 *  - PRESENT / AUTO_PRESENT / ABSENT are counted (weight = subject.creditHours).
 *  - Dates after today never count, even when future marking is enabled (UPCOMING).
 *  - Auto-present slots (Library, Seminar...) are credited on past/today working days even with
 *    no stored record, unless cancelled (NO_CLASS).
 *  - NO_CLASS is excluded from attended and conducted and reported separately.
 *  - Any other scheduled period with no stored status is PENDING; it counts as neither.
 *  - Holidays (official or overridden) are excluded entirely and reported separately.
 */
object ReportAnalyticsEngine {
    // NOTE: percentages, recovery and safe-skip come from AttendanceMath, whose recovery function
    // returns -1 when a 100% target is unreachable. UI code must treat -1 as "not reachable".


    fun analyze(
        range: ReportDateRange,
        records: Map<String, Map<Int, PeriodStatus>>,
        isHoliday: (String) -> Boolean,
        profile: StudentProfile,
        today: LocalDate
    ): ReportAnalytics {
        val batch = profile.batchEnum
        val course = profile.courseSelectionEnum

        // Keyed by subject.code so lecture + tutorial entries of one subject aggregate together
        // (same rule as the repository). Practicals/labs have their own codes and stay separate.
        val subjectsByCode = LinkedHashMap<String, Subject>()
        val sessionsBySubject = LinkedHashMap<String, MutableList<SessionEntry>>()
        TimetableMatrix.getAllActiveSubjects(course).forEach {
            subjectsByCode.getOrPut(it.code) { it }
            sessionsBySubject.getOrPut(it.code) { mutableListOf() }
        }

        var workingDays = 0
        var holidayDays = 0
        var recordedDays = 0
        val holidayDates = mutableListOf<String>()
        val daily = mutableListOf<DailyPoint>()
        val days = mutableListOf<DayReport>()
        var cumAtt = 0
        var cumCond = 0

        for (date in range.dates()) {
            val dow = date.dayOfWeek.value
            val iso = date.toString()
            if (dow !in 1..5) {
                // Weekend: no classes, but an official holiday falling on it is still shown as a holiday.
                days.add(DayReport(iso, dow, if (isHoliday(iso)) DayStatus.HOLIDAY else DayStatus.WEEKEND, 0, 0, emptyList()))
                continue
            }
            if (isHoliday(iso)) {
                holidayDays++
                holidayDates.add(iso)
                days.add(DayReport(iso, dow, DayStatus.HOLIDAY, 0, 0, emptyList()))
                continue
            }
            workingDays++
            val daySessions = mutableListOf<SessionEntry>()

            val record = records[iso]
            var dayAtt = 0
            var dayCond = 0
            for (slot in TimetableMatrix.getScheduleForDay(dow, batch, course)) {
                val stored = record?.get(slot.periodNumber)
                val auto = slot.subject.isAutoPresent
                // Same rules as AttendanceRepository: dates after today never count (even if
                // marked), auto-present slots on past/today working days are credited without a
                // stored record, and PENDING / NO_CLASS contribute nothing.
                val status = if (date.isAfter(today)) {
                    if (stored == PeriodStatus.NO_CLASS) ReportSessionStatus.NO_CLASS else ReportSessionStatus.UPCOMING
                } else when (stored) {
                    PeriodStatus.NO_CLASS -> ReportSessionStatus.NO_CLASS
                    PeriodStatus.PRESENT -> ReportSessionStatus.PRESENT
                    PeriodStatus.ABSENT -> if (auto) ReportSessionStatus.AUTO_PRESENT else ReportSessionStatus.ABSENT
                    PeriodStatus.AUTO_PRESENT -> ReportSessionStatus.AUTO_PRESENT
                    PeriodStatus.PENDING, null -> if (auto) ReportSessionStatus.AUTO_PRESENT else ReportSessionStatus.PENDING
                }
                val weight = slot.subject.creditHours
                when (status) {
                    ReportSessionStatus.PRESENT, ReportSessionStatus.AUTO_PRESENT -> {
                        dayAtt += weight; dayCond += weight
                    }
                    ReportSessionStatus.ABSENT -> dayCond += weight
                    else -> Unit
                }
                val entry = SessionEntry(
                    date = iso,
                    periodNumber = slot.periodNumber,
                    periodLabel = slot.periodLabel,
                    subject = slot.subject,
                    status = status,
                    weight = weight,
                    isTutorial = slot.isTutorial,
                    isTwoHourBlock = slot.isTwoHourBlock
                )
                sessionsBySubject.getOrPut(slot.subject.code) { mutableListOf() }.add(entry)
                daySessions.add(entry)
            }
            days.add(DayReport(iso, dow, classifyDay(daySessions, date, today), dayAtt, dayCond, daySessions))
            if (dayCond > 0) {
                recordedDays++
                cumAtt += dayAtt
                cumCond += dayCond
                daily.add(DailyPoint(iso, dayAtt, dayCond, cumAtt, cumCond))
            }
        }

        val subjectReports = sessionsBySubject.map { (code, sessions) ->
            buildSubjectReport(subjectsByCode.getValue(code), sessions, profile)
        }

        val attended = subjectReports.sumOf { it.attendedCredits }
        val conducted = subjectReports.sumOf { it.conductedCredits }
        val autoBoost = sessionsBySubject.values.flatten()
            .filter { it.status == ReportSessionStatus.AUTO_PRESENT }
            .sumOf { it.weight }
        val counts = mergeCounts(subjectReports.map { it.counts })

        val pct = percentage(attended, conducted)
        return ReportAnalytics(
            range = range,
            attendedCredits = attended,
            conductedCredits = conducted,
            percentage = pct,
            examTarget = profile.examTarget,
            stipendTarget = profile.stipendTarget,
            safetyBuffer = profile.safetyBuffer,
            isExamEligible = conducted > 0 && pct >= profile.examTarget,
            isStipendEligible = conducted > 0 && pct >= profile.stipendTarget,
            tierStatus = AttendanceMath.calculateTierStatus(pct, profile.stipendTarget, profile.safetyBuffer, conducted),
            recoveryHoursExam = AttendanceMath.calculateRecoveryHours(attended, conducted, profile.examTarget),
            recoveryHoursStipend = AttendanceMath.calculateRecoveryHours(attended, conducted, profile.stipendTarget),
            safeSkipHoursExam = AttendanceMath.calculateSafeSkipHours(attended, conducted, profile.examTarget),
            safeSkipHoursStipend = AttendanceMath.calculateSafeSkipHours(attended, conducted, profile.stipendTarget),
            autoPresentBoostHours = autoBoost,
            counts = counts,
            workingDays = workingDays,
            holidayDays = holidayDays,
            holidayDates = holidayDates,
            recordedDays = recordedDays,
            subjects = subjectReports,
            daily = daily,
            days = days
        )
    }

    /**
     * Day colour rule (mirrors the old app's calendar, extended for Pending/Upcoming):
     * any confirmed manual absence -> ABSENT; every live manual session present -> PRESENT;
     * some marked, some unmarked -> INCOMPLETE; all manual sessions cancelled -> CANCELLED;
     * nothing marked -> PENDING (today/past) or UPCOMING (future).
     * Auto-credited subjects (Library, Seminar...) never decide the colour on their own.
     */
    internal fun classifyDay(sessions: List<SessionEntry>, date: LocalDate, today: LocalDate): DayStatus {
        if (sessions.isEmpty()) return DayStatus.NO_SCHEDULE
        val manual = sessions.filter { !it.subject.isAutoPresent }
        val live = manual.filter { it.status != ReportSessionStatus.NO_CLASS }
        if (live.isEmpty()) return DayStatus.CANCELLED
        val marked = live.filter { it.status == ReportSessionStatus.PRESENT || it.status == ReportSessionStatus.ABSENT }
        if (marked.isEmpty()) return if (date.isAfter(today)) DayStatus.UPCOMING else DayStatus.PENDING
        if (marked.any { it.status == ReportSessionStatus.ABSENT }) return DayStatus.ABSENT
        return if (marked.size == live.size) DayStatus.PRESENT else DayStatus.INCOMPLETE
    }

    private fun buildSubjectReport(
        subject: Subject,
        sessions: List<SessionEntry>,
        profile: StudentProfile
    ): SubjectReport {
        var att = 0
        var cond = 0
        var present = 0; var auto = 0; var absent = 0; var noClass = 0; var pending = 0; var upcoming = 0
        sessions.forEach { s ->
            when (s.status) {
                ReportSessionStatus.PRESENT -> { att += s.weight; cond += s.weight; present++ }
                ReportSessionStatus.AUTO_PRESENT -> { att += s.weight; cond += s.weight; auto++ }
                ReportSessionStatus.ABSENT -> { cond += s.weight; absent++ }
                ReportSessionStatus.NO_CLASS -> noClass++
                ReportSessionStatus.PENDING -> pending++
                ReportSessionStatus.UPCOMING -> upcoming++
            }
        }
        val pct = percentage(att, cond)
        return SubjectReport(
            subject = subject,
            attendedCredits = att,
            conductedCredits = cond,
            percentage = pct,
            recoveryHoursExam = AttendanceMath.calculateRecoveryHours(att, cond, profile.examTarget),
            recoveryHoursStipend = AttendanceMath.calculateRecoveryHours(att, cond, profile.stipendTarget),
            safeSkipHoursExam = AttendanceMath.calculateSafeSkipHours(att, cond, profile.examTarget),
            safeSkipHoursStipend = AttendanceMath.calculateSafeSkipHours(att, cond, profile.stipendTarget),
            tierStatus = AttendanceMath.calculateTierStatus(pct, profile.stipendTarget, profile.safetyBuffer, cond),
            counts = StatusCounts(present, auto, absent, noClass, pending, upcoming),
            sessions = sessions
        )
    }

    private fun mergeCounts(all: List<StatusCounts>) = StatusCounts(
        present = all.sumOf { it.present },
        autoPresent = all.sumOf { it.autoPresent },
        absent = all.sumOf { it.absent },
        noClass = all.sumOf { it.noClass },
        pending = all.sumOf { it.pending },
        upcoming = all.sumOf { it.upcoming }
    )

    /** Reuses AttendanceMath; 0 when nothing was conducted (callers must check hasData) and capped at 100. */
    private fun percentage(attended: Int, conducted: Int): Double =
        AttendanceMath.calculatePercentage(attended, conducted).coerceIn(0.0, 100.0)

    /**
     * Percentage-point change versus the previous comparable period, or null when either
     * period has no confirmed sessions (never invents a comparison).
     */
    fun deltaVersusPrevious(current: ReportAnalytics, previous: ReportAnalytics): Double? {
        if (!current.hasManualData || !previous.hasManualData) return null
        return Math.round((current.percentage - previous.percentage) * 10.0) / 10.0
    }

    /**
     * Months that genuinely have attendance history (at least one confirmed session on a
     * non-holiday weekday), plus the current month (so an empty current month can still be
     * opened). Future months are never included. Sorted oldest to newest.
     */
    fun archiveMonths(
        records: Map<String, Map<Int, PeriodStatus>>,
        isHoliday: (String) -> Boolean,
        profile: StudentProfile,
        today: LocalDate
    ): List<ArchiveEntry> {
        val currentMonth = YearMonth.from(today)
        val candidateMonths = records.keys
            .mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() }
            .filter { !it.isAfter(currentMonth) }
            .toMutableSet()
        candidateMonths.add(currentMonth)

        return candidateMonths.sorted().mapNotNull { ym ->
            val range = ReportDateRange(ym.atDay(1), ym.atEndOfMonth())
            val analytics = analyze(range, records, isHoliday, profile, today)
            val isCurrent = ym == currentMonth
            if (!isCurrent && !analytics.hasManualData) null
            else ArchiveEntry(
                monthId = ym.toString(),
                label = monthLabel(ym, short = true),
                percentage = if (analytics.hasData) analytics.percentage else null,
                conductedCredits = analytics.conductedCredits,
                isCurrentMonth = isCurrent
            )
        }
    }

    fun monthLabel(monthId: String, short: Boolean = false): String =
        runCatching { monthLabel(YearMonth.parse(monthId), short) }.getOrDefault(monthId)

    fun monthLabel(ym: YearMonth, short: Boolean = false): String {
        val name = ym.month.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.ENGLISH)
        return "$name ${ym.year}"
    }
}
