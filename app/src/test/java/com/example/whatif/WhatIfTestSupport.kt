package com.example.whatif

import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.timetable.TimetableMatrix
import com.example.data.whatif.WhatIfContext
import java.time.LocalDate

/** Shared fixtures. "Today" is Wednesday 2026-11-04; Mon/Tue before it are marked, the rest is future. */
object Fx {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 4)
    val MON: LocalDate = LocalDate.of(2026, 11, 2)
    val TUE: LocalDate = LocalDate.of(2026, 11, 3)
    val THU: LocalDate = LocalDate.of(2026, 11, 5)
    val FRI: LocalDate = LocalDate.of(2026, 11, 6)
    val NEXT_MON: LocalDate = LocalDate.of(2026, 11, 9)
    val DEC_1: LocalDate = LocalDate.of(2026, 12, 1)

    fun slots(d: LocalDate): List<PeriodSlot> =
        TimetableMatrix.getScheduleForDay(d.dayOfWeek.value, Batch.BATCH_A, CourseSelection.MAJOR_HONOURS)

    fun manual(d: LocalDate) = slots(d).filter { !it.subject.isAutoPresent }
    fun auto(d: LocalDate) = slots(d).filter { it.subject.isAutoPresent }
    fun w(list: List<PeriodSlot>) = list.sumOf { it.subject.creditHours }

    /** Monday all manual present; Tuesday first manual class absent, rest present. */
    fun baseRecords(): Map<String, Map<Int, PeriodStatus>> {
        val mon = manual(MON).associate { it.periodNumber to PeriodStatus.PRESENT }
        val tm = manual(TUE)
        val tue = tm.associate { it.periodNumber to if (it.periodNumber == tm.first().periodNumber) PeriodStatus.ABSENT else PeriodStatus.PRESENT }
        return mapOf(MON.toString() to mon, TUE.toString() to tue)
    }

    fun expectedBaselineCond(): Int = w(slots(MON)) + w(slots(TUE))
    fun expectedBaselineAtt(): Int = expectedBaselineCond() - manual(TUE).first().subject.creditHours

    fun ctx(
        records: Map<String, Map<Int, PeriodStatus>> = baseRecords(),
        holidays: Set<String> = emptySet(),
        profile: StudentProfile = StudentProfile(),
        today: LocalDate = TODAY
    ) = WhatIfContext(profile, records, { it in holidays }, today)
}
