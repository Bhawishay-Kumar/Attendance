package com.example.data.whatif

import com.example.data.math.AttendanceMath
import com.example.data.model.StudentProfile
import java.time.LocalDate
import kotlin.math.roundToInt

enum class PlanDecision { PRESENT, ABSENT }

enum class ProjectionMode(val label: String) {
    PLAN_ONLY("Plan Only"),
    MONTH_END("Month End")
}

enum class TargetStatus { MET, NOT_MET, NO_DATA }

enum class DayAvailability { PLANNABLE, PAST_OR_TODAY, WEEKEND, HOLIDAY, NO_CLASSES, ALL_CANCELLED, AUTO_ONLY }

enum class LineKind { PLANNED_PRESENT, PLANNED_ABSENT, ASSUMED_PRESENT, AUTO_CREDITED, CANCELLED, NOT_COUNTED }

enum class TargetKind(val label: String) { EXAM("Exam"), STIPEND("Stipend") }

enum class RecoveryState { TARGET_MET, APPROACHING, ATTEND_NEXT, NOT_ENOUGH_SESSIONS, UNREACHABLE_100, NO_DATA }

enum class BaselineCompleteness { COMPLETE, PARTIAL, NONE }

/** Everything the calculator needs, read-only. Never contains anything the predictor may write back. */
class WhatIfContext(
    val profile: StudentProfile,
    val records: Map<String, Map<Int, com.example.data.model.PeriodStatus>>,
    val isHoliday: (String) -> Boolean,
    val today: LocalDate
)

/** Exact (unrounded) target checks. Rounded percentages are for display only. */
object WhatIfMath {
    fun meetsTarget(attended: Int, conducted: Int, target: Int): TargetStatus {
        if (conducted <= 0) return TargetStatus.NO_DATA
        val t = target.coerceIn(0, 100).toLong()
        return if (attended.toLong() * 100L >= t * conducted.toLong()) TargetStatus.MET else TargetStatus.NOT_MET
    }

    fun pct(attended: Int, conducted: Int): Double = AttendanceMath.calculatePercentage(attended, conducted)

    /** Sign of (a1/c1 - a2/c2) using exact cross-multiplication. A side with no data ranks lowest. */
    fun compareRatio(a1: Int, c1: Int, a2: Int, c2: Int): Int {
        if (c1 <= 0 && c2 <= 0) return 0
        if (c1 <= 0) return -1
        if (c2 <= 0) return 1
        return (a1.toLong() * c2.toLong()).compareTo(a2.toLong() * c1.toLong())
    }

    fun delta(from: Double, to: Double): Double = ((to - from) * 10.0).roundToInt() / 10.0
}

data class PlannedClass(
    val date: String,
    val periodNumber: Int,
    val subjectCode: String,
    val decision: PlanDecision
)

data class WhatIfPlan(val items: List<PlannedClass> = emptyList()) {
    val isEmpty: Boolean get() = items.isEmpty()
    val dates: List<String> get() = items.map { it.date }.distinct().sorted()
    fun forDate(date: String): List<PlannedClass> = items.filter { it.date == date }
    fun decisionFor(date: String, period: Int): PlanDecision? =
        items.firstOrNull { it.date == date && it.periodNumber == period }?.decision

    fun with(item: PlannedClass): WhatIfPlan =
        copy(items = items.filterNot { it.date == item.date && it.periodNumber == item.periodNumber } + item)

    fun without(date: String, period: Int): WhatIfPlan =
        copy(items = items.filterNot { it.date == date && it.periodNumber == period })

    fun withoutDate(date: String): WhatIfPlan = copy(items = items.filterNot { it.date == date })
    val presentCount: Int get() = items.count { it.decision == PlanDecision.PRESENT }
    val absentCount: Int get() = items.count { it.decision == PlanDecision.ABSENT }
}

data class ClassLine(
    val kind: LineKind,
    val date: String,
    val periodNumber: Int,
    val periodLabel: String,
    val subjectCode: String,
    val subjectName: String,
    val weight: Int,
    val isLab: Boolean
)

data class ExcludedDate(val date: String, val reason: String)
data class DroppedDecision(val item: PlannedClass, val reason: String)

