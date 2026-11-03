package com.example.ui.countdown

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.milestone.CountdownStatus
import com.example.data.model.MilestoneCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation helpers for the shared countdown model (see data/milestone/MilestoneDates.kt).
 * The old demo events with made-up relative dates were removed; every card now comes from a real,
 * stored milestone.
 */
fun MilestoneCategory.icon(): ImageVector = when (this) {
    MilestoneCategory.EXAM -> Icons.Rounded.School
    MilestoneCategory.BIRTHDAY -> Icons.Rounded.Cake
    MilestoneCategory.SUBMISSION -> Icons.Rounded.Description
    MilestoneCategory.ASSIGNMENT -> Icons.Rounded.EditCalendar
    MilestoneCategory.SEMESTER -> Icons.Rounded.CalendarMonth
    MilestoneCategory.PRACTICAL -> Icons.Rounded.Science
    MilestoneCategory.HOLIDAY -> Icons.Rounded.BeachAccess
    MilestoneCategory.GOAL -> Icons.Rounded.Flag
    MilestoneCategory.OTHER -> Icons.Rounded.Star
}

private val cardDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun LocalDate.cardText(): String = format(cardDateFormat)

/** Big text inside the countdown box. */
fun CountdownStatus.bigText(): String = when (this) {
    is CountdownStatus.DaysLeft -> days.toString()
    CountdownStatus.Today -> "Today"
    is CountdownStatus.DaysPassed -> days.toString()
    CountdownStatus.Completed -> "Done"
}

/** Caption under the big text (empty for Today). */
fun CountdownStatus.caption(): String = when (this) {
    is CountdownStatus.DaysLeft -> if (days == 1L) "DAY LEFT" else "DAYS LEFT"
    CountdownStatus.Today -> ""
    is CountdownStatus.DaysPassed -> if (days == 1L) "DAY PASSED" else "DAYS PASSED"
    CountdownStatus.Completed -> "COMPLETED"
}

fun CountdownStatus.spokenText(): String = when (this) {
    is CountdownStatus.DaysLeft -> if (days == 1L) "1 day left" else "$days days left"
    CountdownStatus.Today -> "today"
    is CountdownStatus.DaysPassed -> if (days == 1L) "1 day passed" else "$days days passed"
    CountdownStatus.Completed -> "completed"
}
