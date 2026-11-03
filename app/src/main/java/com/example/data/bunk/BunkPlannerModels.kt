package com.example.data.bunk

import com.example.data.model.Subject
import java.time.LocalDate

/** How one calendar date inside a break is classified. */
enum class BunkDayKind { BUNK, WEEKEND, HOLIDAY, NO_CLASS_DAY }

/** Comfortable = projected at or above (highest target + safety buffer); Tight = meets targets only. */
enum class SafetyTier { COMFORTABLE, TIGHT }

/** Subject status against the configured targets. Subject results are warnings, not eligibility gates. */
enum class SubjectStatus { ON_TRACK, BELOW_STIPEND, BELOW_EXAM }

enum class NoSafeReason {
    /** Even attending every remaining class ends the month below the required target. */
    BELOW_TARGET_EVEN_IF_ATTEND_ALL,
    /** Every possible full-day leave would drop the month-end figure below the target. */
    EVERY_LEAVE_TOO_COSTLY,
    /** No remaining upcoming working day has a manually attended class. */
    NO_ELIGIBLE_DAYS
}

data class BunkSessionEntry(
    val subject: Subject,
    val periodLabel: String,
    val weight: Int,
    val isAutoCredited: Boolean,
    /** True when this manual session is simulated Absent. Auto-credited sessions are never missed. */
    val isMissed: Boolean
)

data class BunkDayEntry(
    val date: LocalDate,
    val kind: BunkDayKind,
    /** Holiday name when known (official list); null for weekends or user-marked holidays. */
    val label: String?,
    val sessions: List<BunkSessionEntry>,
    val classesMissed: Int,
    val subjectsAffected: List<String>
)

data class SubjectImpact(
    val subject: Subject,
    val currentAttended: Int,
    val currentConducted: Int,
    val missedWeight: Int,
    val projectedAttended: Int,
    val projectedConducted: Int,
    val currentPercent: Double?,
    val projectedPercent: Double,
    val status: SubjectStatus
)

data class BunkOption(
    val rank: Int,
    val start: LocalDate,
    val end: LocalDate,
    val days: List<BunkDayEntry>,
    val bunkDates: List<LocalDate>,
    val calendarDays: Int,
    val workingDaysMissed: Int,
    val classesMissed: Int,
    val weightedMissed: Int,
    val weekendDays: Int,
    val holidayDays: Int,
    val noClassDays: Int,
    val currentPercent: Double?,
    val projectedAttended: Int,
    val projectedConducted: Int,
    /** Raw, unrounded month-end projection. Use AttendanceProjection.displayFloor() for display. */
    val projectedPercent: Double,
    val requiredTarget: Int,
    val marginPoints: Double,
    val tier: SafetyTier,
    val subjects: List<SubjectImpact>,
    val subjectWarningCount: Int,
    /** Weakest affected subject (lowest projected percentage), null when none is affected. */
    val weakestSubject: SubjectImpact?,
    val isProvisional: Boolean,
    val explanation: String
)

data class BunkOverview(
    val monthLabel: String,
    val currentPercent: Double?,
    val currentAttended: Int,
    val currentConducted: Int,
    val examTarget: Int,
    val stipendTarget: Int,
    /** The higher of the exam and stipend targets: what a recommendation must satisfy. */
    val requiredTarget: Int,
    val safetyBuffer: Int,
    val horizonStart: LocalDate,
    val horizonEnd: LocalDate,
    val remainingSessions: Int,
    val remainingWeight: Int,
    val remainingWorkingDays: Int,
    val bunkEligibleDays: Int,
    /** Past manual sessions (before today) with no Present/Absent/No Class mark. They count for nothing. */
    val unmarkedPastSessions: Int,
    val belowTargetNow: Boolean,
    /** Month-end percentage if every remaining class is attended and no leave is taken. */
    val attendAllProjectedPercent: Double?
) {
    val isProvisional: Boolean get() = unmarkedPastSessions > 0
}

sealed interface BunkPlannerResult {
    val overview: BunkOverview?

    data class Ready(override val overview: BunkOverview, val options: List<BunkOption>) : BunkPlannerResult

    data class NoSafeOption(
        override val overview: BunkOverview,
        val reason: NoSafeReason,
        /** The least costly leave found and its projection, to explain how close it was. */
        val closestProjectedPercent: Double?,
        val closestDates: List<LocalDate>,
        /** Subjects projected below the required target even when attending everything remaining. */
        val weakSubjects: List<String>
    ) : BunkPlannerResult

    data class InsufficientData(override val overview: BunkOverview?, val message: String) : BunkPlannerResult

    data class NoRemainingClasses(override val overview: BunkOverview?) : BunkPlannerResult
}
