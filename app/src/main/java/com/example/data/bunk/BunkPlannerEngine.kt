package com.example.data.bunk

import com.example.data.holidays.AktuHolidays
import com.example.data.math.AttendanceMath
import com.example.data.math.AttendanceProjection
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.model.Subject
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.timetable.TimetableMatrix
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Pure, read-only Bunk Planner engine. It never touches the repository: everything it needs is
 * passed in, and it returns a result object. No simulated absence is ever persisted.
 *
 * Scope: the CURRENT calendar month. Baseline = the month so far (same figures the Home/Reports
 * month view shows, from [ReportAnalyticsEngine.analyze]); horizon = tomorrow .. month end.
 * Every projection is therefore a MONTH-END projection, not an all-time attendance forecast.
 *
 * Projection assumption: every remaining manual class that is not on a leave date is attended.
 *
 * Eligibility (hard rule): projected overall attendance must meet BOTH configured targets
 * (exam and stipend) on the raw ratio. The safety buffer only decides the COMFORTABLE/TIGHT tier.
 * Subject-level results are warnings and never block or unblock a recommendation.
 */
object BunkPlannerEngine {

    const val MAX_OPTIONS = 3

    fun defaultSchedule(profile: StudentProfile): (Int) -> List<PeriodSlot> = { dow ->
        TimetableMatrix.getScheduleForDay(dow, profile.batchEnum, profile.courseSelectionEnum)
    }

    private class HorizonDay(
        val date: LocalDate,
        val kind: BunkDayKind,
        val label: String?,
        /** Live (non-cancelled) sessions; empty for free days. */
        val sessions: List<PeriodSlot>
    ) {
        val manual: List<PeriodSlot> = sessions.filter { !it.subject.isAutoPresent }
        val manualWeight: Int = manual.sumOf { it.subject.creditHours }
        val manualCount: Int = manual.size
        val weightBySubject: Map<String, Int> = manual.groupBy { it.subject.code }.mapValues { (_, v) -> v.sumOf { it.subject.creditHours } }
        val firstSubjectByCode: Map<String, Subject> = manual.associate { it.subject.code to it.subject }
    }

    private class Candidate(
        val coreStartIdx: Int,
        val coreEndIdx: Int,
        val startIdx: Int,
        val endIdx: Int,
        val bunkDays: List<HorizonDay>
    )

