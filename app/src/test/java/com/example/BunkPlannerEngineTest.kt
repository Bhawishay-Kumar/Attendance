package com.example

import com.example.data.bunk.BunkDayKind
import com.example.data.bunk.BunkPlannerEngine
import com.example.data.bunk.BunkPlannerResult
import com.example.data.bunk.NoSafeReason
import com.example.data.bunk.SafetyTier
import com.example.data.math.AttendanceProjection
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.timetable.TimetableMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure-JVM tests for the Bunk Planner engine and its exact-ratio math. */
class BunkPlannerEngineTest {

    private val profile = StudentProfile() // Batch A, Major Honours, exam 75, stipend 80, buffer 4
    private fun d(s: String) = LocalDate.parse(s)

    /** Marks every MANUAL session on weekdays in [from, to] (skipping [holidays]) with [status]. */
    private fun mark(
        records: MutableMap<String, MutableMap<Int, PeriodStatus>>,
        from: String, to: String, status: PeriodStatus, holidays: Set<String> = emptySet(),
        only: ((LocalDate) -> Boolean)? = null
    ) {
        var cur = d(from)
        while (!cur.isAfter(d(to))) {
            if (cur.dayOfWeek.value <= 5 && cur.toString() !in holidays && (only?.invoke(cur) ?: true)) {
                TimetableMatrix.getScheduleForDay(cur.dayOfWeek.value, profile.batchEnum, profile.courseSelectionEnum)
                    .filter { !it.subject.isAutoPresent }
                    .forEach { records.getOrPut(cur.toString()) { mutableMapOf() }[it.periodNumber] = status }
            }
            cur = cur.plusDays(1)
        }
    }

    private fun plan(
        today: String,
        records: Map<String, Map<Int, PeriodStatus>>,
        holidays: Set<String> = emptySet()
    ) = BunkPlannerEngine.plan(profile, records, { it in holidays }, d(today))

    // ---- exact math ----------------------------------------------------------------------

    @Test fun `raw ratio decides eligibility not the rounded display`() {
        assertFalse(AttendanceProjection.meetsTarget(7996, 10000, 80)) // 79.96% would DISPLAY as 80.0 if rounded
        assertTrue(AttendanceProjection.meetsTarget(8000, 10000, 80))
        assertEquals(79.9, AttendanceProjection.displayFloor(79.96), 1e-9)
    }

    @Test fun `zero denominator and 100 percent target are safe`() {
        assertFalse(AttendanceProjection.meetsTarget(0, 0, 80))
        assertEquals(null, AttendanceProjection.rawPercent(0, 0))
        assertTrue(AttendanceProjection.meetsTarget(10, 10, 100))
        assertFalse(AttendanceProjection.meetsTarget(9, 10, 100))
    }

    // ---- result states -------------------------------------------------------------------

    @Test fun `no manual marks gives insufficient data`() {
        val r = plan("2026-11-05", emptyMap())
        assertTrue(r is BunkPlannerResult.InsufficientData)
    }

    @Test fun `no remaining classes on last day of month`() {
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-30", PeriodStatus.PRESENT)
        val r = plan("2026-11-30", rec)
        assertTrue(r is BunkPlannerResult.NoRemainingClasses)
    }

    @Test fun `below target even when attending everything gives no safe option`() {
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-02", PeriodStatus.PRESENT)
        mark(rec, "2026-11-03", "2026-11-27", PeriodStatus.ABSENT)
        val r = plan("2026-11-27", rec)
        assertTrue(r is BunkPlannerResult.NoSafeOption)
        r as BunkPlannerResult.NoSafeOption
        assertEquals(NoSafeReason.BELOW_TARGET_EVEN_IF_ATTEND_ALL, r.reason)
        assertTrue(r.overview.belowTargetNow)
    }

    // ---- recommendations -----------------------------------------------------------------

    private fun readyScenario(holidays: Set<String> = setOf("2026-11-09")): BunkPlannerResult.Ready {
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-05", PeriodStatus.PRESENT, holidays)
        val r = plan("2026-11-05", rec, holidays)
        assertTrue("expected Ready but was $r", r is BunkPlannerResult.Ready)
        return r as BunkPlannerResult.Ready
    }

    @Test fun `at most three options, all genuinely safe on raw ratios`() {
        val r = readyScenario()
        assertTrue(r.options.size in 1..3)
        r.options.forEach {
            assertTrue(AttendanceProjection.meetsTarget(it.projectedAttended, it.projectedConducted, profile.examTarget))
            assertTrue(AttendanceProjection.meetsTarget(it.projectedAttended, it.projectedConducted, profile.stipendTarget))
            assertTrue(it.marginPoints >= 0.0)
        }
        assertEquals(r.options.indices.map { it + 1 }, r.options.map { it.rank })
    }

