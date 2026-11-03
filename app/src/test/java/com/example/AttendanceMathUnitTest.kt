package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.holidays.AktuHolidays
import com.example.data.math.AttendanceMath
import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.model.SubjectCategory
import com.example.data.model.TierStatus
import com.example.data.repository.AttendanceRepository
import com.example.data.timetable.TimetableMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttendanceMathUnitTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bhtt_attendance_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testPercentageCalculation() {
        assertEquals(0.0, AttendanceMath.calculatePercentage(0, 0), 0.01)
        assertEquals(0.0, AttendanceMath.calculatePercentage(0, 10), 0.01)
        assertEquals(84.3, AttendanceMath.calculatePercentage(118, 140), 0.01)
        assertEquals(75.0, AttendanceMath.calculatePercentage(75, 100), 0.01)
        assertEquals(80.0, AttendanceMath.calculatePercentage(80, 100), 0.01)
    }

    @Test
    fun testTierStatusSign() {
        assertEquals(TierStatus.NO_DATA, AttendanceMath.calculateTierStatus(0.0, 80, 4, 0))
        assertEquals(TierStatus.COMPLETELY_SAFE, AttendanceMath.calculateTierStatus(85.0, 80, 4))
        assertEquals(TierStatus.COMPLETELY_SAFE, AttendanceMath.calculateTierStatus(84.0, 80, 4))
        assertEquals(TierStatus.SAFE_ZONE, AttendanceMath.calculateTierStatus(82.5, 80, 4))
        assertEquals(TierStatus.SAFE_ZONE, AttendanceMath.calculateTierStatus(80.0, 80, 4))
        assertEquals(TierStatus.DANGER_ZONE, AttendanceMath.calculateTierStatus(79.9, 80, 4))
        assertEquals(TierStatus.DANGER_ZONE, AttendanceMath.calculateTierStatus(65.0, 80, 4))
    }

    @Test
    fun testRecoveryHoursCalculationAndOverflowProtection() {
        assertEquals(50, AttendanceMath.calculateRecoveryHours(70, 100, 80))
        assertEquals(0, AttendanceMath.calculateRecoveryHours(85, 100, 80))
        assertEquals(0, AttendanceMath.calculateRecoveryHours(0, 0, 80))
        // 100% target edge cases
        assertEquals(0, AttendanceMath.calculateRecoveryHours(10, 10, 100))
        assertEquals(-1, AttendanceMath.calculateRecoveryHours(9, 10, 100))

        // Extreme integer boundary overflow check (99% target with Int.MAX_VALUE conducted)
        val extremeRecovery = AttendanceMath.calculateRecoveryHours(0, Int.MAX_VALUE, 99)
        assertEquals(Int.MAX_VALUE, extremeRecovery)
        assertTrue(extremeRecovery > 0)
    }

    @Test
    fun testSafeSkipHoursCalculationAndOverflowProtection() {
        assertEquals(12, AttendanceMath.calculateSafeSkipHours(90, 100, 80))
        assertEquals(0, AttendanceMath.calculateSafeSkipHours(75, 100, 80))
        assertEquals(0, AttendanceMath.calculateSafeSkipHours(0, 0, 80))

        // Extreme integer boundary overflow check (1% target with Int.MAX_VALUE attended/conducted)
        val extremeSkip = AttendanceMath.calculateSafeSkipHours(Int.MAX_VALUE, Int.MAX_VALUE, 1)
        assertEquals(Int.MAX_VALUE, extremeSkip)
        assertTrue(extremeSkip > 0)
    }

    @Test
    fun testPeriodStatusSerializationAndFallback() {
        assertEquals(PeriodStatus.PRESENT, PeriodStatus.fromCode("P"))
        assertEquals(PeriodStatus.ABSENT, PeriodStatus.fromCode("A"))
        assertEquals(PeriodStatus.NO_CLASS, PeriodStatus.fromCode("N"))
        assertEquals(PeriodStatus.AUTO_PRESENT, PeriodStatus.fromCode("AUTO_P"))
        assertEquals(PeriodStatus.PENDING, PeriodStatus.fromCode("U"))
        assertEquals(PeriodStatus.PENDING, PeriodStatus.fromCode(""))
        assertEquals(PeriodStatus.PENDING, PeriodStatus.fromCode("UNKNOWN"))
    }

    @Test
    fun testTimetableResolutionAndCategories() {
        val htft = TimetableMatrix.resolveElective(CourseSelection.MAJOR_HONOURS)
        val mnpm = TimetableMatrix.resolveElective(CourseSelection.MINOR_ELECTIVE)
        assertEquals(SubjectCategory.MAJOR, htft.category)
        assertEquals(SubjectCategory.MINOR, mnpm.category)

        val monSlots = TimetableMatrix.getScheduleForDay(1, Batch.BATCH_A, CourseSelection.MAJOR_HONOURS)
        assertEquals(7, monSlots.size)
        assertEquals("LIBRARY", monSlots[6].subject.code)
        assertTrue(monSlots[6].subject.isAutoPresent)

        val wedA = TimetableMatrix.getScheduleForDay(3, Batch.BATCH_A, CourseSelection.MAJOR_HONOURS)
        val wedB = TimetableMatrix.getScheduleForDay(3, Batch.BATCH_B, CourseSelection.MAJOR_HONOURS)
        assertEquals("BHT553", wedA.last().subject.code)
        assertEquals(2, wedA.last().subject.creditHours)
        assertEquals("SEMINAR", wedB.last().subject.code)
        assertTrue(wedB.last().subject.isAutoPresent)
    }

    @Test
    fun testPastAutoPresentCountsWithoutSavedRecordAndFutureAutoPresentExcluded() {
        // Fix clock to Monday, 2026-10-05
        val fixedClock = LocalDate.of(2026, 10, 5)
        val repo = AttendanceRepository(context) { fixedClock }

        // Thursday 2026-10-01 has Seminar (2 credits for Batch A) and Monday 2026-10-05 has Library (1 credit).
        // Total auto-present credits up to 2026-10-05 = 3 credits.
        // Future working days in October (Oct 6..30) must NOT be counted yet.
        val octAnalytics = repo.getMonthlyAnalytics("2026-10")
        assertEquals(3, octAnalytics.totalAttendedCredits)
        assertEquals(3, octAnalytics.totalConductedCredits)
        assertEquals(3, octAnalytics.autoPresentBoostHours)

        // Enabling futureMarkingEnabled must still NOT allow future auto-present or future manual marks to inflate attendance
        repo.updateProfile(repo.profileState.value.copy(futureMarkingEnabled = true))
        val futureTueSlots = TimetableMatrix.getScheduleForDay(2, Batch.BATCH_A, CourseSelection.MAJOR_HONOURS)
        assertTrue(repo.markAllPresent("2026-10-06", futureTueSlots))

        val octAnalyticsAfterFutureMark = repo.getMonthlyAnalytics("2026-10")
        assertEquals(3, octAnalyticsAfterFutureMark.totalAttendedCredits)
        assertEquals(3, octAnalyticsAfterFutureMark.totalConductedCredits)

        // Cloud record for future date 2026-10-06 must also report 0 attended/conducted until that date arrives
        val futureCloudRecord = repo.buildCloudRecord("2026-10-06", "uid_test")
        assertEquals(0, futureCloudRecord.attendedCredits)
        assertEquals(0, futureCloudRecord.conductedCredits)
    }

    @Test
    fun testFutureMarkingBlockedAcrossAllEntryPointsWhenDisabled() {
        val fixedClock = LocalDate.of(2026, 10, 5)
        val repo = AttendanceRepository(context) { fixedClock }
        repo.updateProfile(repo.profileState.value.copy(futureMarkingEnabled = false))

        val tueSlots = TimetableMatrix.getScheduleForDay(2, Batch.BATCH_A, CourseSelection.MAJOR_HONOURS)

        assertFalse(repo.markPeriodStatus("2026-10-06", 1, PeriodStatus.PRESENT))
        assertFalse(repo.markPeriodStatus("2026-10-06", 1, PeriodStatus.ABSENT))
        assertFalse(repo.markAllPresent("2026-10-06", tueSlots))
        assertFalse(repo.markAllAbsent("2026-10-06", tueSlots))
        assertFalse(repo.markAllCancelled("2026-10-06", tueSlots))
        assertFalse(repo.setHoliday("2026-10-06", true))
        assertTrue(repo.recordsMap.value["2026-10-06"] == null)

        // Today (2026-10-05) remains markable
        assertTrue(repo.markPeriodStatus("2026-10-05", 1, PeriodStatus.PRESENT))
        assertEquals(PeriodStatus.PRESENT, repo.getPeriodsForDate("2026-10-05")[1])
    }

    @Test
    fun testCompleteProfileBackupRoundTripAndAtomicValidation() {
        val fixedClock = LocalDate.of(2026, 10, 5)
        val repo = AttendanceRepository(context) { fixedClock }

        val customProfile = StudentProfile(
            userId = "usr_42",
            name = "Bhawishya",
            dateOfBirth = "2005-10-26",
            batch = Batch.BATCH_B.name,
            courseSelection = CourseSelection.DIGITAL_LIBRARY.name,
            examTarget = 78,
            stipendTarget = 85,
            safetyBuffer = 5,
            futureMarkingEnabled = true,
            isDarkMode = false,
            starfieldAnimationEnabled = false,
            progressiveBlurEnabled = false,
            dailyReminderEnabled = false,
            weeklyReminderEnabled = false,
            mainObjective = "Custom Goal",
            isOnboardingCompleted = true,
            themeMode = "LIGHT",
            rollNumber = "220010",
            academicSession = "2026-27",
            photoPath = "/data/user/0/com.example/files/avatar.jpg",
            classScheduleAlertsEnabled = true,
            dailyReminderTime = "19:30",
            hapticFeedbackEnabled = false,
            hapticIntensity = "HARD"
        )
        repo.updateProfile(customProfile)
        repo.markPeriodStatus("2026-10-05", 1, PeriodStatus.PRESENT)
        repo.setHoliday("2026-10-01", true)

        val backupJson = repo.exportBackupJson()

        // Wipe and restore into fresh state
        repo.resetAppData()
        assertTrue(repo.importBackupJson(backupJson, replaceExisting = true))

        val restored = repo.profileState.value
        assertEquals(customProfile.name, restored.name)
        assertEquals(customProfile.batch, restored.batch)
        assertEquals(customProfile.courseSelection, restored.courseSelection)
        assertEquals(customProfile.examTarget, restored.examTarget)
        assertEquals(customProfile.stipendTarget, restored.stipendTarget)
        assertEquals(customProfile.safetyBuffer, restored.safetyBuffer)
        assertEquals(customProfile.futureMarkingEnabled, restored.futureMarkingEnabled)
        assertEquals(customProfile.isDarkMode, restored.isDarkMode)
        assertEquals(customProfile.starfieldAnimationEnabled, restored.starfieldAnimationEnabled)
        assertEquals(customProfile.progressiveBlurEnabled, restored.progressiveBlurEnabled)
        assertEquals(customProfile.dailyReminderEnabled, restored.dailyReminderEnabled)
        assertEquals(customProfile.weeklyReminderEnabled, restored.weeklyReminderEnabled)
        assertEquals(customProfile.mainObjective, restored.mainObjective)
        assertEquals(customProfile.isOnboardingCompleted, restored.isOnboardingCompleted)
        assertEquals(customProfile.themeMode, restored.themeMode)
        assertEquals(customProfile.rollNumber, restored.rollNumber)
        assertEquals(customProfile.academicSession, restored.academicSession)
        assertEquals(customProfile.photoPath, restored.photoPath)
        assertEquals(customProfile.classScheduleAlertsEnabled, restored.classScheduleAlertsEnabled)
        assertEquals(customProfile.dailyReminderTime, restored.dailyReminderTime)
        assertEquals(customProfile.hapticFeedbackEnabled, restored.hapticFeedbackEnabled)
        assertEquals(customProfile.hapticIntensity, restored.hapticIntensity)
        assertEquals(PeriodStatus.PRESENT, repo.recordsMap.value["2026-10-05"]?.get(1))
        assertTrue(repo.isDateHoliday("2026-10-01"))

        // Malformed backup must be rejected without corrupting current state
        val badJson = """{"profile":{"examTarget":999},"records":{"bad-date":{"1":"P"}}}"""
        assertFalse(repo.importBackupJson(badJson))
        assertEquals("Bhawishya", repo.profileState.value.name)
    }

    @Test
    fun testHistoricalBackfillValidationAndPreservation() {
        val fixedClock = LocalDate.of(2026, 10, 5)
        val repo = AttendanceRepository(context) { fixedClock }

        // Manually mark Period 1 as ABSENT on 2026-10-05
        assertTrue(repo.markPeriodStatus("2026-10-05", 1, PeriodStatus.ABSENT))

        // Reversed range must fail
        assertFalse(repo.bulkBackfillPastWorkingDays("2026-10-05", "2026-10-01", markAllPresent = true))
        // Future range must fail
        assertFalse(repo.bulkBackfillPastWorkingDays("2026-10-05", "2026-10-06", markAllPresent = true))

        // Valid backfill fills PENDING periods with PRESENT but preserves existing manual ABSENT on Period 1
        assertTrue(repo.bulkBackfillPastWorkingDays("2026-10-05", "2026-10-05", markAllPresent = true))
        val oct5 = repo.getPeriodsForDate("2026-10-05")
        assertNotNull(oct5)
        assertEquals(PeriodStatus.ABSENT, oct5[1])
        assertEquals(PeriodStatus.PRESENT, oct5[2])
    }

    @Test
    fun testAttendanceStatsCalculator() {
        val stats = com.example.ui.components.AttendanceStatsCalculator.fromStatuses(
            listOf(PeriodStatus.PRESENT, PeriodStatus.AUTO_PRESENT, PeriodStatus.ABSENT, PeriodStatus.NO_CLASS, PeriodStatus.PENDING)
        )
        assertEquals(2, stats.attended)
        assertEquals(1, stats.missed)
        assertEquals(3, stats.conducted)
        assertEquals(1, stats.cancelled)
    }

    @Test
    fun testAttendanceStatsCalculatorMonthlyHeroMatchesMonthlyAnalytics() {
        val fixedClock = LocalDate.of(2026, 10, 5)
        val repo = AttendanceRepository(context) { fixedClock }
        val analytics = repo.getMonthlyAnalytics("2026-10")
        val heroStats = repo.getHeroSessionStats("2026-10")

        assertEquals(analytics.totalAttendedCredits, heroStats.attended)
        assertEquals(analytics.totalConductedCredits, heroStats.conducted)
    }
}