    fun plan(
        profile: StudentProfile,
        records: Map<String, Map<Int, PeriodStatus>>,
        isHoliday: (String) -> Boolean,
        today: LocalDate,
        scheduleFor: (Int) -> List<PeriodSlot> = defaultSchedule(profile)
    ): BunkPlannerResult {
        val month = YearMonth.from(today)
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val horizonStart = today.plusDays(1)
        val exam = profile.examTarget
        val stipend = profile.stipendTarget
        val required = maxOf(exam, stipend)
        val buffer = profile.safetyBuffer

        val analytics = ReportAnalyticsEngine.analyze(
            ReportDateRange(monthStart, monthEnd), records, isHoliday, profile, today
        )
        val monthLabel = ReportAnalyticsEngine.monthLabel(month)
        val baseA = analytics.attendedCredits
        val baseC = analytics.conductedCredits
        val baseBySubject = analytics.subjects.associateBy { it.subject.code }

        // --- Unmarked past sessions (before today): they count for nothing, so they make the baseline provisional.
        var unmarkedPast = 0
        var d = monthStart
        while (d.isBefore(today)) {
            if (d.dayOfWeek.value <= 5 && !isHoliday(d.toString())) {
                val stored = records[d.toString()].orEmpty()
                scheduleFor(d.dayOfWeek.value).forEach { slot ->
                    if (!slot.subject.isAutoPresent) {
                        val s = stored[slot.periodNumber]
                        if (s == null || s == PeriodStatus.PENDING) unmarkedPast++
                    }
                }
            }
            d = d.plusDays(1)
        }

        // --- Remaining sessions. Today: only manual sessions not yet marked (marked and auto ones are already in the baseline).
        val remainingSlots = ArrayList<PeriodSlot>()
        if (today.dayOfWeek.value <= 5 && !isHoliday(today.toString())) {
            val stored = records[today.toString()].orEmpty()
            scheduleFor(today.dayOfWeek.value).forEach { slot ->
                val s = stored[slot.periodNumber]
                if (!slot.subject.isAutoPresent && (s == null || s == PeriodStatus.PENDING)) remainingSlots += slot
            }
        }

        // --- Horizon days (tomorrow .. month end). Never includes a past date.
        val days = ArrayList<HorizonDay>()
        var cur = horizonStart
        while (!cur.isAfter(monthEnd)) {
            val iso = cur.toString()
            val dow = cur.dayOfWeek.value
            val holiday = isHoliday(iso)
            val day = when {
                holiday -> HorizonDay(cur, BunkDayKind.HOLIDAY, AktuHolidays.getHoliday(iso)?.name, emptyList())
                dow > 5 -> HorizonDay(cur, BunkDayKind.WEEKEND, null, emptyList())
                else -> {
                    val stored = records[iso].orEmpty()
                    val live = scheduleFor(dow).filter { stored[it.periodNumber] != PeriodStatus.NO_CLASS }
                    if (live.none { !it.subject.isAutoPresent }) {
                        // Only automatic sessions, or everything cancelled: not a bunk day, but a free day.
                        remainingSlots += live // automatic sessions still add attended+conducted weight
                        HorizonDay(cur, BunkDayKind.NO_CLASS_DAY, null, emptyList())
                    } else {
                        remainingSlots += live
                        HorizonDay(cur, BunkDayKind.BUNK, null, live)
                    }
                }
            }
            days += day
            cur = cur.plusDays(1)
        }

        val remainingWeight = remainingSlots.sumOf { it.subject.creditHours }
        val remainingWeightBySubject = remainingSlots.groupBy { it.subject.code }
            .mapValues { (_, v) -> v.sumOf { it.subject.creditHours } }
        val bunkDays = days.filter { it.kind == BunkDayKind.BUNK }
        val remainingWorkingDays = days.count { it.kind == BunkDayKind.BUNK || it.kind == BunkDayKind.NO_CLASS_DAY }

        val attendAllA = baseA + remainingWeight
        val attendAllC = baseC + remainingWeight
        val attendAllPct = AttendanceProjection.rawPercent(attendAllA, attendAllC)
        val currentPct = if (baseC > 0) AttendanceMath.calculatePercentage(baseA, baseC) else null
        val belowNow = baseC > 0 && !(AttendanceProjection.meetsTarget(baseA, baseC, exam) &&
            AttendanceProjection.meetsTarget(baseA, baseC, stipend))

        val overview = BunkOverview(
            monthLabel = monthLabel,
            currentPercent = currentPct,
            currentAttended = baseA,
            currentConducted = baseC,
            examTarget = exam,
            stipendTarget = stipend,
            requiredTarget = required,
            safetyBuffer = buffer,
            horizonStart = horizonStart,
            horizonEnd = monthEnd,
            remainingSessions = remainingSlots.size,
            remainingWeight = remainingWeight,
            remainingWorkingDays = remainingWorkingDays,
            bunkEligibleDays = bunkDays.size,
            unmarkedPastSessions = unmarkedPast,
            belowTargetNow = belowNow,
            attendAllProjectedPercent = attendAllPct
        )

        if (!analytics.hasManualData || baseC <= 0) {
            return BunkPlannerResult.InsufficientData(
                overview,
                "No classes have been marked Present or Absent yet this month. " +
                    "Only automatically credited hours exist, so there is no reliable baseline to project from."
            )
        }
        if (remainingSlots.isEmpty()) return BunkPlannerResult.NoRemainingClasses(overview)
        if (bunkDays.isEmpty()) {
            return BunkPlannerResult.NoSafeOption(
                overview, NoSafeReason.NO_ELIGIBLE_DAYS, null, emptyList(),
                weakSubjects(baseBySubject, remainingWeightBySubject, exam, stipend)
            )
        }

        // --- Candidate generation: every interval whose first and last day are bunk days; every working
        // day inside is bunked (a break is continuous). Then extend over adjacent FREE days inside the horizon.
        val bunkIdx = days.indices.filter { days[it].kind == BunkDayKind.BUNK }
        val candidates = ArrayList<Candidate>()
        for (i in bunkIdx.indices) for (j in i until bunkIdx.size) {
            val a = bunkIdx[i]
            val b = bunkIdx[j]
            var s = a
            while (s - 1 >= 0 && days[s - 1].kind != BunkDayKind.BUNK) s--
            var e = b
            while (e + 1 < days.size && days[e + 1].kind != BunkDayKind.BUNK) e++
            candidates += Candidate(a, b, s, e, (a..b).map { days[it] }.filter { it.kind == BunkDayKind.BUNK })
        }

        // --- Evaluate
        class Evaluated(
            val c: Candidate,
            val weighted: Int,
            val classes: Int,
            val projA: Int,
            val projC: Int,
            val safe: Boolean,
            val tier: SafetyTier,
            val margin: Double,
            val missedBySubject: Map<String, Int>,
            val impacts: List<SubjectImpact>
        ) {
            val weakestMargin: Double
                get() = impacts.minOfOrNull { it.projectedPercent - required } ?: Double.MAX_VALUE
        }

        val evaluated = candidates.map { c ->
            var weighted = 0
            var classesCount = 0
            val bySubject = mutableMapOf<String, Int>()
            val subjectByCode = mutableMapOf<String, Subject>()
            for (bd in c.bunkDays) {
                weighted += bd.manualWeight
                classesCount += bd.manualCount
                for ((code, w) in bd.weightBySubject) {
                    bySubject[code] = (bySubject[code] ?: 0) + w
                }
                for ((code, sub) in bd.firstSubjectByCode) {
                    subjectByCode.putIfAbsent(code, sub)
                }
            }
            val projA = attendAllA - weighted
            val projC = attendAllC
            val safe = AttendanceProjection.meetsTarget(projA, projC, exam) &&
                AttendanceProjection.meetsTarget(projA, projC, stipend)
            val comfortable = AttendanceProjection.meetsTarget(projA, projC, (required + buffer).coerceAtMost(100))
            val impacts = bySubject.map { (code, m) ->
                val sub = subjectByCode[code] ?: Subject(code, code, code)
                val base = baseBySubject[code]
                val a0 = base?.attendedCredits ?: 0
                val c0 = base?.conductedCredits ?: 0
                val f = remainingWeightBySubject[code] ?: 0
                val pa = a0 + f - m
                val pc = c0 + f
                SubjectImpact(
                    subject = sub,
                    currentAttended = a0,
                    currentConducted = c0,
                    missedWeight = m,
                    projectedAttended = pa,
                    projectedConducted = pc,
                    currentPercent = if (c0 > 0) AttendanceMath.calculatePercentage(a0, c0) else null,
                    projectedPercent = AttendanceProjection.rawPercent(pa, pc) ?: 0.0,
                    status = subjectStatus(pa, pc, exam, stipend)
                )
            }.sortedBy { it.projectedPercent }
            Evaluated(
                c, weighted, classesCount, projA, projC, safe,
                if (comfortable) SafetyTier.COMFORTABLE else SafetyTier.TIGHT,
                (AttendanceProjection.rawPercent(projA, projC) ?: 0.0) - required,
                bySubject, impacts
            )
        }

        val safeOnes = evaluated.filter { it.safe }
        if (safeOnes.isEmpty()) {
            val best = evaluated.minByOrNull { it.weighted }
            val closest = best?.let { it }
            val reason = if (!(AttendanceProjection.meetsTarget(attendAllA, attendAllC, exam) &&
                    AttendanceProjection.meetsTarget(attendAllA, attendAllC, stipend)))
                NoSafeReason.BELOW_TARGET_EVEN_IF_ATTEND_ALL else NoSafeReason.EVERY_LEAVE_TOO_COSTLY
            return BunkPlannerResult.NoSafeOption(
                overview, reason,
                closest?.let { AttendanceProjection.rawPercent(it.projA, it.projC) },
                closest?.c?.bunkDays?.map { it.date }.orEmpty(),
                weakSubjects(baseBySubject, remainingWeightBySubject, exam, stipend)
            )
        }

        // --- Ranking: a fixed lexicographic order, no hidden score.
        val comparator = Comparator<Evaluated> { x, y ->
            var r = x.tier.ordinal.compareTo(y.tier.ordinal)                       // 1. safety tier (COMFORTABLE first)
            if (r == 0) r = (y.c.endIdx - y.c.startIdx).compareTo(x.c.endIdx - x.c.startIdx) // 2. longer break
            if (r == 0) r = x.c.bunkDays.size.compareTo(y.c.bunkDays.size)         // 3. fewer working days missed
            if (r == 0) r = y.weakestMargin.compareTo(x.weakestMargin)             // 4. gentler on weakest subject
            if (r == 0) r = y.margin.compareTo(x.margin)                           // 5. larger overall margin
            if (r == 0) r = x.c.startIdx.compareTo(y.c.startIdx)                   // 6. earlier start
            r
        }
        val ranked = safeOnes.sortedWith(comparator)

        // --- Selection: prefer non-overlapping breaks; fill remaining places with overlapping ones.
        val chosen = ArrayList<Evaluated>()
        for (e in ranked) {
            if (chosen.size >= MAX_OPTIONS) break
            if (chosen.none { overlaps(it.c, e.c) }) chosen += e
        }
        if (chosen.size < MAX_OPTIONS) {
            for (e in ranked) {
                if (chosen.size >= MAX_OPTIONS) break
                if (e !in chosen) chosen += e
            }
        }
        val final = chosen.sortedWith(comparator)

        val provisional = unmarkedPast > 0
        val options = final.mapIndexed { index, e ->
            val span = (e.c.startIdx..e.c.endIdx).map { days[it] }
            val entries = span.map { day ->
                val sessions = day.sessions.map {
                    BunkSessionEntry(it.subject, it.periodLabel, it.subject.creditHours, it.subject.isAutoPresent,
                        isMissed = day.kind == BunkDayKind.BUNK && !it.subject.isAutoPresent)
                }
                val man = day.manual
                BunkDayEntry(day.date, day.kind, day.label, sessions, man.size,
                    man.map { it.subject.shortName }.distinct())
            }
            val weekend = span.count { it.kind == BunkDayKind.WEEKEND }
            val holidays = span.count { it.kind == BunkDayKind.HOLIDAY }
            val noClass = span.count { it.kind == BunkDayKind.NO_CLASS_DAY }
            val projPct = AttendanceProjection.rawPercent(e.projA, e.projC) ?: 0.0
            val warnings = e.impacts.count { it.status != SubjectStatus.ON_TRACK }
            val option = BunkOption(
                rank = index + 1,
                start = span.first().date,
                end = span.last().date,
                days = entries,
                bunkDates = e.c.bunkDays.map { it.date },
                calendarDays = span.size,
                workingDaysMissed = e.c.bunkDays.size,
                classesMissed = e.classes,
                weightedMissed = e.weighted,
                weekendDays = weekend,
                holidayDays = holidays,
                noClassDays = noClass,
                currentPercent = currentPct,
                projectedAttended = e.projA,
                projectedConducted = e.projC,
                projectedPercent = projPct,
                requiredTarget = required,
                marginPoints = e.margin,
                tier = e.tier,
                subjects = e.impacts,
                subjectWarningCount = warnings,
                weakestSubject = e.impacts.firstOrNull(),
                isProvisional = provisional,
                explanation = ""
            )
            option.copy(explanation = explain(option, exam, stipend, buffer, unmarkedPast))
        }
        return BunkPlannerResult.Ready(overview, options)
    }

