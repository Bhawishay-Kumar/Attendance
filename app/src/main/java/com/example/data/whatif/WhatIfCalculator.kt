package com.example.data.whatif

import com.example.data.math.AttendanceMath
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.reports.DayStatus
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.reports.ReportSessionStatus
import com.example.data.timetable.TimetableMatrix
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What one future date offers the planner. Pure read of timetable, holidays and stored cancellations. */
data class DayInfo(
    val date: String,
    val availability: DayAvailability,
    val manual: List<PeriodSlot>,
    val auto: List<PeriodSlot>,
    val cancelled: List<PeriodSlot>
)

/**
 * Pure, read-only What-If calculations. Nothing here writes attendance, holidays or profile data.
 * Actual history is computed with the existing [ReportAnalyticsEngine]; simulated future sessions are
 * kept completely separate and only ever added on top of that actual baseline.
 */
object WhatIfCalculator {

    private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun shortDate(iso: String): String =
        runCatching { LocalDate.parse(iso).format(DISPLAY_DATE) }.getOrDefault(iso)

    private fun parseIso(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    private fun isLab(name: String) = name.contains("Lab", ignoreCase = true)

    // ───────────────────────── baseline ─────────────────────────

    /** Best-effort parse of a session start date typed into the free-text academic session field. */
    fun parseSessionStart(text: String): LocalDate? {
        if (text.isBlank()) return null
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("""(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})""").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    /** Earliest valid date (not in the future) that carries at least one manual Present/Absent mark. */
    fun earliestValidRecord(ctx: WhatIfContext): LocalDate? {
        var best: LocalDate? = null
        for ((dateStr, periods) in ctx.records) {
            val d = parseIso(dateStr) ?: continue
            if (d.isAfter(ctx.today)) continue
            val hasMark = periods.values.any { it == PeriodStatus.PRESENT || it == PeriodStatus.ABSENT }
            if (!hasMark) continue
            if (best == null || d.isBefore(best)) best = d
        }
        return best
    }

    fun buildBaseline(ctx: WhatIfContext): WhatIfBaseline {
        val first = earliestValidRecord(ctx)
        val hint = parseSessionStart(ctx.profile.academicSession)
        if (first == null) {
            return WhatIfBaseline(
                rangeStart = null, rangeEnd = ctx.today.toString(), attended = 0, conducted = 0,
                subjects = emptyList(), completeness = BaselineCompleteness.NONE,
                unmarkedSessions = 0, unmarkedDays = 0, sessionStartHint = hint?.toString(),
                notes = listOf("No attendance has been marked yet, so there is no baseline to project from.")
            )
        }
        val analytics = ReportAnalyticsEngine.analyze(
            ReportDateRange(first, ctx.today), ctx.records, ctx.isHoliday, ctx.profile, ctx.today
        )
        val todayStr = ctx.today.toString()
        // Sessions on past working days that have no status are excluded (never guessed). Today is not a gap yet.
        val unmarkedSessions = analytics.days
            .filter { it.date != todayStr }
            .sumOf { d -> d.sessions.count { it.status == ReportSessionStatus.PENDING } }
        val unmarkedDays = analytics.days.count { it.date != todayStr && it.status == DayStatus.PENDING }

        val notes = mutableListOf<String>()
        notes.add("Calculated from stored records: ${shortDate(first.toString())} to ${shortDate(todayStr)}.")
        var partial = false
        if (unmarkedSessions > 0) {
            partial = true
            notes.add(
                "$unmarkedSessions manual session(s) on $unmarkedDays past working day(s) have no recorded status. " +
                    "They are left out — not counted as Present or Absent."
            )
        }
        if (hint != null && hint.isBefore(first)) {
            partial = true
            notes.add(
                "Your academic session appears to start on ${shortDate(hint.toString())}, but the earliest stored record is " +
                    "${shortDate(first.toString())}. Earlier classes are not included."
            )
        }
        val subjects = analytics.subjects.map {
            SubjectTotals(
                code = it.subject.code, shortName = it.subject.shortName, fullName = it.subject.fullName,
                isLab = isLab(it.subject.fullName), creditHours = it.subject.creditHours,
                attended = it.attendedCredits, conducted = it.conductedCredits
            )
        }
        return WhatIfBaseline(
            rangeStart = first.toString(), rangeEnd = todayStr,
            attended = analytics.attendedCredits, conducted = analytics.conductedCredits,
            subjects = subjects,
            completeness = if (partial) BaselineCompleteness.PARTIAL else BaselineCompleteness.COMPLETE,
            unmarkedSessions = unmarkedSessions, unmarkedDays = unmarkedDays,
            sessionStartHint = hint?.toString(), notes = notes
        )
    }

    // ───────────────────────── day availability ─────────────────────────

    fun dayInfo(ctx: WhatIfContext, date: LocalDate): DayInfo {
        val iso = date.toString()
        fun none(a: DayAvailability) = DayInfo(iso, a, emptyList(), emptyList(), emptyList())
        if (!date.isAfter(ctx.today)) return none(DayAvailability.PAST_OR_TODAY)
        val dow = date.dayOfWeek.value
        if (dow !in 1..5) return none(if (ctx.isHoliday(iso)) DayAvailability.HOLIDAY else DayAvailability.WEEKEND)
        if (ctx.isHoliday(iso)) return none(DayAvailability.HOLIDAY)
        val slots = TimetableMatrix.getScheduleForDay(dow, ctx.profile.batchEnum, ctx.profile.courseSelectionEnum)
        if (slots.isEmpty()) return none(DayAvailability.NO_CLASSES)
        val stored = ctx.records[iso]
        val cancelled = slots.filter { stored?.get(it.periodNumber) == PeriodStatus.NO_CLASS }
        val live = slots.filter { s -> cancelled.none { it.periodNumber == s.periodNumber } }
        val manual = live.filter { !it.subject.isAutoPresent }
        val auto = live.filter { it.subject.isAutoPresent }
        val availability = when {
            manual.isNotEmpty() -> DayAvailability.PLANNABLE
            cancelled.any { !it.subject.isAutoPresent } -> DayAvailability.ALL_CANCELLED
            else -> DayAvailability.AUTO_ONLY
        }
        return DayInfo(iso, availability, manual, auto, cancelled)
    }

    private fun availabilityReason(a: DayAvailability) = when (a) {
        DayAvailability.PAST_OR_TODAY -> "Date is today or in the past, so it is already part of your actual records"
        DayAvailability.WEEKEND -> "Weekend — no classes"
        DayAvailability.HOLIDAY -> "Holiday — no classes"
        DayAvailability.NO_CLASSES -> "No classes scheduled"
        DayAvailability.ALL_CANCELLED -> "All classes are marked cancelled"
        DayAvailability.AUTO_ONLY -> "Only automatic sessions remain — nothing to plan"
        DayAvailability.PLANNABLE -> ""
    }

    private fun line(kind: LineKind, date: String, slot: PeriodSlot) = ClassLine(
        kind = kind, date = date, periodNumber = slot.periodNumber, periodLabel = slot.periodLabel,
        subjectCode = slot.subject.code, subjectName = slot.subject.shortName,
        weight = slot.subject.creditHours, isLab = isLab(slot.subject.fullName)
    )

    private fun target(att: Int, cond: Int, target: Int) = TargetCheck(
        target = target,
        status = WhatIfMath.meetsTarget(att, cond, target),
        hoursNeeded = AttendanceMath.calculateRecoveryHours(att, cond, target),
        safeSkipHours = AttendanceMath.calculateSafeSkipHours(att, cond, target)
    )

    // ───────────────────────── projection ─────────────────────────

    fun calculate(
        ctx: WhatIfContext,
        plan: WhatIfPlan,
        mode: ProjectionMode,
        baseline: WhatIfBaseline = buildBaseline(ctx)
    ): WhatIfResult {
        val profile = ctx.profile
        val horizonEnd: LocalDate? =
            if (mode == ProjectionMode.MONTH_END) YearMonth.from(ctx.today).atEndOfMonth() else null

        // 1. Validate planned decisions against today's timetable / holidays / cancellations.
        val validated = HashMap<String, HashMap<Int, PlanDecision>>()
        val dropped = mutableListOf<DroppedDecision>()
        val excluded = mutableListOf<ExcludedDate>()
        val beyondBoundary = sortedSetOf<String>()
        for (item in plan.items) {
            val d = parseIso(item.date)
            if (d == null) { dropped.add(DroppedDecision(item, "Invalid date")); continue }
            val info = dayInfo(ctx, d)
            if (info.availability != DayAvailability.PLANNABLE) {
                dropped.add(DroppedDecision(item, availabilityReason(info.availability))); continue
            }
            val slot = info.manual.firstOrNull { it.periodNumber == item.periodNumber }
            if (slot == null) {
                dropped.add(DroppedDecision(item, "That period is no longer a scheduled, live class")); continue
            }
            if (slot.subject.code != item.subjectCode) {
                dropped.add(DroppedDecision(item, "The subject at this period has changed (${item.subjectCode} → ${slot.subject.code})")); continue
            }
            if (horizonEnd != null && d.isAfter(horizonEnd)) {
                beyondBoundary.add(item.date); continue
            }
            validated.getOrPut(item.date) { HashMap() }[item.periodNumber] = item.decision
        }
        for (d in beyondBoundary) {
            excluded.add(ExcludedDate(d, "After ${shortDate(horizonEnd.toString())} (Month End boundary) — not included"))
        }

        // 2. Dates that enter the projection.
        val dates: List<LocalDate> = if (mode == ProjectionMode.MONTH_END) {
            val out = mutableListOf<LocalDate>()
            var d = ctx.today.plusDays(1)
            while (!d.isAfter(horizonEnd!!)) { out.add(d); d = d.plusDays(1) }
            out
        } else {
            validated.keys.sorted().mapNotNull { parseIso(it) }
        }

        // 3. Simulated sessions (never touches actual records).
        val lines = mutableListOf<ClassLine>()
        val addAtt = HashMap<String, Int>()
        val addCond = HashMap<String, Int>()
        fun add(code: String, a: Int, c: Int) {
            addAtt[code] = (addAtt[code] ?: 0) + a
            addCond[code] = (addCond[code] ?: 0) + c
        }
        for (d in dates) {
            val iso = d.toString()
            val info = dayInfo(ctx, d)
            when (info.availability) {
                DayAvailability.HOLIDAY -> { excluded.add(ExcludedDate(iso, "Holiday")); continue }
                DayAvailability.WEEKEND, DayAvailability.NO_CLASSES, DayAvailability.PAST_OR_TODAY -> continue
                else -> Unit
            }
            info.cancelled.forEach { lines.add(line(LineKind.CANCELLED, iso, it)) }
            info.auto.forEach { slot ->
                lines.add(line(LineKind.AUTO_CREDITED, iso, slot))
                add(slot.subject.code, slot.subject.creditHours, slot.subject.creditHours)
            }
            info.manual.forEach { slot ->
                val w = slot.subject.creditHours
                when (validated[iso]?.get(slot.periodNumber)) {
                    PlanDecision.PRESENT -> { lines.add(line(LineKind.PLANNED_PRESENT, iso, slot)); add(slot.subject.code, w, w) }
                    PlanDecision.ABSENT -> { lines.add(line(LineKind.PLANNED_ABSENT, iso, slot)); add(slot.subject.code, 0, w) }
                    null -> if (mode == ProjectionMode.MONTH_END) {
                        lines.add(line(LineKind.ASSUMED_PRESENT, iso, slot)); add(slot.subject.code, w, w)
                    } else {
                        lines.add(line(LineKind.NOT_COUNTED, iso, slot))
                    }
                }
            }
        }

        // 4. Combine baseline + simulated, per subject and overall.
        val baseByCode = baseline.subjects.associateBy { it.code }
        val subjectDefs = TimetableMatrix.getAllActiveSubjects(profile.courseSelectionEnum)
        val subjects = subjectDefs.map { s ->
            val b = baseByCode[s.code]
            val ba = b?.attended ?: 0
            val bc = b?.conducted ?: 0
            val pa = ba + (addAtt[s.code] ?: 0)
            val pc = bc + (addCond[s.code] ?: 0)
            SubjectProjection(
                code = s.code, shortName = s.shortName, fullName = s.fullName, isLab = isLab(s.fullName),
                baselineAttended = ba, baselineConducted = bc, projectedAttended = pa, projectedConducted = pc,
                exam = target(pa, pc, profile.examTarget), stipend = target(pa, pc, profile.stipendTarget)
            )
        }
        val projAtt = baseline.attended + addAtt.values.sum()
        val projCond = baseline.conducted + addCond.values.sum()

        // 5. Plain-language assumptions.
        val assumptions = mutableListOf<String>()
        assumptions.addAll(baseline.notes)
        when (mode) {
            ProjectionMode.PLAN_ONLY -> {
                assumptions.add("Plan Only: only the dates you selected are added to your actual record. Other upcoming classes are not counted at all.")
                assumptions.add("On selected dates, manual classes you left undecided are not counted.")
            }
            ProjectionMode.MONTH_END -> {
                assumptions.add(
                    "Month End: every remaining working day up to ${shortDate(horizonEnd.toString())} is included. " +
                        "Manual classes you did not plan are ASSUMED PRESENT — an assumption, not a guarantee."
                )
            }
        }
        assumptions.add("Automatic sessions (Library, Seminar) are credited as attended on every included date, as the app does for real days.")
        assumptions.add("Cancelled classes and holidays are excluded. Simulated sessions are never saved to your attendance.")
        assumptions.add("Eligibility uses exact, unrounded ratios; percentages shown are rounded to 0.1.")
        val boundary = if (beyondBoundary.isNotEmpty())
            "${beyondBoundary.size} planned date(s) fall after Month End (${shortDate(horizonEnd.toString())}) and are not included. Use Plan Only to include them."
        else null

        return WhatIfResult(
            mode = mode, horizonEnd = horizonEnd?.toString(), calcDate = ctx.today.toString(),
            batch = profile.batchEnum.displayName, course = profile.courseSelectionEnum.shortTitle,
            examTarget = profile.examTarget, stipendTarget = profile.stipendTarget, safetyBuffer = profile.safetyBuffer,
            baseline = baseline, projectedAttended = projAtt, projectedConducted = projCond,
            exam = target(projAtt, projCond, profile.examTarget),
            stipend = target(projAtt, projCond, profile.stipendTarget),
            subjects = subjects, lines = lines, excludedDates = excluded, dropped = dropped,
            assumptions = assumptions, boundaryNote = boundary
        )
    }

    /** Best case = Month End projection with no overrides: every remaining manual class attended. */
    fun bestCase(ctx: WhatIfContext, baseline: WhatIfBaseline): BestCaseSummary {
        val r = calculate(ctx, WhatIfPlan(), ProjectionMode.MONTH_END, baseline)
        return BestCaseSummary(r.projectedAttended, r.projectedConducted, r.exam.status, r.stipend.status, r.horizonEnd ?: ctx.today.toString())
    }

    // ───────────────────────── smart recovery ─────────────────────────

    /** Upcoming live manual classes through month end that are NOT yet in the projection (no plan decision). */
    private fun unplannedPool(ctx: WhatIfContext, plan: WhatIfPlan, mode: ProjectionMode): List<ClassLine> {
        if (mode == ProjectionMode.MONTH_END) return emptyList() // everything this month is already assumed Present
        val end = YearMonth.from(ctx.today).atEndOfMonth()
        val out = mutableListOf<ClassLine>()
        var d = ctx.today.plusDays(1)
        while (!d.isAfter(end)) {
            val info = dayInfo(ctx, d)
            if (info.availability == DayAvailability.PLANNABLE) {
                info.manual.forEach { slot ->
                    if (plan.decisionFor(info.date, slot.periodNumber) == null) out.add(line(LineKind.NOT_COUNTED, info.date, slot))
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    fun buildRecovery(
        ctx: WhatIfContext,
        plan: WhatIfPlan,
        mode: ProjectionMode,
        result: WhatIfResult
    ): List<RecoverySuggestion> {
        val pool = unplannedPool(ctx, plan, mode)
        val monthEnd = YearMonth.from(ctx.today).atEndOfMonth().toString()
        val out = mutableListOf<RecoverySuggestion>()

        data class Scope(val code: String?, val name: String, val baseA: Int, val baseC: Int, val a: Int, val c: Int)
        val scopes = mutableListOf(
            Scope(null, "Overall", result.baseline.attended, result.baseline.conducted, result.projectedAttended, result.projectedConducted)
        )
        result.subjects.filter { it.projectedConducted > 0 }.forEach {
            scopes.add(Scope(it.code, it.shortName, it.baselineAttended, it.baselineConducted, it.projectedAttended, it.projectedConducted))
        }

        for (scope in scopes) {
            val scopePool = if (scope.code == null) pool else pool.filter { it.subjectCode == scope.code }
            val absentLines = result.lines.filter {
                it.kind == LineKind.PLANNED_ABSENT && (scope.code == null || it.subjectCode == scope.code)
            }
            val flippedResult: WhatIfResult? = if (absentLines.isEmpty()) null else {
                val flipped = WhatIfPlan(plan.items.map { p ->
                    if (p.decision == PlanDecision.ABSENT && (scope.code == null || p.subjectCode == scope.code))
                        p.copy(decision = PlanDecision.PRESENT) else p
                })
                calculate(ctx, flipped, mode, result.baseline)
            }

            for (kind in TargetKind.values()) {
                val t = if (kind == TargetKind.EXAM) result.examTarget else result.stipendTarget
                val status = WhatIfMath.meetsTarget(scope.a, scope.c, t)
                val flipOutcome: FlipOutcome? = flippedResult?.let { r2 ->
                    val fa: Int
                    val fc: Int
                    if (scope.code == null) {
                        fa = r2.projectedAttended; fc = r2.projectedConducted
                    } else {
                        val sp = r2.subjects.first { it.code == scope.code }
                        fa = sp.projectedAttended; fc = sp.projectedConducted
                    }
                    FlipOutcome(
                        classCount = absentLines.size,
                        weightedHours = absentLines.sumOf { it.weight },
                        projectedPct = WhatIfMath.pct(fa, fc),
                        status = WhatIfMath.meetsTarget(fa, fc, t),
                        firstDate = absentLines.minOf { it.date }
                    )
                }
                val skip = AttendanceMath.calculateSafeSkipHours(scope.a, scope.c, t)
                val cur = WhatIfMath.pct(scope.baseA, scope.baseC)
                val proj = WhatIfMath.pct(scope.a, scope.c)
                fun make(
                    state: RecoveryState, needed: Int = 0, k: Int? = null, avail: Int = 0,
                    first: String? = null, last: String? = null, after: Double? = null, msg: String
                ) = RecoverySuggestion(
                    scope.code, scope.name, kind, t, state, cur, proj, needed, k, avail, first, last, after, skip, flipOutcome, msg
                )

                if (scope.c <= 0) {
                    if (scope.code == null) out.add(make(RecoveryState.NO_DATA, msg = "No sessions are counted yet, so there is nothing to recover."))
                    continue
                }
                if (status == TargetStatus.MET) {
                    val approaching = scope.a.toLong() * 100L < (t + result.safetyBuffer).toLong() * scope.c.toLong()
                    if (approaching) {
                        out.add(make(
                            RecoveryState.APPROACHING,
                            msg = "${kind.label} target of $t% is met, but only just. You can afford to miss ${skip} more hour(s) before dropping below it."
                        ))
                    }
                    continue
                }
                val needed = AttendanceMath.calculateRecoveryHours(scope.a, scope.c, t)
                if (needed < 0) {
                    out.add(make(RecoveryState.UNREACHABLE_100, msg = "A $t% target cannot be reached again once a class has been missed."))
                    continue
                }
                if (mode == ProjectionMode.MONTH_END) {
                    out.add(make(
                        RecoveryState.NOT_ENOUGH_SESSIONS, needed = needed,
                        msg = "All remaining classes through ${shortDate(monthEnd)} are already assumed Present. " +
                            "Reaching $t% needs $needed more attended hour(s) beyond that, in sessions the app cannot verify yet."
                    ))
                    continue
                }
                var taken = 0
                var k = 0
                val chosen = mutableListOf<ClassLine>()
                for (c in scopePool) {
                    if (taken >= needed) break
                    taken += c.weight; k++; chosen.add(c)
                }
                val availHours = scopePool.sumOf { it.weight }
                if (taken >= needed && WhatIfMath.meetsTarget(scope.a + taken, scope.c + taken, t) == TargetStatus.MET) {
                    val after = WhatIfMath.pct(scope.a + taken, scope.c + taken)
                    out.add(make(
                        RecoveryState.ATTEND_NEXT, needed = needed, k = k, avail = availHours,
                        first = chosen.first().date, last = chosen.last().date, after = after,
                        msg = "Attend the next $k scheduled ${if (scope.code == null) "" else scope.name + " "}class${if (k == 1) "" else "es"} " +
                            "(${shortDate(chosen.first().date)} – ${shortDate(chosen.last().date)}) with no further absences to reach $t%: projected $after%."
                    ))
                } else {
                    val allPct = WhatIfMath.pct(scope.a + availHours, scope.c + availHours)
                    out.add(make(
                        RecoveryState.NOT_ENOUGH_SESSIONS, needed = needed, avail = availHours, after = allPct,
                        msg = "Reaching $t% needs $needed more attended hour(s), but only $availHours remain unplanned through ${shortDate(monthEnd)}. " +
                            "Attending all of them would reach $allPct%."
                    ))
                }
            }
        }
        return out
    }

    // ───────────────────────── snapshots ─────────────────────────

    fun createSnapshot(
        id: String, name: String, savedAtMillis: Long, ctx: WhatIfContext, plan: WhatIfPlan,
        mode: ProjectionMode, planningStyle: String, result: WhatIfResult,
        recovery: List<RecoverySuggestion>, bestCase: BestCaseSummary?,
        isRecalculation: Boolean = false, derivedFromId: String? = null
    ) = WhatIfSnapshot(
        id = id, name = name, savedAtMillis = savedAtMillis, isRecalculation = isRecalculation, derivedFromId = derivedFromId,
        mode = mode, planningStyle = planningStyle, plan = plan, result = result, recovery = recovery, bestCase = bestCase
    )
}
