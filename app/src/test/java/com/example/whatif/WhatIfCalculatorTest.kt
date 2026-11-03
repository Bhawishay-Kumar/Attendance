package com.example.whatif

import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.whatif.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatIfCalculatorTest {

    private fun planDay(date: java.time.LocalDate, d: PlanDecision) =
        Fx.manual(date).map { PlannedClass(date.toString(), it.periodNumber, it.subject.code, d) }

    // ── baseline ──

    @Test fun baselineWithNoRecordsHasNoData() {
        val b = WhatIfCalculator.buildBaseline(Fx.ctx(records = emptyMap()))
        assertFalse(b.hasData)
        assertEquals(BaselineCompleteness.NONE, b.completeness)
    }

    @Test fun baselineStartsAtEarliestValidRecordAndMatchesTimetable() {
        val b = WhatIfCalculator.buildBaseline(Fx.ctx())
        assertEquals(Fx.MON.toString(), b.rangeStart)
        assertEquals(Fx.expectedBaselineAtt(), b.attended)
        assertEquals(Fx.expectedBaselineCond(), b.conducted)
    }

    @Test fun missingPastRecordsAreNeitherPresentNorAbsentAndMarkBaselinePartial() {
        // Mon marked, Tue (past working day) completely unmarked.
        val mon = Fx.manual(Fx.MON).associate { it.periodNumber to PeriodStatus.PRESENT }
        val b = WhatIfCalculator.buildBaseline(Fx.ctx(records = mapOf(Fx.MON.toString() to mon)))
        assertEquals(BaselineCompleteness.PARTIAL, b.completeness)
        assertTrue(b.unmarkedSessions >= Fx.manual(Fx.TUE).size)
        // Tuesday's manual classes add nothing to conducted; only Monday plus auto sessions of Tue count.
        assertEquals(Fx.w(Fx.slots(Fx.MON)) + Fx.w(Fx.auto(Fx.TUE)), b.conducted)
        assertEquals(b.conducted, b.attended)
    }

    @Test fun futureDatedRecordsNeverBecomeTheBaselineStart() {
        val rec = Fx.baseRecords() + (Fx.THU.toString() to mapOf(1 to PeriodStatus.PRESENT))
        val b = WhatIfCalculator.buildBaseline(Fx.ctx(records = rec))
        assertEquals(Fx.MON.toString(), b.rangeStart)
    }

    @Test fun sessionStartBeforeFirstRecordIsFlaggedPartial() {
        val p = StudentProfile(academicSession = "Started 2026-08-17")
        val b = WhatIfCalculator.buildBaseline(Fx.ctx(profile = p))
        assertEquals(BaselineCompleteness.PARTIAL, b.completeness)
        assertTrue(b.notes.any { it.contains("Earlier classes are not included") })
    }

    // ── exact eligibility ──

    @Test fun eligibilityUsesRawRatioNotRoundedPercentage() {
        // 74.96% displays as 75.0 but must NOT be eligible for a 75% target.
        assertEquals(75.0, WhatIfMath.pct(7496, 10000), 0.0001)
        assertEquals(TargetStatus.NOT_MET, WhatIfMath.meetsTarget(7496, 10000, 75))
        assertEquals(TargetStatus.MET, WhatIfMath.meetsTarget(7500, 10000, 75))
        assertEquals(TargetStatus.NO_DATA, WhatIfMath.meetsTarget(0, 0, 75))
    }

    @Test fun compareRatioIsExact() {
        assertEquals(0, WhatIfMath.compareRatio(1, 3, 2, 6))
        assertTrue(WhatIfMath.compareRatio(7, 10, 69, 100) > 0)
        assertTrue(WhatIfMath.compareRatio(0, 0, 1, 2) < 0)
    }

    // ── plan only ──

    @Test fun planOnlyAddsOnlyPlannedDatesAndSeparatesActualRecords() {
        val ctx = Fx.ctx()
        val recordsBefore = ctx.records.mapValues { it.value.toMap() }
        val plan = WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))
        val base = WhatIfCalculator.buildBaseline(ctx)
        val r = WhatIfCalculator.calculate(ctx, plan, ProjectionMode.PLAN_ONLY, base)

        assertEquals(base.attended + Fx.w(Fx.auto(Fx.THU)), r.projectedAttended)
        assertEquals(base.conducted + Fx.w(Fx.auto(Fx.THU)) + Fx.w(Fx.manual(Fx.THU)), r.projectedConducted)
        assertEquals(recordsBefore, ctx.records) // simulation never touches actual records
        assertTrue(r.dropped.isEmpty())
    }

    @Test fun planOnlyUnplannedClassesOnSelectedDateAreNotCounted() {
        val ctx = Fx.ctx()
        val first = Fx.manual(Fx.THU).first()
        val plan = WhatIfPlan(listOf(PlannedClass(Fx.THU.toString(), first.periodNumber, first.subject.code, PlanDecision.PRESENT)))
        val r = WhatIfCalculator.calculate(ctx, plan, ProjectionMode.PLAN_ONLY)
        assertEquals(1, r.count(LineKind.PLANNED_PRESENT))
        assertEquals(Fx.manual(Fx.THU).size - 1, r.count(LineKind.NOT_COUNTED))
        assertEquals(r.baseline.attended + first.subject.creditHours + Fx.w(Fx.auto(Fx.THU)), r.projectedAttended)
    }

    @Test fun emptyPlanOnlyProjectionEqualsBaseline() {
        val r = WhatIfCalculator.calculate(Fx.ctx(), WhatIfPlan(), ProjectionMode.PLAN_ONLY)
        assertEquals(r.baseline.attended, r.projectedAttended)
        assertEquals(r.baseline.conducted, r.projectedConducted)
    }

    // ── validation of decisions ──

    @Test fun todayAndPastDecisionsAreDroppedNotApplied() {
        val item = PlannedClass(Fx.TODAY.toString(), 1, "X", PlanDecision.ABSENT)
        val past = PlannedClass(Fx.MON.toString(), 1, "X", PlanDecision.ABSENT)
        val r = WhatIfCalculator.calculate(Fx.ctx(), WhatIfPlan(listOf(item, past)), ProjectionMode.PLAN_ONLY)
        assertEquals(2, r.dropped.size)
        assertEquals(r.baseline.conducted, r.projectedConducted)
    }

    @Test fun holidayAndWeekendDecisionsAreDropped() {
        val ctx = Fx.ctx(holidays = setOf(Fx.THU.toString()))
        val thu = PlannedClass(Fx.THU.toString(), 1, "X", PlanDecision.ABSENT)
        val sat = PlannedClass("2026-11-07", 1, "X", PlanDecision.ABSENT)
        val r = WhatIfCalculator.calculate(ctx, WhatIfPlan(listOf(thu, sat)), ProjectionMode.PLAN_ONLY)
        assertEquals(2, r.dropped.size)
    }

    @Test fun changedSubjectAtPeriodIsDroppedOnRecalculation() {
        val first = Fx.manual(Fx.THU).first()
        val stale = PlannedClass(Fx.THU.toString(), first.periodNumber, "OLDCODE", PlanDecision.PRESENT)
        val r = WhatIfCalculator.calculate(Fx.ctx(), WhatIfPlan(listOf(stale)), ProjectionMode.PLAN_ONLY)
        assertEquals(1, r.dropped.size)
        assertTrue(r.dropped.first().reason.contains("changed"))
    }

    @Test fun cancelledFutureClassIsExcludedAndNotPlannable() {
        val first = Fx.manual(Fx.THU).first()
        val rec = Fx.baseRecords() + (Fx.THU.toString() to mapOf(first.periodNumber to PeriodStatus.NO_CLASS))
        val ctx = Fx.ctx(records = rec)
        val info = WhatIfCalculator.dayInfo(ctx, Fx.THU)
        assertTrue(info.cancelled.any { it.periodNumber == first.periodNumber })
        assertTrue(info.manual.none { it.periodNumber == first.periodNumber })
        val plan = WhatIfPlan(listOf(PlannedClass(Fx.THU.toString(), first.periodNumber, first.subject.code, PlanDecision.ABSENT)))
        val r = WhatIfCalculator.calculate(ctx, plan, ProjectionMode.PLAN_ONLY)
        assertEquals(1, r.dropped.size)
    }

    // ── month end ──

    @Test fun monthEndAssumesUnplannedManualClassesPresent() {
        val ctx = Fx.ctx()
        val r = WhatIfCalculator.calculate(ctx, WhatIfPlan(), ProjectionMode.MONTH_END)
        val addedAtt = r.projectedAttended - r.baseline.attended
        val addedCond = r.projectedConducted - r.baseline.conducted
        assertTrue(addedCond > 0)
        assertEquals(addedCond, addedAtt) // everything added is attended
        assertEquals("2026-11-30", r.horizonEnd)
        assertTrue(r.assumptions.any { it.contains("ASSUMED PRESENT") })
        assertEquals(0, r.count(LineKind.PLANNED_ABSENT))
    }

    @Test fun monthEndPlannedAbsenceLowersProjectionByExactlyItsWeight() {
        val ctx = Fx.ctx()
        val none = WhatIfCalculator.calculate(ctx, WhatIfPlan(), ProjectionMode.MONTH_END)
        val absent = WhatIfCalculator.calculate(ctx, WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)), ProjectionMode.MONTH_END)
        assertEquals(none.projectedConducted, absent.projectedConducted)
        assertEquals(none.projectedAttended - Fx.w(Fx.manual(Fx.THU)), absent.projectedAttended)
    }

    @Test fun monthEndExcludesPlannedDatesAfterBoundaryAndExplainsIt() {
        val ctx = Fx.ctx()
        val plan = WhatIfPlan(planDay(Fx.DEC_1, PlanDecision.ABSENT))
        val r = WhatIfCalculator.calculate(ctx, plan, ProjectionMode.MONTH_END)
        assertNotNull(r.boundaryNote)
        assertTrue(r.excludedDates.any { it.date == Fx.DEC_1.toString() })
        val clean = WhatIfCalculator.calculate(ctx, WhatIfPlan(), ProjectionMode.MONTH_END)
        assertEquals(clean.projectedAttended, r.projectedAttended)
        assertEquals(clean.projectedConducted, r.projectedConducted)
    }

    @Test fun monthEndSkipsHolidays() {
        val hol = Fx.ctx(holidays = setOf(Fx.THU.toString()))
        val withHoliday = WhatIfCalculator.calculate(hol, WhatIfPlan(), ProjectionMode.MONTH_END)
        val without = WhatIfCalculator.calculate(Fx.ctx(), WhatIfPlan(), ProjectionMode.MONTH_END)
        assertEquals(without.projectedConducted - Fx.w(Fx.slots(Fx.THU)), withHoliday.projectedConducted)
    }

    @Test fun bestCaseMatchesMonthEndWithNoOverrides() {
        val ctx = Fx.ctx()
        val base = WhatIfCalculator.buildBaseline(ctx)
        val best = WhatIfCalculator.bestCase(ctx, base)
        val me = WhatIfCalculator.calculate(ctx, WhatIfPlan(), ProjectionMode.MONTH_END, base)
        assertEquals(me.projectedAttended, best.attended)
        assertEquals(me.projectedConducted, best.conducted)
    }

    // ── per-subject consistency ──

    @Test fun subjectTotalsSumToOverall() {
        val r = WhatIfCalculator.calculate(Fx.ctx(), WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT) + planDay(Fx.FRI, PlanDecision.PRESENT)), ProjectionMode.PLAN_ONLY)
        assertEquals(r.projectedAttended, r.subjects.sumOf { it.projectedAttended })
        assertEquals(r.projectedConducted, r.subjects.sumOf { it.projectedConducted })
    }

    // ── recovery ──

    @Test fun recoverySuggestionActuallyReachesTargetWhenAttended() {
        val p = StudentProfile(examTarget = 97, stipendTarget = 99)
        val ctx = Fx.ctx(profile = p)
        val r = WhatIfCalculator.calculate(ctx, WhatIfPlan(), ProjectionMode.PLAN_ONLY)
        val rec = WhatIfCalculator.buildRecovery(ctx, WhatIfPlan(), ProjectionMode.PLAN_ONLY, r)
        val overallExam = rec.first { it.scopeCode == null && it.targetKind == TargetKind.EXAM }
        assertEquals(RecoveryState.ATTEND_NEXT, overallExam.state)
        val needed = overallExam.hoursNeeded
        assertTrue(needed > 0)
        // After attending 'taken' >= needed hours the exact ratio meets the target.
        val taken = overallExam.classesNeeded!!
        assertTrue(taken >= 1)
        val a = r.projectedAttended + needed
        val c = r.projectedConducted + needed
        assertEquals(TargetStatus.MET, WhatIfMath.meetsTarget(a, c, 97))
        assertNotNull(overallExam.pctIfAttended)
    }

    @Test fun recoveryAt100PercentTargetIsUnreachableAfterAMiss() {
        val p = StudentProfile(examTarget = 100)
        val ctx = Fx.ctx(profile = p)
        val r = WhatIfCalculator.calculate(ctx, WhatIfPlan(), ProjectionMode.PLAN_ONLY)
        val rec = WhatIfCalculator.buildRecovery(ctx, WhatIfPlan(), ProjectionMode.PLAN_ONLY, r)
        assertEquals(RecoveryState.UNREACHABLE_100, rec.first { it.scopeCode == null && it.targetKind == TargetKind.EXAM }.state)
    }

    @Test fun monthEndRecoveryNeverInventsExtraClasses() {
        val p = StudentProfile(examTarget = 100)
        val ctx = Fx.ctx(profile = p.copy(examTarget = 99))
        val plan = WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))
        val r = WhatIfCalculator.calculate(ctx, plan, ProjectionMode.MONTH_END)
        val rec = WhatIfCalculator.buildRecovery(ctx, plan, ProjectionMode.MONTH_END, r)
        rec.filter { it.state == RecoveryState.ATTEND_NEXT }.forEach { fail("Month End has no unplanned pool: ${it.message}") }
    }

    @Test fun recoveryOffersFlippingPlannedAbsencesWithRealRecalculation() {
        val p = StudentProfile(examTarget = 90)
        val ctx = Fx.ctx(profile = p)
        val plan = WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))
        val r = WhatIfCalculator.calculate(ctx, plan, ProjectionMode.PLAN_ONLY)
        val rec = WhatIfCalculator.buildRecovery(ctx, plan, ProjectionMode.PLAN_ONLY, r)
        val flip = rec.first { it.scopeCode == null && it.targetKind == TargetKind.EXAM }.flip
        assertNotNull(flip)
        val flipped = WhatIfCalculator.calculate(ctx, WhatIfPlan(planDay(Fx.THU, PlanDecision.PRESENT)), ProjectionMode.PLAN_ONLY)
        assertEquals(flipped.projectedPct, flip!!.projectedPct, 0.0001)
    }
}
