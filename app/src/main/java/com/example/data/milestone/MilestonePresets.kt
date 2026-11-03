package com.example.data.milestone

import com.example.data.holidays.AktuHolidays
import com.example.data.model.MilestoneAccent
import com.example.data.model.MilestoneCategory
import com.example.data.model.MilestoneRecurrence
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MilestonePreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val category: MilestoneCategory,
    val accent: MilestoneAccent,
    val recurrence: MilestoneRecurrence = MilestoneRecurrence.NONE,
    /** Whether the preset can carry a "Verified" vs "Manual" date badge. */
    val officialCapable: Boolean = false,
    /** Shown in the form when no official date exists, so the user knows why it is manual. */
    val manualNote: String? = null
)

object MilestonePresets {
    const val BIRTHDAY = "birthday"
    const val GATE = "gate"
    const val AKTU = "aktu"
    const val HOLIDAY = "holiday"

    /** Shown in this order in the Readymade grid. */
    val all: List<MilestonePreset> = listOf(
        MilestonePreset(
            AKTU, "AKTU Final Semester Exam", "Final Semester",
            "End semester examination",
            MilestoneCategory.EXAM, MilestoneAccent.RED,
            officialCapable = true,
            manualNote = "No official AKTU date sheet is published yet. Pick the date yourself and edit it once AKTU releases the schedule."
        ),
        MilestonePreset(
            BIRTHDAY, "Birthday", "Special Day",
            "Your special day",
            MilestoneCategory.BIRTHDAY, MilestoneAccent.PINK,
            recurrence = MilestoneRecurrence.YEARLY
        ),
        MilestonePreset(
            "semester", "Semester Start / End", "Academic",
            "Semester milestone",
            MilestoneCategory.SEMESTER, MilestoneAccent.GREEN
        ),
        MilestonePreset(
            "goal", "Personal Goal", "Goal",
            "Something I'm working towards",
            MilestoneCategory.GOAL, MilestoneAccent.GREEN
        )
    )

    fun byKey(key: String?): MilestonePreset? = all.firstOrNull { it.key == key }
}

data class OfficialDate(val iso: String, val label: String)

data class OfficialSchedule(
    val dates: List<OfficialDate>,
    val sourceName: String,
    val sourceUrl: String?,
    val verifiedOn: String,
    val note: String
)

/**
 * Sourced reference dates. Nothing here is generated: each entry cites where it came from and
 * disappears once its dates have passed. Anything not listed must be entered manually by the user.
 */
object OfficialSchedules {

    private val dayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    // GATE 2027 (organised by IIT Madras). The official "important dates" page lists the exam DAYS,
    // not per-paper dates; each candidate's paper/session is on their admit card.
    // Source: https://gate2027.iitm.ac.in/important_dates  (checked 2026-10-30; "all dates are liable to change")
    private val gate2027Days = listOf(
        "2027-02-06", "2027-02-07", "2027-02-13", "2027-02-14", "2027-02-20", "2027-02-21"
    )

    fun forPreset(key: String?, today: LocalDate = LocalDate.now()): OfficialSchedule? = when (key) {
        MilestonePresets.GATE -> {
            val upcoming = gate2027Days.mapNotNull { MilestoneDates.parse(it) }
                .filter { !it.isBefore(today) }
                .map { OfficialDate(it.toString(), it.format(dayLabel)) }
            if (upcoming.isEmpty()) null else OfficialSchedule(
                dates = upcoming,
                sourceName = "gate2027.iitm.ac.in",
                sourceUrl = "https://gate2027.iitm.ac.in/important_dates",
                verifiedOn = "2026-10-30",
                note = "Official GATE 2027 exam days. Pick the day of your paper; your admit card confirms it. Dates are liable to change."
            )
        }
        MilestonePresets.HOLIDAY -> {
            val upcoming = AktuHolidays.OFFICIAL_HOLIDAYS_2026
                .mapNotNull { h -> MilestoneDates.parse(h.date)?.let { it to h.name } }
                .filter { !it.first.isBefore(today) }
                .take(8)
                .map { (d, name) -> OfficialDate(d.toString(), "$name · ${d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}") }
            if (upcoming.isEmpty()) null else OfficialSchedule(
                dates = upcoming,
                sourceName = "AKTU holiday list in this app",
                sourceUrl = null,
                verifiedOn = "",
                note = "Upcoming holidays from the AKTU holiday list already used for attendance."
            )
        }
        else -> null
    }

    /** True when [iso] is one of the sourced dates for this preset. */
    fun isOfficial(key: String?, iso: String, today: LocalDate = LocalDate.now()): Boolean =
        forPreset(key, today)?.dates?.any { it.iso == iso } == true
}
