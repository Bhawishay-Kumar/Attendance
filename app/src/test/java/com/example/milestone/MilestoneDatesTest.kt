package com.example.milestone

import com.example.data.milestone.CountdownStatus
import com.example.data.milestone.MilestoneDates
import com.example.data.milestone.OfficialSchedules
import com.example.data.model.Milestone
import com.example.data.model.MilestoneRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MilestoneDatesTest {

    private val today = LocalDate.parse("2026-10-31")

    private fun m(
        date: String,
        rec: MilestoneRecurrence = MilestoneRecurrence.NONE,
        start: String = "",
        id: String = "a",
        pinned: Boolean = false,
        pinnedAt: Long = 0,
        completed: Boolean = false,
        reminders: List<Int> = emptyList()
    ) = Milestone(
        id = id, title = "t", targetDate = date, recurrence = rec, startDate = start,
        pinned = pinned, pinnedAt = pinnedAt, completed = completed, reminderDays = reminders
    )

    private fun d(s: String) = LocalDate.parse(s)

    @Test fun upcomingTodayPassedCompleted() {
        assertEquals(CountdownStatus.DaysLeft(10), MilestoneDates.resolve(m("2026-11-10"), today)!!.status)
        assertEquals(CountdownStatus.Today, MilestoneDates.resolve(m("2026-10-31"), today)!!.status)
        assertEquals(CountdownStatus.DaysPassed(3), MilestoneDates.resolve(m("2026-10-28"), today)!!.status)
        assertEquals(CountdownStatus.Completed, MilestoneDates.resolve(m("2026-12-01", completed = true), today)!!.status)
    }

    @Test fun invalidOrMissingDatesAreSkipped() {
        assertNull(MilestoneDates.resolve(m("2026-02-30"), today))
        assertNull(MilestoneDates.resolve(m(""), today))
    }

    @Test fun yearlyBirthdayRollsToNextOccurrence() {
        assertEquals(d("2027-05-15"), MilestoneDates.resolve(m("2004-05-15", MilestoneRecurrence.YEARLY), today)!!.date)
        assertEquals(d("2026-11-05"), MilestoneDates.resolve(m("2004-11-05", MilestoneRecurrence.YEARLY), today)!!.date)
        assertEquals(CountdownStatus.Today, MilestoneDates.resolve(m("2004-10-31", MilestoneRecurrence.YEARLY), today)!!.status)
    }

    @Test fun leapDayFallsOnFeb28InCommonYears() {
        val leap = m("2004-02-29", MilestoneRecurrence.YEARLY)
        assertEquals(d("2027-02-28"), MilestoneDates.resolve(leap, today)!!.date)
        assertEquals(d("2028-02-29"), MilestoneDates.resolve(leap, d("2027-12-01"))!!.date)
    }

    @Test fun monthlyClampsToMonthLength() {
        val thirtyFirst = m("2026-01-31", MilestoneRecurrence.MONTHLY)
        assertEquals(d("2026-11-30"), MilestoneDates.resolve(thirtyFirst, d("2026-11-15"))!!.date)
        assertEquals(d("2027-02-28"), MilestoneDates.resolve(thirtyFirst, d("2027-02-01"))!!.date)
    }

    @Test fun progressOnlyWhenStartAndEndAreMeaningful() {
        assertEquals(0.5f, MilestoneDates.resolve(m("2026-11-10", start = "2026-10-21"), today)!!.progress!!, 0.0001f)
        assertNull(MilestoneDates.resolve(m("2026-11-10"), today)!!.progress)
        assertNull(MilestoneDates.resolve(m("2026-11-01", start = "2026-10-31"), today)!!.progress)
        assertNull(MilestoneDates.resolve(m("2026-10-01", start = "2026-09-01"), today)!!.progress)
        assertNotNull(MilestoneDates.resolve(m("2004-05-15", MilestoneRecurrence.YEARLY), today)!!.progress)
    }

    @Test fun pinnedFirstThenUpcomingThenPassedThenCompleted() {
        val items = listOf(
            m("2026-12-01", id = "late"), m("2026-11-05", id = "soon"), m("2026-10-01", id = "past"),
            m("2027-01-01", id = "pin2", pinned = true, pinnedAt = 20),
            m("2027-06-01", id = "pin1", pinned = true, pinnedAt = 10),
            m("2026-11-20", id = "done", completed = true)
        ).mapNotNull { MilestoneDates.resolve(it, today) }
        assertEquals(
            listOf("pin1", "pin2", "soon", "late", "past", "done"),
            MilestoneDates.sortForDisplay(items).map { it.id }
        )
    }

    @Test fun remindersFireAtNineLocalAndSkipThePast() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = Instant.parse("2026-10-31T00:00:00Z")
        val all = MilestoneDates.reminderTimes(m("2026-11-10", reminders = listOf(7, 3, 1)), today, zone, now)
        assertEquals(setOf(7, 3, 1), all.keys)
        assertEquals(ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, zone).toInstant(), all[7])
        assertEquals(setOf(1), MilestoneDates.reminderTimes(m("2026-11-02", reminders = listOf(7, 3, 1)), today, zone, now).keys)
        assertTrue(MilestoneDates.reminderTimes(m("2026-11-10", completed = true, reminders = listOf(1)), today, zone, now).isEmpty())
    }

    @Test fun dateOfBirthValidation() {
        assertEquals(d("2004-05-15"), MilestoneDates.parseDateOfBirth("2004-05-15", today))
        assertNull(MilestoneDates.parseDateOfBirth("2030-01-01", today))
        assertNull(MilestoneDates.parseDateOfBirth("", today))
    }

    @Test fun officialSchedulesAreSourcedAndExpire() {
        assertEquals(6, OfficialSchedules.forPreset("gate", today)!!.dates.size)
        assertNull(OfficialSchedules.forPreset("gate", d("2027-03-01")))
        assertTrue(OfficialSchedules.isOfficial("gate", "2027-02-13", today))
        assertTrue(!OfficialSchedules.isOfficial("gate", "2027-02-15", today))
        assertNull(OfficialSchedules.forPreset("aktu", today)) // no official AKTU date sheet -> manual entry
    }
}