    @Test fun `weekends and holidays are free days and never counted as bunk days`() {
        val hol = "2026-11-09"
        val r = readyScenario(setOf(hol))
        r.options.forEach { o ->
            o.days.forEach { e ->
                if (e.kind == BunkDayKind.WEEKEND || e.kind == BunkDayKind.HOLIDAY) {
                    assertEquals(0, e.classesMissed)
                    assertFalse(e.date in o.bunkDates)
                }
                if (e.date.toString() == hol) assertEquals(BunkDayKind.HOLIDAY, e.kind)
                if (e.date.dayOfWeek.value > 5 && e.date.toString() != hol) assertEquals(BunkDayKind.WEEKEND, e.kind)
            }
            assertEquals(o.workingDaysMissed, o.days.count { it.kind == BunkDayKind.BUNK })
            assertEquals(o.calendarDays, o.days.size)
            assertEquals(o.weekendDays + o.holidayDays + o.noClassDays + o.workingDaysMissed, o.calendarDays)
        }
    }

    @Test fun `breaks never reach into the past or beyond month end`() {
        val r = readyScenario()
        r.options.forEach {
            assertTrue(it.start.isAfter(d("2026-11-05")))
            assertFalse(it.end.isAfter(d("2026-11-30")))
            assertTrue(it.bunkDates.all { b -> b.isAfter(d("2026-11-05")) })
        }
    }

    @Test fun `weighting applied exactly once and matches the listed missed sessions`() {
        val r = readyScenario()
        r.options.forEach { o ->
            val missed = o.days.flatMap { it.sessions }.filter { it.isMissed }
            assertEquals(missed.sumOf { it.weight }, o.weightedMissed)
            assertEquals(missed.size, o.classesMissed)
            assertTrue(missed.none { it.isAutoCredited })
            o.subjects.forEach { sub -> assertTrue(sub.projectedAttended + sub.missedWeight <= sub.projectedConducted) }
            assertEquals(r.overview.currentConducted + r.overview.remainingWeight, o.projectedConducted)
            assertEquals(r.overview.currentAttended + r.overview.remainingWeight - o.weightedMissed, o.projectedAttended)
        }
    }

    @Test fun `cancelled day is not a bunk day`() {
        val hol = setOf("2026-11-09")
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-05", PeriodStatus.PRESENT, hol)
        mark(rec, "2026-11-06", "2026-11-06", PeriodStatus.NO_CLASS)
        val r = plan("2026-11-05", rec, hol) as BunkPlannerResult.Ready
        r.options.forEach { assertFalse(d("2026-11-06") in it.bunkDates) }
    }

    @Test fun `ranking order is tier then longer break then fewer working days`() {
        val r = readyScenario()
        r.options.zipWithNext().forEach { (a, b) ->
            assertTrue(a.tier.ordinal <= b.tier.ordinal)
            if (a.tier == b.tier) {
                assertTrue(a.calendarDays >= b.calendarDays)
                if (a.calendarDays == b.calendarDays) assertTrue(a.workingDaysMissed <= b.workingDaysMissed)
            }
        }
    }

    @Test fun `unmarked past classes make results provisional and are counted`() {
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-05", PeriodStatus.PRESENT, only = { it.toString() != "2026-11-03" })
        val r = plan("2026-11-05", rec) as BunkPlannerResult.Ready
        assertTrue(r.overview.unmarkedPastSessions > 0)
        assertTrue(r.overview.isProvisional)
        r.options.forEach {
            assertTrue(it.isProvisional)
            assertTrue(it.explanation.contains("Provisional"))
        }
    }

    @Test fun `planner never mutates the records it reads`() {
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-05", PeriodStatus.PRESENT)
        val before = rec.mapValues { it.value.toMap() }
        plan("2026-11-05", rec)
        assertEquals(before, rec.mapValues { it.value.toMap() })
    }

    @Test fun `fewer than three safe options are returned as-is`() {
        // Barely above target: only very small leaves fit.
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        mark(rec, "2026-11-02", "2026-11-26", PeriodStatus.PRESENT)
        mark(rec, "2026-11-20", "2026-11-20", PeriodStatus.ABSENT)
        val r = plan("2026-11-26", rec)
        if (r is BunkPlannerResult.Ready) {
            assertTrue(r.options.size <= 3)
            r.options.forEach { assertNotNull(it.explanation); assertTrue(it.marginPoints >= 0.0) }
        } else {
            assertTrue(r is BunkPlannerResult.NoSafeOption)
        }
    }

    @Test fun `tier reflects the safety buffer without being a hard filter`() {
        val r = readyScenario()
        r.options.forEach {
            val comfortable = AttendanceProjection.meetsTarget(
                it.projectedAttended, it.projectedConducted, (it.requiredTarget + profile.safetyBuffer).coerceAtMost(100)
            )
            assertEquals(if (comfortable) SafetyTier.COMFORTABLE else SafetyTier.TIGHT, it.tier)
        }
    }
}