    private fun overlaps(a: Candidate, b: Candidate) = a.startIdx <= b.endIdx && b.startIdx <= a.endIdx

    private fun subjectStatus(att: Int, cond: Int, exam: Int, stipend: Int): SubjectStatus = when {
        AttendanceProjection.meetsTarget(att, cond, exam) && AttendanceProjection.meetsTarget(att, cond, stipend) -> SubjectStatus.ON_TRACK
        !AttendanceProjection.meetsTarget(att, cond, exam) -> SubjectStatus.BELOW_EXAM
        else -> SubjectStatus.BELOW_STIPEND
    }

    /** Manually attended subjects that finish the month below target even if every remaining class is attended. */
    private fun weakSubjects(
        base: Map<String, com.example.data.reports.SubjectReport>,
        remaining: Map<String, Int>,
        exam: Int,
        stipend: Int
    ): List<String> = base.values
        .filter { !it.subject.isAutoPresent }
        .filter {
            val a = it.attendedCredits + (remaining[it.subject.code] ?: 0)
            val c = it.conductedCredits + (remaining[it.subject.code] ?: 0)
            c > 0 && !(AttendanceProjection.meetsTarget(a, c, exam) && AttendanceProjection.meetsTarget(a, c, stipend))
        }
        .map { it.subject.shortName }