data class SubjectTotals(
    val code: String,
    val shortName: String,
    val fullName: String,
    val isLab: Boolean,
    val creditHours: Int,
    val attended: Int,
    val conducted: Int
)

data class WhatIfBaseline(
    val rangeStart: String?,
    val rangeEnd: String,
    val attended: Int,
    val conducted: Int,
    val subjects: List<SubjectTotals>,
    val completeness: BaselineCompleteness,
    val unmarkedSessions: Int,
    val unmarkedDays: Int,
    val sessionStartHint: String?,
    val notes: List<String>
) {
    val hasData: Boolean get() = rangeStart != null && conducted > 0
    val percentage: Double get() = WhatIfMath.pct(attended, conducted)
}

data class TargetCheck(val target: Int, val status: TargetStatus, val hoursNeeded: Int, val safeSkipHours: Int)

data class SubjectProjection(
    val code: String,
    val shortName: String,
    val fullName: String,
    val isLab: Boolean,
    val baselineAttended: Int,
    val baselineConducted: Int,
    val projectedAttended: Int,
    val projectedConducted: Int,
    val exam: TargetCheck,
    val stipend: TargetCheck
) {
    val baselinePct: Double get() = WhatIfMath.pct(baselineAttended, baselineConducted)
    val projectedPct: Double get() = WhatIfMath.pct(projectedAttended, projectedConducted)
}

data class WhatIfResult(
    val mode: ProjectionMode,
    val horizonEnd: String?,
    val calcDate: String,
    val batch: String,
    val course: String,
    val examTarget: Int,
    val stipendTarget: Int,
    val safetyBuffer: Int,
    val baseline: WhatIfBaseline,
    val projectedAttended: Int,
    val projectedConducted: Int,
    val exam: TargetCheck,
    val stipend: TargetCheck,
    val subjects: List<SubjectProjection>,
    val lines: List<ClassLine>,
    val excludedDates: List<ExcludedDate>,
    val dropped: List<DroppedDecision>,
    val assumptions: List<String>,
    val boundaryNote: String?
) {
    val baselinePct: Double get() = baseline.percentage
    val projectedPct: Double get() = WhatIfMath.pct(projectedAttended, projectedConducted)
    val deltaPoints: Double?
        get() = if (baseline.conducted > 0 && projectedConducted > 0) WhatIfMath.delta(baselinePct, projectedPct) else null

    fun count(kind: LineKind): Int = lines.count { it.kind == kind }
    fun weight(kind: LineKind): Int = lines.filter { it.kind == kind }.sumOf { it.weight }
}

data class FlipOutcome(
    val classCount: Int,
    val weightedHours: Int,
    val projectedPct: Double,
    val status: TargetStatus,
    val firstDate: String
)

data class RecoverySuggestion(
    val scopeCode: String?,
    val scopeName: String,
    val targetKind: TargetKind,
    val target: Int,
    val state: RecoveryState,
    val currentPct: Double,
    val projectedPct: Double,
    val hoursNeeded: Int,
    val classesNeeded: Int?,
    val availableHours: Int,
    val firstDate: String?,
    val lastDate: String?,
    val pctIfAttended: Double?,
    val safeSkipHours: Int,
    val flip: FlipOutcome?,
    val message: String
)

data class BestCaseSummary(
    val attended: Int,
    val conducted: Int,
    val exam: TargetStatus,
    val stipend: TargetStatus,
    val horizonEnd: String
) {
    val pct: Double get() = WhatIfMath.pct(attended, conducted)
}

data class WhatIfSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val id: String,
    val name: String,
    val savedAtMillis: Long,
    val isRecalculation: Boolean = false,
    val derivedFromId: String? = null,
    val mode: ProjectionMode,
    val planningStyle: String,
    val plan: WhatIfPlan,
    val result: WhatIfResult,
    val recovery: List<RecoverySuggestion>,
    val bestCase: BestCaseSummary?
) {
    companion object {
        const val CURRENT_SCHEMA = 1
        const val STYLE_WHOLE_DAY = "WHOLE_DAY"
        const val STYLE_SELECTED = "SELECTED_CLASSES"
    }
}
