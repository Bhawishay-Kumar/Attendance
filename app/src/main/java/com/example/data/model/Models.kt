package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class Batch {
    BATCH_A,
    BATCH_B;

    val displayName: String
        get() = when (this) {
            BATCH_A -> "Batch A"
            BATCH_B -> "Batch B"
        }
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class HapticIntensity {
    SOFT,
    MEDIUM,
    HARD
}

enum class CourseSelection {
    MAJOR_HONOURS,
    MINOR_ELECTIVE,
    DIGITAL_LIBRARY;

    val shortTitle: String
        get() = when (this) {
            MAJOR_HONOURS -> "Honours (HTFT 501)"
            MINOR_ELECTIVE -> "Minor (MNPM 501)"
            DIGITAL_LIBRARY -> "Digital Library"
        }

    val subtitle: String
        get() = when (this) {
            MAJOR_HONOURS -> "Clothing Comfort"
            MINOR_ELECTIVE -> "Production Planning & Control"
            DIGITAL_LIBRARY -> "Auto-Present 100%"
        }
}

enum class SubjectCategory {
    ALL,
    MAJOR,
    MINOR,
    LIBRARY
}

enum class PeriodStatus(val code: String) {
    PRESENT("P"),
    ABSENT("A"),
    NO_CLASS("N"),
    AUTO_PRESENT("AUTO_P"),
    PENDING("U");

    companion object {
        fun fromCode(code: String?): PeriodStatus = when (code?.trim()?.uppercase()) {
            "P", "PRESENT" -> PRESENT
            "A", "ABSENT" -> ABSENT
            "N", "NO_CLASS", "CANCELLED" -> NO_CLASS
            "AUTO_P", "AUTO_PRESENT" -> AUTO_PRESENT
            "U", "PENDING", "" , null -> PENDING
            else -> PENDING
        }
    }
}

@Immutable
data class Subject(
    val code: String,
    val shortName: String,
    val fullName: String,
    val creditHours: Int = 1,
    val category: SubjectCategory = SubjectCategory.MAJOR,
    val isAutoPresent: Boolean = false,
    val iconKey: String = "book"
)

@Immutable
data class PeriodSlot(
    val periodNumber: Int,
    val periodLabel: String,
    val startTime: String,
    val endTime: String,
    val timeRange: String,
    val subject: Subject,
    val isTwoHourBlock: Boolean = false,
    val isTutorial: Boolean = false
)

enum class TierStatus(val label: String, val colorHex: Long) {
    NO_DATA("No Data Yet", 0xFF94A3B8),
    COMPLETELY_SAFE("Completely Safe", 0xFF10B981),
    SAFE_ZONE("Safe Zone", 0xFFF59E0B),
    DANGER_ZONE("Danger Zone", 0xFFF43F5E)
}

@Stable
@IgnoreExtraProperties
data class StudentProfile(
    val userId: String = "",
    val name: String = "",
    val dateOfBirth: String = "",
    val batch: String = "BATCH_A",
    val courseSelection: String = "MAJOR_HONOURS",
    val examTarget: Int = 75,
    val stipendTarget: Int = 80,
    val safetyBuffer: Int = 4,
    val futureMarkingEnabled: Boolean = false,
    @get:PropertyName("isDarkMode") @set:PropertyName("isDarkMode") var isDarkMode: Boolean = true,
    val starfieldAnimationEnabled: Boolean = true,
    val progressiveBlurEnabled: Boolean = true,
    val dailyReminderEnabled: Boolean = true,
    val weeklyReminderEnabled: Boolean = true,
    val mainObjective: String = "Maintain my attendance target",
    @get:PropertyName("isOnboardingCompleted") @set:PropertyName("isOnboardingCompleted") var isOnboardingCompleted: Boolean = false,
    // --- Settings & Profile redesign fields (all backward compatible, all optional) ---
    /** Empty = never chosen; resolved from legacy isDarkMode (see themeModeEnum). */
    val themeMode: String = "",
    val rollNumber: String = "",
    val academicSession: String = "",
    /** Absolute path of the locally saved profile photo (never synced to cloud). */
    val photoPath: String = "",
    val classScheduleAlertsEnabled: Boolean = false,
    /** 24-hour "HH:mm". */
    val dailyReminderTime: String = "16:45",
    val hapticFeedbackEnabled: Boolean = true,
    val hapticIntensity: String = "MEDIUM",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val themeModeEnum: ThemeMode
        get() = runCatching { ThemeMode.valueOf(themeMode) }
            .getOrElse { if (isDarkMode) ThemeMode.DARK else ThemeMode.LIGHT }

    @get:Exclude
    val hapticIntensityEnum: HapticIntensity
        get() = runCatching { HapticIntensity.valueOf(hapticIntensity) }.getOrDefault(HapticIntensity.MEDIUM)

    val batchEnum: Batch
        get() = runCatching { Batch.valueOf(batch) }.getOrDefault(Batch.BATCH_A)

    val courseSelectionEnum: CourseSelection
        get() = runCatching { CourseSelection.valueOf(courseSelection) }.getOrDefault(CourseSelection.MAJOR_HONOURS)
}

@Stable
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val date: String = "",
    val userId: String = "",
    val monthId: String = "",
    @get:PropertyName("isHoliday") @set:PropertyName("isHoliday") var isHoliday: Boolean = false,
    val holidayReason: String = "",
    val periodsJson: String = "{}",
    val attendedCredits: Int = 0,
    val conductedCredits: Int = 0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Stable
@IgnoreExtraProperties
data class MonthSummary(
    @DocumentId val monthId: String = "",
    val userId: String = "",
    val attendedCredits: Int = 0,
    val conductedCredits: Int = 0,
    val percentage: Double = 0.0,
    @get:PropertyName("isSavedArchive") @set:PropertyName("isSavedArchive") var isSavedArchive: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
data class SubjectSummary(
    val subject: Subject,
    val attendedCredits: Int,
    val conductedCredits: Int,
    val percentage: Double,
    val recoveryHoursNeeded: Int,
    val safeSkipHoursAvailable: Int,
    val tierStatus: TierStatus
)

@Immutable
data class MonthlyAnalytics(
    val monthId: String,
    val monthName: String,
    val totalAttendedCredits: Int,
    val totalConductedCredits: Int,
    val percentage: Double,
    val examTarget: Int,
    val stipendTarget: Int,
    val safetyBuffer: Int,
    val isExamEligible: Boolean,
    val isStipendEligible: Boolean,
    val tierStatus: TierStatus,
    val recoveryHoursNeeded75: Int,
    val recoveryHoursNeeded80: Int,
    val safeSkipHoursAvailable: Int,
    val autoPresentBoostHours: Int,
    val isSavedArchive: Boolean
)