    fun fmt(value: Double): String = String.format(Locale.US, "%.1f", AttendanceProjection.displayFloor(value))

    private fun explain(o: BunkOption, exam: Int, stipend: Int, buffer: Int, unmarked: Int): String {
        val sb = StringBuilder()
        val workWord = if (o.workingDaysMissed == 1) "working day" else "working days"
        sb.append("Missing ${o.workingDaysMissed} $workWord (${o.classesMissed} classes) gives ${o.calendarDays} consecutive days off")
        val free = ArrayList<String>()
        if (o.weekendDays > 0) free += "${o.weekendDays} weekend day${if (o.weekendDays > 1) "s" else ""}"
        if (o.holidayDays > 0) free += "${o.holidayDays} holiday${if (o.holidayDays > 1) "s" else ""}"
        if (o.noClassDays > 0) free += "${o.noClassDays} no-class day${if (o.noClassDays > 1) "s" else ""}"
        if (free.isNotEmpty()) sb.append(" by using ${free.joinToString(" and ")}")
        sb.append(". Projected attendance if you attend all other scheduled classes this month and take this leave: ")
        sb.append("${fmt(o.projectedPercent)}% at month end against a required ${o.requiredTarget}% ")
        sb.append("(exam $exam%, stipend $stipend%), ${fmt(o.marginPoints)} points above it.")
        sb.append(
            if (o.tier == SafetyTier.COMFORTABLE) " That clears your $buffer-point safety buffer."
            else " That is Tight: it meets the targets but not the $buffer-point safety buffer."
        )
        val warn = o.subjects.filter { it.status != SubjectStatus.ON_TRACK }
        if (warn.isNotEmpty()) {
            sb.append(" Subject warning: ")
            sb.append(warn.joinToString(", ") { "${it.subject.shortName} ${fmt(it.projectedPercent)}%" })
            sb.append(" would finish below your target.")
        } else if (o.subjects.isNotEmpty()) {
            sb.append(" Every affected subject also stays on track.")
        }
        if (unmarked > 0) {
            sb.append(" Provisional: $unmarked past ${if (unmarked == 1) "class is" else "classes are"} still unmarked and not counted, so this is not a definitive result.")
        }
        return sb.toString()
    }
}
