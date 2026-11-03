package com.example.data.model

enum class MilestoneCategory(val label: String) {
    EXAM("Examination"),
    BIRTHDAY("Birthday"),
    SUBMISSION("Submission"),
    ASSIGNMENT("Assignment"),
    SEMESTER("Semester"),
    PRACTICAL("Practical"),
    HOLIDAY("Holiday"),
    GOAL("Personal Goal"),
    OTHER("Other");

    companion object {
        fun fromKey(key: String?): MilestoneCategory = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

enum class MilestoneRecurrence(val label: String) {
    NONE("Does not repeat"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    companion object {
        fun fromKey(key: String?): MilestoneRecurrence = entries.firstOrNull { it.name == key } ?: NONE
    }
}

/** Where the date came from. OFFICIAL = picked from a sourced schedule, PROFILE = student profile (DOB). */
enum class MilestoneDateSource {
    MANUAL, OFFICIAL, PROFILE;

    companion object {
        fun fromKey(key: String?): MilestoneDateSource = entries.firstOrNull { it.name == key } ?: MANUAL
    }
}

/** Theme-neutral accent keys; the UI maps them to light/dark colours. */
enum class MilestoneAccent {
    PINK, VIOLET, BLUE, GREEN, AMBER, RED;

    companion object {
        fun fromKey(key: String?): MilestoneAccent = entries.firstOrNull { it.name == key } ?: VIOLET
    }
}

object MilestoneLimits {
    const val MAX_TITLE = 80
    const val MAX_DESCRIPTION = 240
    const val MAX_MILESTONES = 100
    val REMINDER_OPTIONS: List<Int> = listOf(7, 3, 1)
}

/**
 * A single user milestone. Dates are ISO-8601 strings (yyyy-MM-dd) so they never shift with time zones.
 * [targetDate] is the anchor: for recurring milestones the countdown points to the next occurrence.
 */
@androidx.compose.runtime.Immutable
data class Milestone(
    val id: String,
    val title: String,
    val description: String = "",
    val category: MilestoneCategory = MilestoneCategory.OTHER,
    val targetDate: String,
    val accent: MilestoneAccent = MilestoneAccent.VIOLET,
    val recurrence: MilestoneRecurrence = MilestoneRecurrence.NONE,
    val reminderDays: List<Int> = emptyList(),
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L,
    val createdAt: Long = 0L,
    /** Date the milestone was added; the real start of its progress bar. */
    val startDate: String = "",
    val completed: Boolean = false,
    val presetKey: String? = null,
    val dateSource: MilestoneDateSource = MilestoneDateSource.MANUAL,
    val sourceLabel: String? = null,
    /** Epoch millis of last change; used for last-write-wins sync. */
    val updatedAt: Long = 0L,
    /** Tombstone so deletions sync to other devices. */
    val deleted: Boolean = false,
    /** Local-only: has changes not yet acknowledged by the cloud. */
    val dirty: Boolean = false
)
