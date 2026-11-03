package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.holidays.AktuHolidays
import com.example.data.math.AttendanceMath
import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.HapticIntensity
import com.example.data.model.MonthlyAnalytics
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.model.SubjectSummary
import com.example.data.model.ThemeMode
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.data.timetable.TimetableMatrix
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AttendanceRepository(
    private val context: Context,
    private val clockProvider: () -> LocalDate = { LocalDate.now(IST_ZONE) }
) {

    companion object {
        val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
        private val VALID_STATUS_CODES = setOf(
            "P", "PRESENT",
            "A", "ABSENT",
            "N", "NO_CLASS", "CANCELLED",
            "AUTO_P", "AUTO_PRESENT",
            "U", "PENDING"
        )
    }

    private val prefs = context.getSharedPreferences("bhtt_attendance_prefs", Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    private val _profileState = MutableStateFlow(loadLocalProfile())
    val profileState: StateFlow<StudentProfile> = _profileState.asStateFlow()

    // Map of Date String ("YYYY-MM-DD") to Map of Period Number to PeriodStatus
    private val _recordsMap = MutableStateFlow<Map<String, Map<Int, PeriodStatus>>>(emptyMap())
    val recordsMap: StateFlow<Map<String, Map<Int, PeriodStatus>>> = _recordsMap.asStateFlow()

    // Map of Date String to whether that date is manually or officially marked as a Holiday
    private val _holidayOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val holidayOverrides: StateFlow<Map<String, Boolean>> = _holidayOverrides.asStateFlow()

    private val ioScope = CoroutineScope(Dispatchers.IO)

    init {
        performNonDestructiveMigrationIfNeeded()
        loadLocalRecords()
    }

    fun liveToday(): LocalDate = clockProvider()

    private fun parseValidIsoDate(dateStr: String): LocalDate? {
        if (!ISO_DATE_REGEX.matches(dateStr)) return null
        return runCatching { LocalDate.parse(dateStr) }.getOrNull()
    }

    /**
     * Centralized date-eligibility check for attendance mutations.
     * Rejects malformed dates and blocks future dates when `futureMarkingEnabled` is disabled.
     */
    fun canModifyDate(dateStr: String): Boolean {
        val parsed = parseValidIsoDate(dateStr) ?: return false
        if (_profileState.value.futureMarkingEnabled) return true
        return !parsed.isAfter(liveToday())
    }

    /**
     * Centralized date-eligibility check for attendance calculations.
     * Future dates NEVER count toward actual conducted/attended attendance totals,
     * even when `futureMarkingEnabled` is enabled.
     */
    fun isDateEligibleForCalculation(dateStr: String): Boolean {
        val parsed = parseValidIsoDate(dateStr) ?: return false
        return !parsed.isAfter(liveToday())
    }

    /**
     * Strict status code validation so unknown/corrupted codes are logged and rejected
     * where appropriate instead of silently masking errors.
     */
    fun isKnownStatusCode(code: String?): Boolean {
        if (code == null) return false
        return code.trim().uppercase() in VALID_STATUS_CODES
    }

    /**
     * Bug #26: Non-destructive migration that backs up legacy JSON records before V2 Pending status rules.
     */
    private fun performNonDestructiveMigrationIfNeeded() {
        val schemaVersion = prefs.getInt("schema_version", 1)
        if (schemaVersion < 2) {
            val existingRecords = prefs.getString("saved_records_json", null)
            val existingHolidays = prefs.getString("saved_holidays_json", null)
            prefs.edit().apply {
                if (existingRecords != null && !prefs.contains("backup_v1_saved_records_json")) {
                    putString("backup_v1_saved_records_json", existingRecords)
                }
                if (existingHolidays != null && !prefs.contains("backup_v1_saved_holidays_json")) {
                    putString("backup_v1_saved_holidays_json", existingHolidays)
                }
                putInt("schema_version", 2)
                apply()
            }
        }
    }

    // --- Profile Management ---

    private fun loadLocalProfile(): StudentProfile {
        var name = prefs.getString("prof_name", "") ?: ""
        var dob = prefs.getString("prof_dob", "") ?: ""
        if (com.example.DEV_BYPASS_ONBOARDING && name.isBlank()) {
            name = "Student"
            dob = "2004-05-15"
        }
        val batchStr = prefs.getString("prof_batch", Batch.BATCH_A.name) ?: Batch.BATCH_A.name
        val courseStr = prefs.getString("prof_course", CourseSelection.MAJOR_HONOURS.name) ?: CourseSelection.MAJOR_HONOURS.name
        val examTarget = prefs.getInt("prof_exam_target", 75)
        val stipendTarget = prefs.getInt("prof_stipend_target", 80)
        val safetyBuffer = prefs.getInt("prof_buffer", 4)
        val futureMarking = prefs.getBoolean("prof_future_marking", false)
        val isDarkMode = prefs.getBoolean("prof_dark_mode", true)
        val starfield = prefs.getBoolean("prof_starfield", true)
        val progressiveBlur = prefs.getBoolean("prof_blur", true)
        val isOnboardingCompleted = prefs.getBoolean("prof_onboarding_done", false)
        val themeMode = prefs.getString("prof_theme_mode", null)
            ?: (if (isDarkMode) ThemeMode.DARK.name else ThemeMode.LIGHT.name)

        return StudentProfile(
            userId = prefs.getString("prof_user_id", "") ?: "",
            themeMode = themeMode,
            rollNumber = prefs.getString("prof_roll", "") ?: "",
            academicSession = prefs.getString("prof_session", "") ?: "",
            photoPath = prefs.getString("prof_photo_path", "") ?: "",
            classScheduleAlertsEnabled = prefs.getBoolean("prof_class_alerts", false),
            dailyReminderTime = prefs.getString("prof_daily_time", "16:45") ?: "16:45",
            hapticFeedbackEnabled = prefs.getBoolean("prof_haptic_on", true),
            hapticIntensity = prefs.getString("prof_haptic_level", "MEDIUM") ?: "MEDIUM",
            dailyReminderEnabled = prefs.getBoolean("prof_daily_rem", true),
            weeklyReminderEnabled = prefs.getBoolean("prof_weekly_rem", true),
            mainObjective = prefs.getString("prof_objective", "Maintain my attendance target") ?: "Maintain my attendance target",
            name = name,
            dateOfBirth = dob,
            batch = batchStr,
            courseSelection = courseStr,
            examTarget = examTarget,
            stipendTarget = stipendTarget,
            safetyBuffer = safetyBuffer,
            futureMarkingEnabled = futureMarking,
            isDarkMode = isDarkMode,
            starfieldAnimationEnabled = starfield,
            progressiveBlurEnabled = progressiveBlur,
            isOnboardingCompleted = isOnboardingCompleted
        )
    }

    fun resetAppData() {
        prefs.edit().clear().apply()
        _recordsMap.value = emptyMap()
        _holidayOverrides.value = emptyMap()
        _profileState.value = StudentProfile(
            name = "",
            dateOfBirth = "",
            batch = Batch.BATCH_A.name,
            courseSelection = CourseSelection.MAJOR_HONOURS.name,
            isOnboardingCompleted = false
        )
    }

    fun updateProfile(requested: StudentProfile) {
        val old = _profileState.value
        val profile = if (requested.isDarkMode != old.isDarkMode && requested.themeMode == old.themeMode)
            requested.copy(themeMode = if (requested.isDarkMode) ThemeMode.DARK.name else ThemeMode.LIGHT.name)
        else requested
        _profileState.value = profile
        prefs.edit().apply {
            putString("prof_user_id", profile.userId)
            putString("prof_theme_mode", profile.themeMode.ifBlank { if (profile.isDarkMode) "DARK" else "LIGHT" })
            putString("prof_roll", profile.rollNumber)
            putString("prof_session", profile.academicSession)
            putString("prof_photo_path", profile.photoPath)
            putBoolean("prof_class_alerts", profile.classScheduleAlertsEnabled)
            putString("prof_daily_time", profile.dailyReminderTime)
            putBoolean("prof_haptic_on", profile.hapticFeedbackEnabled)
            putString("prof_haptic_level", profile.hapticIntensity)
            putBoolean("prof_daily_rem", profile.dailyReminderEnabled)
            putBoolean("prof_weekly_rem", profile.weeklyReminderEnabled)
            putString("prof_objective", profile.mainObjective)
            putString("prof_name", profile.name)
            putString("prof_dob", profile.dateOfBirth)
            putString("prof_batch", profile.batch)
            putString("prof_course", profile.courseSelection)
            putInt("prof_exam_target", profile.examTarget)
            putInt("prof_stipend_target", profile.stipendTarget)
            putInt("prof_buffer", profile.safetyBuffer)
            putBoolean("prof_future_marking", profile.futureMarkingEnabled)
            putBoolean("prof_dark_mode", profile.isDarkMode)
            putBoolean("prof_starfield", profile.starfieldAnimationEnabled)
            putBoolean("prof_blur", profile.progressiveBlurEnabled)
            putBoolean("prof_onboarding_done", profile.isOnboardingCompleted)
            apply()
        }

        syncProfileToCloud(profile)
        rescheduleReminders()
    }

    /** Re-arms daily / weekly / class alarms from the current profile. Safe to call repeatedly. */
    fun rescheduleReminders() {
        ioScope.launch {
            runCatching {
                com.example.notifications.ReminderScheduler.rescheduleAll(context, _profileState.value) { isDateHoliday(it) }
            }.onFailure { Log.w("AttendanceRepo", "Reminder scheduling failed: ${it.message}") }
        }
    }

    // --- Records & Attendance Operations ---

    private fun loadLocalRecords() {
        val recordsJsonStr = prefs.getString("saved_records_json", null)
        val holidaysJsonStr = prefs.getString("saved_holidays_json", null)

        if (!recordsJsonStr.isNullOrEmpty()) {
            runCatching {
                val rootObj = JSONObject(recordsJsonStr)
                val map = mutableMapOf<String, Map<Int, PeriodStatus>>()
                val keys = rootObj.keys()
                while (keys.hasNext()) {
                    val date = keys.next()
                    if (parseValidIsoDate(date) == null) {
                        Log.w("AttendanceRepo", "Skipping malformed stored date key: $date")
                        continue
                    }
                    val dayObj = rootObj.optJSONObject(date) ?: continue
                    val periodMap = mutableMapOf<Int, PeriodStatus>()
                    val pKeys = dayObj.keys()
                    while (pKeys.hasNext()) {
                        val pNumStr = pKeys.next()
                        val pNum = pNumStr.toIntOrNull()
                        if (pNum == null || pNum !in 1..12) {
                            Log.w("AttendanceRepo", "Skipping invalid period number '$pNumStr' on $date")
                            continue
                        }
                        val statusCode = dayObj.optString(pNumStr, "U")
                        if (!isKnownStatusCode(statusCode)) {
                            Log.w("AttendanceRepo", "Unknown status code '$statusCode' on $date period $pNum; defaulting to PENDING")
                        }
                        periodMap[pNum] = PeriodStatus.fromCode(statusCode)
                    }
                    map[date] = periodMap
                }
                _recordsMap.value = map
            }
        }

        if (!holidaysJsonStr.isNullOrEmpty()) {
            runCatching {
                val obj = JSONObject(holidaysJsonStr)
                val map = mutableMapOf<String, Boolean>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val date = keys.next()
                    if (parseValidIsoDate(date) == null) continue
                    map[date] = obj.getBoolean(date)
                }
                _holidayOverrides.value = map
            }
        }
    }

    private fun saveLocalRecords() {
        val rootObj = JSONObject()
        _recordsMap.value.forEach { (date, periods) ->
            val dayObj = JSONObject()
            periods.forEach { (pNum, status) ->
                dayObj.put(pNum.toString(), status.code)
            }
            rootObj.put(date, dayObj)
        }

        val holObj = JSONObject()
        _holidayOverrides.value.forEach { (date, isHol) ->
            holObj.put(date, isHol)
        }

        prefs.edit().apply {
            putString("saved_records_json", rootObj.toString())
            putString("saved_holidays_json", holObj.toString())
            apply()
        }
    }

    fun isDateHoliday(date: String): Boolean {
        return _holidayOverrides.value[date] ?: AktuHolidays.isHoliday(date)
    }

    /**
     * Fixes Bugs #19 & #20: Toggling a holiday ONLY updates the holiday flag.
     * Enforces future-date restriction when `futureMarkingEnabled` is disabled.
     */
    fun setHoliday(date: String, isHoliday: Boolean): Boolean {
        if (!canModifyDate(date)) return false
        val updatedHolidays = _holidayOverrides.value.toMutableMap()
        updatedHolidays[date] = isHoliday
        _holidayOverrides.value = updatedHolidays

        saveLocalRecords()
        syncDateRecordToCloud(date)
        return true
    }

    /**
     * Fixes Bugs #5, #13, #14, #18 + Central Future-Date Restriction + Timetable Period Validation:
     * - Rejects future dates when `futureMarkingEnabled` is false.
     * - Validates that `periodNumber` is scheduled on `date`.
     * - Preserves all existing periods on that date (including legacy/extra periods).
     * - Protects Auto-Present slots (Library, Digital Library, Seminar) from being marked Absent or Pending.
     */
    fun markPeriodStatus(date: String, periodNumber: Int, status: PeriodStatus): Boolean {
        if (!canModifyDate(date) || isDateHoliday(date)) return false

        val dayOfWeek = getDayOfWeek(date)
        if (dayOfWeek !in 1..5) return false

        val slots = TimetableMatrix.getScheduleForDay(
            dayOfWeek = dayOfWeek,
            batch = _profileState.value.batchEnum,
            courseSelection = _profileState.value.courseSelectionEnum
        )
        val targetSlot = slots.find { it.periodNumber == periodNumber } ?: return false

        // Bug #5 & #18: Auto-present activities cannot be manually marked Absent or Pending
        val safeStatus = if (targetSlot.subject.isAutoPresent) {
            if (status == PeriodStatus.NO_CLASS) PeriodStatus.NO_CLASS else PeriodStatus.AUTO_PRESENT
        } else {
            status
        }

        val updatedRecords = _recordsMap.value.toMutableMap()
        val currentPeriods = (resolveDefaultPeriodsForDate(date) + (updatedRecords[date] ?: emptyMap())).toMutableMap()
        currentPeriods[periodNumber] = safeStatus
        updatedRecords[date] = currentPeriods
        _recordsMap.value = updatedRecords

        saveLocalRecords()
        syncDateRecordToCloud(date)
        return true
    }

    fun markAllPresent(date: String, slots: List<PeriodSlot>): Boolean {
        if (!canModifyDate(date) || isDateHoliday(date)) return false
        val dayOfWeek = getDayOfWeek(date)
        if (dayOfWeek !in 1..5) return false

        val scheduledNumbers = TimetableMatrix.getScheduleForDay(
            dayOfWeek = dayOfWeek,
            batch = _profileState.value.batchEnum,
            courseSelection = _profileState.value.courseSelectionEnum
        ).map { it.periodNumber }.toSet()

        val validSlots = slots.filter { it.periodNumber in scheduledNumbers }
        if (validSlots.isEmpty()) return false

        val updatedRecords = _recordsMap.value.toMutableMap()
        // Bug #14: Preserve any existing non-template period entries
        val mergedMap = (updatedRecords[date] ?: emptyMap()).toMutableMap()
        validSlots.forEach { slot ->
            mergedMap[slot.periodNumber] = if (slot.subject.isAutoPresent) {
                PeriodStatus.AUTO_PRESENT
            } else {
                PeriodStatus.PRESENT
            }
        }
        updatedRecords[date] = mergedMap
        _recordsMap.value = updatedRecords
        saveLocalRecords()
        syncDateRecordToCloud(date)
        return true
    }

    fun markAllAbsent(date: String, slots: List<PeriodSlot>): Boolean {
        if (!canModifyDate(date) || isDateHoliday(date)) return false
        val dayOfWeek = getDayOfWeek(date)
        if (dayOfWeek !in 1..5) return false

        val scheduledNumbers = TimetableMatrix.getScheduleForDay(
            dayOfWeek = dayOfWeek,
            batch = _profileState.value.batchEnum,
            courseSelection = _profileState.value.courseSelectionEnum
        ).map { it.periodNumber }.toSet()

        val validSlots = slots.filter { it.periodNumber in scheduledNumbers }
        if (validSlots.isEmpty()) return false

        val updatedRecords = _recordsMap.value.toMutableMap()
        val mergedMap = (updatedRecords[date] ?: emptyMap()).toMutableMap()
        validSlots.forEach { slot ->
            // Bug #5 & #17: Auto-Present Immunity Rule
            mergedMap[slot.periodNumber] = if (slot.subject.isAutoPresent) {
                PeriodStatus.AUTO_PRESENT
            } else {
                PeriodStatus.ABSENT
            }
        }
        updatedRecords[date] = mergedMap
        _recordsMap.value = updatedRecords
        saveLocalRecords()
        syncDateRecordToCloud(date)
        return true
    }

    fun markAllCancelled(date: String, slots: List<PeriodSlot>): Boolean {
        if (!canModifyDate(date) || isDateHoliday(date)) return false
        val dayOfWeek = getDayOfWeek(date)
        if (dayOfWeek !in 1..5) return false

        val scheduledNumbers = TimetableMatrix.getScheduleForDay(
            dayOfWeek = dayOfWeek,
            batch = _profileState.value.batchEnum,
            courseSelection = _profileState.value.courseSelectionEnum
        ).map { it.periodNumber }.toSet()

        val validSlots = slots.filter { it.periodNumber in scheduledNumbers }
        if (validSlots.isEmpty()) return false

        val updatedRecords = _recordsMap.value.toMutableMap()
        val mergedMap = (updatedRecords[date] ?: emptyMap()).toMutableMap()
        validSlots.forEach { slot ->
            mergedMap[slot.periodNumber] = PeriodStatus.NO_CLASS
        }
        updatedRecords[date] = mergedMap
        _recordsMap.value = updatedRecords
        saveLocalRecords()
        syncDateRecordToCloud(date)
        return true
    }

    /**
     * Fixes Bugs #1 & #2:
     * Ordinary classes start as `PeriodStatus.PENDING` (never `PRESENT`).
     * Auto-present activities start as `PeriodStatus.AUTO_PRESENT` on or before today,
     * and remain `PeriodStatus.PENDING` (Upcoming) on future dates until their scheduled date arrives.
     */
    fun resolveDefaultPeriodsForDate(date: String): Map<Int, PeriodStatus> {
        if (isDateHoliday(date)) {
            return (1..7).associateWith { PeriodStatus.NO_CLASS }
        }
        val dayOfWeek = getDayOfWeek(date)
        if (dayOfWeek !in 1..5) return emptyMap()

        val isFuture = parseValidIsoDate(date)?.isAfter(liveToday()) == true

        val slots = TimetableMatrix.getScheduleForDay(
            dayOfWeek = dayOfWeek,
            batch = _profileState.value.batchEnum,
            courseSelection = _profileState.value.courseSelectionEnum
        )

        return slots.associate { slot ->
            val status = when {
                slot.subject.isAutoPresent && !isFuture -> PeriodStatus.AUTO_PRESENT
                slot.subject.isAutoPresent && isFuture && _profileState.value.futureMarkingEnabled -> PeriodStatus.AUTO_PRESENT
                else -> PeriodStatus.PENDING
            }
            slot.periodNumber to status
        }
    }

    /**
     * Fixes Bugs #13, #14, #19, #20:
     * - If date is a holiday, returns `NO_CLASS` for display without deleting underlying saved records.
     * - If date is in the future and `futureMarkingEnabled` is false, returns `PENDING` (Upcoming) for all slots.
     * - Otherwise merges saved records on top of the current schedule defaults.
     */
    fun getPeriodsForDate(date: String): Map<Int, PeriodStatus> {
        if (isDateHoliday(date)) {
            return (1..7).associateWith { PeriodStatus.NO_CLASS }
        }
        val parsed = parseValidIsoDate(date)
        val isFuture = parsed != null && parsed.isAfter(liveToday())
        if (isFuture && !_profileState.value.futureMarkingEnabled) {
            val dayOfWeek = getDayOfWeek(date)
            if (dayOfWeek !in 1..5) return emptyMap()
            val slots = TimetableMatrix.getScheduleForDay(
                dayOfWeek = dayOfWeek,
                batch = _profileState.value.batchEnum,
                courseSelection = _profileState.value.courseSelectionEnum
            )
            return slots.associate { it.periodNumber to PeriodStatus.PENDING }
        }

        val defaults = resolveDefaultPeriodsForDate(date)
        val saved = _recordsMap.value[date] ?: return defaults

        val merged = defaults.toMutableMap()
        saved.forEach { (periodNumber, savedStatus) ->
            val defaultStatus = defaults[periodNumber]
            merged[periodNumber] = if (defaultStatus == PeriodStatus.AUTO_PRESENT && savedStatus != PeriodStatus.NO_CLASS) {
                PeriodStatus.AUTO_PRESENT
            } else {
                savedStatus
            }
        }
        return merged
    }

    // --- Bulk Backfill Tool ---

    /**
     * Fixes Bugs #13 & #16 + Historical Backfill Validation:
     * - Validates start/end date format and rejects reversed ranges or future dates.
     * - Requires `confirmed = true` before modifying historical records.
     * - Never overwrites already-marked (`PRESENT`, `ABSENT`, `NO_CLASS`) periods unless `overwriteExisting = true`.
     */
    fun bulkBackfillPastWorkingDays(
        fromDate: String,
        toDate: String,
        markAllPresent: Boolean,
        confirmed: Boolean = true,
        overwriteExisting: Boolean = false
    ): Boolean {
        if (!confirmed) return false
        val startLocal = parseValidIsoDate(fromDate) ?: return false
        val endLocal = parseValidIsoDate(toDate) ?: return false
        val today = liveToday()

        // Reject reversed ranges or ranges extending into the future
        if (startLocal.isAfter(endLocal) || endLocal.isAfter(today)) {
            return false
        }

        val updatedRecords = _recordsMap.value.toMutableMap()
        val modifiedDates = mutableListOf<String>()

        var cursor = startLocal
        while (!cursor.isAfter(endLocal)) {
            val dateStr = cursor.toString()
            val dayOfWeek = getDayOfWeek(dateStr)

            if (dayOfWeek in 1..5 && !isDateHoliday(dateStr)) {
                val slots = TimetableMatrix.getScheduleForDay(
                    dayOfWeek = dayOfWeek,
                    batch = _profileState.value.batchEnum,
                    courseSelection = _profileState.value.courseSelectionEnum
                )
                val existingDayMap = (updatedRecords[dateStr] ?: emptyMap()).toMutableMap()
                var dayChanged = false

                slots.forEach { slot ->
                    val currentStatus = existingDayMap[slot.periodNumber]
                    val isUnmarked = currentStatus == null || currentStatus == PeriodStatus.PENDING
                    if (isUnmarked || overwriteExisting) {
                        val newStatus = if (slot.subject.isAutoPresent) {
                            if (currentStatus == PeriodStatus.NO_CLASS && !overwriteExisting) {
                                PeriodStatus.NO_CLASS
                            } else {
                                PeriodStatus.AUTO_PRESENT
                            }
                        } else {
                            if (markAllPresent) PeriodStatus.PRESENT else PeriodStatus.ABSENT
                        }
                        if (existingDayMap[slot.periodNumber] != newStatus) {
                            existingDayMap[slot.periodNumber] = newStatus
                            dayChanged = true
                        }
                    }
                }
                if (dayChanged) {
                    updatedRecords[dateStr] = existingDayMap
                    modifiedDates.add(dateStr)
                }
            }
            cursor = cursor.plusDays(1)
        }

        if (modifiedDates.isNotEmpty()) {
            _recordsMap.value = updatedRecords
            saveLocalRecords()
            modifiedDates.forEach { syncDateRecordToCloud(it) }
        }
        return true
    }

    // --- Analytics & Mathematical Aggregation ---

    /**
     * Resolves the effective calculation status for a scheduled slot on an eligible date (`date <= today`).
     * - Explicitly cancelled (`NO_CLASS`) slots are excluded.
     * - Manually marked `PRESENT` or `ABSENT` slots are respected.
     * - Configured `isAutoPresent` slots automatically count as `AUTO_PRESENT` on eligible working days
     *   even when no manual record exists in `_recordsMap` or when stored as `PENDING`.
     */
    private fun resolveCalculationStatusForEligibleDate(
        slot: PeriodSlot,
        savedStatus: PeriodStatus?
    ): PeriodStatus {
        return when (savedStatus) {
            PeriodStatus.NO_CLASS -> PeriodStatus.NO_CLASS
            PeriodStatus.PRESENT -> PeriodStatus.PRESENT
            PeriodStatus.ABSENT -> if (slot.subject.isAutoPresent) PeriodStatus.AUTO_PRESENT else PeriodStatus.ABSENT
            PeriodStatus.AUTO_PRESENT -> PeriodStatus.AUTO_PRESENT
            PeriodStatus.PENDING, null -> {
                if (slot.subject.isAutoPresent) PeriodStatus.AUTO_PRESENT else PeriodStatus.PENDING
            }
        }
    }

    /**
     * Returns all eligible working dates (`<= liveToday()`, Monday..Friday, non-holiday) in `monthId` ("YYYY-MM").
     * Avoids creating duplicate records in `_recordsMap` while guaranteeing past/today auto-present sessions are counted,
     * and strictly excluding future dates even when `futureMarkingEnabled` is enabled.
     */
    private fun getEligibleWorkingDatesInMonth(monthId: String): List<String> {
        val ym = runCatching { YearMonth.parse(monthId) }.getOrNull() ?: return emptyList()
        val today = liveToday()
        val firstDay = ym.atDay(1)
        if (firstDay.isAfter(today)) return emptyList()

        // If the month has any recorded activity or is the current/active semester month,
        // only count auto-present sessions from the earliest active semester date in that month (or day 1 if records exist).
        val lastEligibleDay = if (ym.atEndOfMonth().isAfter(today)) today else ym.atEndOfMonth()

        val result = mutableListOf<String>()
        var cursor = firstDay
        while (!cursor.isAfter(lastEligibleDay)) {
            val dateStr = cursor.toString()
            val dow = cursor.dayOfWeek.value
            if (dow in 1..5 && !isDateHoliday(dateStr)) {
                result.add(dateStr)
            }
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * Fixes Bugs #2, #3, #4, #6, #21, #27 + Auto-Present & Future-Cutoff Rules:
     * - Future dates (`> liveToday()`) NEVER count toward attendance, even if `futureMarkingEnabled` is true.
     * - Eligible past/today auto-present sessions count without requiring a stored record in `_recordsMap`.
     * - `PENDING` and `NO_CLASS` contribute 0 to both `attended` and `conducted`.
     * - `slot.subject.creditHours` is applied once per slot (no double lab multiplication).
     */
    private data class CachedAnalysisKey(
        val monthId: String,
        val recordsHash: Int,
        val holidaysHash: Int,
        val profile: StudentProfile,
        val today: LocalDate
    )
    private var cachedAnalysis: Pair<CachedAnalysisKey, com.example.data.reports.ReportAnalytics>? = null

    private fun getOrComputeAnalytics(monthId: String, today: LocalDate, profile: StudentProfile): com.example.data.reports.ReportAnalytics {
        val range = ReportDateRange.ofMonth(monthId) ?: ReportDateRange(today.plusDays(1), today.plusDays(1))
        val key = CachedAnalysisKey(monthId, _recordsMap.value.hashCode(), _holidayOverrides.value.hashCode(), profile, today)
        cachedAnalysis?.let { (cachedKey, result) ->
            if (cachedKey == key) return result
        }
        val result = ReportAnalyticsEngine.analyze(range, _recordsMap.value, this::isDateHoliday, profile, today)
        cachedAnalysis = key to result
        return result
    }

    fun getMonthlyAnalytics(monthId: String): MonthlyAnalytics {
        val today = liveToday()
        val profile = _profileState.value
        val a = getOrComputeAnalytics(monthId, today, profile)

        return MonthlyAnalytics(
            monthId = monthId,
            monthName = formatMonthLabel(monthId),
            totalAttendedCredits = a.attendedCredits,
            totalConductedCredits = a.conductedCredits,
            percentage = a.percentage,
            examTarget = profile.examTarget,
            stipendTarget = profile.stipendTarget,
            safetyBuffer = profile.safetyBuffer,
            isExamEligible = a.isExamEligible,
            isStipendEligible = a.isStipendEligible,
            tierStatus = a.tierStatus,
            recoveryHoursNeeded75 = a.recoveryHoursExam,
            recoveryHoursNeeded80 = a.recoveryHoursStipend,
            safeSkipHoursAvailable = a.safeSkipHoursStipend,
            autoPresentBoostHours = a.autoPresentBoostHours,
            // A month is an archive once it has ended (was hardcoded to monthId < "2026-10").
            isSavedArchive = ReportDateRange.ofMonth(monthId)?.end?.isBefore(today.withDayOfMonth(1)) == true
        )
    }

    /** Combines lectures and tutorials by subject code; same rules as every other Reports number. */
    fun getSubjectSummaries(monthId: String): List<SubjectSummary> {
        val today = liveToday()
        return getOrComputeAnalytics(monthId, today, _profileState.value)
            .subjects
            .map { r ->
                SubjectSummary(
                    subject = r.subject,
                    attendedCredits = r.attendedCredits,
                    conductedCredits = r.conductedCredits,
                    percentage = r.percentage,
                    recoveryHoursNeeded = r.recoveryHoursStipend,
                    safeSkipHoursAvailable = r.safeSkipHoursStipend,
                    tierStatus = r.tierStatus
                )
            }
    }

    /**
     * Home hero numbers now come from the same engine as Reports, so Home and Reports can never
     * disagree: manual holiday overrides honoured, future dates never counted, auto-present
     * credited without a record, Pending / No Class excluded.
     */
    fun getHeroSessionStats(monthId: String): com.example.ui.components.AttendanceSessionStats {
        val today = liveToday()
        val a = getOrComputeAnalytics(monthId, today, _profileState.value)
        return com.example.ui.components.AttendanceSessionStats(
            attended = a.attendedCredits,
            missed = a.missedCredits,
            conducted = a.conductedCredits,
            cancelled = a.cancelledCredits
        )
    }

    // --- JSON Backup Export / Import (All 23 Persistent StudentProfile Fields) ---

    private fun serializeProfileToJson(prof: StudentProfile): JSONObject {
        return JSONObject().apply {
            put("userId", prof.userId)
            put("name", prof.name)
            put("dateOfBirth", prof.dateOfBirth)
            put("batch", prof.batch)
            put("courseSelection", prof.courseSelection)
            put("examTarget", prof.examTarget)
            put("stipendTarget", prof.stipendTarget)
            put("safetyBuffer", prof.safetyBuffer)
            put("futureMarkingEnabled", prof.futureMarkingEnabled)
            put("isDarkMode", prof.isDarkMode)
            put("starfieldAnimationEnabled", prof.starfieldAnimationEnabled)
            put("progressiveBlurEnabled", prof.progressiveBlurEnabled)
            put("dailyReminderEnabled", prof.dailyReminderEnabled)
            put("weeklyReminderEnabled", prof.weeklyReminderEnabled)
            put("mainObjective", prof.mainObjective)
            put("isOnboardingCompleted", prof.isOnboardingCompleted)
            put("themeMode", prof.themeMode)
            put("rollNumber", prof.rollNumber)
            put("academicSession", prof.academicSession)
            put("photoPath", prof.photoPath)
            put("classScheduleAlertsEnabled", prof.classScheduleAlertsEnabled)
            put("dailyReminderTime", prof.dailyReminderTime)
            put("hapticFeedbackEnabled", prof.hapticFeedbackEnabled)
            put("hapticIntensity", prof.hapticIntensity)
        }
    }

    private fun deserializeProfileFromJson(p: JSONObject, fallback: StudentProfile): StudentProfile {
        val restoredDarkMode = if (p.has("isDarkMode")) p.optBoolean("isDarkMode", fallback.isDarkMode) else fallback.isDarkMode
        val restoredThemeMode = if (p.has("themeMode")) {
            p.optString("themeMode", fallback.themeMode)
        } else if (p.has("isDarkMode")) {
            if (restoredDarkMode) ThemeMode.DARK.name else ThemeMode.LIGHT.name
        } else {
            fallback.themeMode
        }

        return fallback.copy(
            userId = p.optString("userId", fallback.userId),
            name = p.optString("name", fallback.name),
            dateOfBirth = p.optString("dateOfBirth", fallback.dateOfBirth),
            batch = p.optString("batch", fallback.batch),
            courseSelection = p.optString("courseSelection", fallback.courseSelection),
            examTarget = p.optInt("examTarget", fallback.examTarget),
            stipendTarget = p.optInt("stipendTarget", fallback.stipendTarget),
            safetyBuffer = p.optInt("safetyBuffer", fallback.safetyBuffer),
            futureMarkingEnabled = if (p.has("futureMarkingEnabled")) p.optBoolean("futureMarkingEnabled", fallback.futureMarkingEnabled) else fallback.futureMarkingEnabled,
            isDarkMode = restoredDarkMode,
            starfieldAnimationEnabled = if (p.has("starfieldAnimationEnabled")) p.optBoolean("starfieldAnimationEnabled", fallback.starfieldAnimationEnabled) else fallback.starfieldAnimationEnabled,
            progressiveBlurEnabled = if (p.has("progressiveBlurEnabled")) p.optBoolean("progressiveBlurEnabled", fallback.progressiveBlurEnabled) else fallback.progressiveBlurEnabled,
            dailyReminderEnabled = if (p.has("dailyReminderEnabled")) p.optBoolean("dailyReminderEnabled", fallback.dailyReminderEnabled) else fallback.dailyReminderEnabled,
            weeklyReminderEnabled = if (p.has("weeklyReminderEnabled")) p.optBoolean("weeklyReminderEnabled", fallback.weeklyReminderEnabled) else fallback.weeklyReminderEnabled,
            mainObjective = p.optString("mainObjective", fallback.mainObjective),
            isOnboardingCompleted = if (p.has("isOnboardingCompleted")) p.optBoolean("isOnboardingCompleted", fallback.isOnboardingCompleted) else fallback.isOnboardingCompleted,
            themeMode = restoredThemeMode,
            rollNumber = p.optString("rollNumber", fallback.rollNumber),
            academicSession = p.optString("academicSession", fallback.academicSession),
            photoPath = p.optString("photoPath", fallback.photoPath),
            classScheduleAlertsEnabled = if (p.has("classScheduleAlertsEnabled")) p.optBoolean("classScheduleAlertsEnabled", fallback.classScheduleAlertsEnabled) else fallback.classScheduleAlertsEnabled,
            dailyReminderTime = p.optString("dailyReminderTime", fallback.dailyReminderTime),
            hapticFeedbackEnabled = if (p.has("hapticFeedbackEnabled")) p.optBoolean("hapticFeedbackEnabled", fallback.hapticFeedbackEnabled) else fallback.hapticFeedbackEnabled,
            hapticIntensity = p.optString("hapticIntensity", fallback.hapticIntensity)
        )
    }

    fun exportBackupJson(): String {
        val root = JSONObject()
        val prof = _profileState.value
        root.put("version", 2)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
        root.put("profile", serializeProfileToJson(prof))

        val holObj = JSONObject()
        _holidayOverrides.value.forEach { (d, h) -> holObj.put(d, h) }
        root.put("holidays", holObj)

        val recObj = JSONObject()
        _recordsMap.value.forEach { (date, pMap) ->
            val dayObj = JSONObject()
            pMap.forEach { (pNum, status) ->
                dayObj.put(pNum.toString(), status.code)
            }
            recObj.put(date, dayObj)
        }
        root.put("records", recObj)
        return root.toString(2)
    }

    /**
     * Atomic, validated backup import.
     * Validates the entire JSON structure before mutating profile, records, or holidays.
     * When `replaceExisting = true`, replaces all records/holidays; when `false`, merges into existing data.
     */
    fun importBackupJson(jsonString: String, replaceExisting: Boolean = true): Boolean {
        val preview = parseBackup(jsonString).getOrNull() ?: return false
        applyBackup(preview, replaceExisting = replaceExisting)
        return true
    }

    // --- Validated restore (Settings → Restore Data) ---

    /** Result of validating a backup file. Nothing is changed until [applyBackup] is called. */
    class BackupPreview internal constructor(
        val profileName: String,
        val recordDays: Int,
        val periodEntries: Int,
        val exportedAt: String?,
        internal val records: Map<String, Map<Int, PeriodStatus>>,
        internal val holidays: Map<String, Boolean>,
        internal val profile: JSONObject?
    )

    /** Strictly validates [jsonString]. Returns a failure with a readable message if anything is malformed. */
    fun parseBackup(jsonString: String): Result<BackupPreview> = runCatching {
        val root = JSONObject(jsonString)
        if (!root.has("records") && !root.has("profile") && !root.has("holidays")) {
            error("This file is not an attendance backup.")
        }

        val records = mutableMapOf<String, Map<Int, PeriodStatus>>()
        var entries = 0
        if (root.has("records")) {
            val rec = root.optJSONObject("records") ?: error("\"records\" must be an object.")
            rec.keys().forEach { date ->
                if (parseValidIsoDate(date) == null) error("Invalid date in backup: $date")
                val day = rec.optJSONObject(date) ?: error("Invalid entry for $date")
                val periods = mutableMapOf<Int, PeriodStatus>()
                day.keys().forEach { k ->
                    val n = k.toIntOrNull()
                    if (n == null || n !in 1..12) error("Invalid period \"$k\" on $date")
                    val code = day.optString(k)
                    if (!isKnownStatusCode(code)) error("Invalid status \"$code\" on $date")
                    periods[n] = PeriodStatus.fromCode(code)
                    entries++
                }
                records[date] = periods
            }
        }

        val holidays = mutableMapOf<String, Boolean>()
        if (root.has("holidays")) {
            val h = root.optJSONObject("holidays") ?: error("\"holidays\" must be an object.")
            h.keys().forEach { d ->
                if (parseValidIsoDate(d) == null) error("Invalid holiday date: $d")
                val v = h.opt(d)
                if (v !is Boolean) error("Invalid holiday value for $d")
                holidays[d] = v
            }
        }

        val prof = if (root.has("profile")) {
            root.optJSONObject("profile") ?: error("\"profile\" must be an object.")
        } else null

        prof?.let {
            fun checkRange(key: String, lo: Int, hi: Int) {
                if (it.has(key) && it.optInt(key, -1) !in lo..hi) error("$key must be between $lo and $hi.")
            }
            checkRange("examTarget", 50, 100)
            checkRange("stipendTarget", 50, 100)
            checkRange("safetyBuffer", 0, 25)
            if (it.has("batch") && runCatching { Batch.valueOf(it.getString("batch")) }.isFailure) error("Unknown batch.")
            if (it.has("courseSelection") && runCatching { CourseSelection.valueOf(it.getString("courseSelection")) }.isFailure) error("Unknown course.")
            if (it.has("themeMode")) {
                val tm = it.optString("themeMode", "")
                if (tm.isNotEmpty() && runCatching { ThemeMode.valueOf(tm) }.isFailure) error("Unknown themeMode.")
            }
            if (it.has("hapticIntensity")) {
                val hi = it.optString("hapticIntensity", "")
                if (hi.isNotEmpty() && runCatching { HapticIntensity.valueOf(hi) }.isFailure) error("Unknown hapticIntensity.")
            }
        }

        BackupPreview(
            profileName = prof?.optString("name").orEmpty(),
            recordDays = records.size,
            periodEntries = entries,
            exportedAt = root.optString("exportedAt").ifBlank { null },
            records = records,
            holidays = holidays,
            profile = prof
        )
    }

    /**
     * Applies a validated backup atomically.
     * - When `replaceExisting = false` (default merge mode): dates in the backup overwrite matching local dates,
     *   while local-only dates are preserved.
     * - When `replaceExisting = true`: replaces the entire records and holidays maps with the backup's contents.
     * Stores the previous state under `"pre_restore_snapshot"`.
     */
    fun applyBackup(preview: BackupPreview, replaceExisting: Boolean = false): Int {
        prefs.edit().putString("pre_restore_snapshot", exportBackupJson()).apply()

        if (replaceExisting) {
            _recordsMap.value = preview.records.toMap()
            _holidayOverrides.value = preview.holidays.toMap()
        } else {
            val merged = _recordsMap.value.toMutableMap()
            preview.records.forEach { (d, p) -> merged[d] = p }
            _recordsMap.value = merged
            if (preview.holidays.isNotEmpty()) {
                _holidayOverrides.value = _holidayOverrides.value + preview.holidays
            }
        }
        saveLocalRecords()

        preview.profile?.let { p ->
            val baseProfile = if (replaceExisting) StudentProfile() else _profileState.value
            val restored = deserializeProfileFromJson(p, baseProfile)
            updateProfile(restored)
        }

        preview.records.keys.forEach { syncDateRecordToCloud(it) }
        return preview.records.size
    }

    // --- Explicit cloud backup (Settings → Backup Now) ---

    fun lastCloudBackupMs(): Long = prefs.getLong("last_cloud_backup_ms", 0L)

    /** Writes the profile and every attendance day to Firestore and waits for the server. Returns days written. */
    suspend fun backupToCloud(): Result<Int> = runCatching {
        val user = Firebase.auth.currentUser ?: error("Sign in with Google first.")
        withContext(Dispatchers.IO) {
            val profile = _profileState.value
            firestore.collection("users").document(user.uid)
                .set(cloudProfileMap(profile, user.uid), SetOptions.merge()).await()

            val allDates = (_recordsMap.value.keys + getEligibleWorkingDatesInMonth(liveToday().toString().substring(0, 7))).distinct()
            allDates.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { d ->
                    val ref = firestore.collection("users").document(user.uid).collection("records").document(d)
                    batch.set(ref, buildCloudRecord(d, user.uid), SetOptions.merge())
                }
                batch.commit().await()
            }
            prefs.edit().putLong("last_cloud_backup_ms", System.currentTimeMillis()).apply()
            allDates.size
        }
    }

    internal fun cloudProfileMap(profile: StudentProfile, uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to profile.name,
        "dateOfBirth" to profile.dateOfBirth,
        "batch" to profile.batch,
        "courseSelection" to profile.courseSelection,
        "examTarget" to profile.examTarget,
        "stipendTarget" to profile.stipendTarget,
        "safetyBuffer" to profile.safetyBuffer,
        "futureMarkingEnabled" to profile.futureMarkingEnabled,
        "isDarkMode" to profile.isDarkMode,
        "starfieldAnimationEnabled" to profile.starfieldAnimationEnabled,
        "progressiveBlurEnabled" to profile.progressiveBlurEnabled,
        "dailyReminderEnabled" to profile.dailyReminderEnabled,
        "weeklyReminderEnabled" to profile.weeklyReminderEnabled,
        "mainObjective" to profile.mainObjective,
        "isOnboardingCompleted" to profile.isOnboardingCompleted,
        "themeMode" to profile.themeModeEnum.name,
        "rollNumber" to profile.rollNumber,
        "academicSession" to profile.academicSession,
        "classScheduleAlertsEnabled" to profile.classScheduleAlertsEnabled,
        "dailyReminderTime" to profile.dailyReminderTime,
        "hapticFeedbackEnabled" to profile.hapticFeedbackEnabled,
        "hapticIntensity" to profile.hapticIntensity
    )

    /**
     * Builds a cloud `AttendanceRecord` using the exact same date-eligibility and auto-present rules
     * as local calculations:
     * - Future dates (`> liveToday()`) always have `attendedCredits = 0` and `conductedCredits = 0`.
     * - Eligible working dates (`<= liveToday()`) credit configured auto-present sessions unless marked `NO_CLASS`.
     */
    internal fun buildCloudRecord(date: String, uid: String): AttendanceRecord {
        val periods = _recordsMap.value[date] ?: emptyMap()
        val periodsJson = JSONObject().apply {
            periods.forEach { (pNum, status) -> put(pNum.toString(), status.code) }
        }.toString()

        val dayOfWeek = getDayOfWeek(date)
        val slots = if (dayOfWeek in 1..5) {
            TimetableMatrix.getScheduleForDay(
                dayOfWeek,
                _profileState.value.batchEnum,
                _profileState.value.courseSelectionEnum
            )
        } else {
            emptyList()
        }

        var att = 0
        var cond = 0
        if (!isDateHoliday(date) && isDateEligibleForCalculation(date)) {
            slots.forEach { slot ->
                val st = resolveCalculationStatusForEligibleDate(slot, periods[slot.periodNumber])
                val w = slot.subject.creditHours
                when (st) {
                    PeriodStatus.PRESENT, PeriodStatus.AUTO_PRESENT -> {
                        att += w
                        cond += w
                    }
                    PeriodStatus.ABSENT -> {
                        cond += w
                    }
                    PeriodStatus.NO_CLASS, PeriodStatus.PENDING -> {
                        // Do not count towards attended/conducted
                    }
                }
            }
        }
        return AttendanceRecord(
            date = date,
            userId = uid,
            monthId = if (date.length >= 7) date.substring(0, 7) else date,
            isHoliday = isDateHoliday(date),
            holidayReason = AktuHolidays.getHoliday(date)?.name ?: "",
            periodsJson = periodsJson,
            attendedCredits = att,
            conductedCredits = cond
        )
    }

    // --- Firebase Firestore Sync ---

    private fun syncProfileToCloud(profile: StudentProfile) {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return
        ioScope.launch {
            try {
                val data = cloudProfileMap(profile, user.uid)
                firestore.collection("users").document(user.uid).set(data, SetOptions.merge())
            } catch (e: Exception) {
                Log.w("AttendanceRepo", "Cloud profile sync skipped/failed: ${e.message}")
            }
        }
    }

    /**
     * Fixes Bug #25: Cloud sync uses `buildCloudRecord` so local and cloud calculations always match.
     */
    private fun syncDateRecordToCloud(date: String) {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return
        ioScope.launch {
            try {
                val record = buildCloudRecord(date, user.uid)
                firestore.collection("users").document(user.uid)
                    .collection("records").document(date)
                    .set(record, SetOptions.merge())
            } catch (e: Exception) {
                Log.w("AttendanceRepo", "Cloud record sync skipped: ${e.message}")
            }
        }
    }

    private fun getDayOfWeek(dateStr: String): Int {
        val parsed = parseValidIsoDate(dateStr) ?: return 0
        return when (parsed.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> 1
            java.time.DayOfWeek.TUESDAY -> 2
            java.time.DayOfWeek.WEDNESDAY -> 3
            java.time.DayOfWeek.THURSDAY -> 4
            java.time.DayOfWeek.FRIDAY -> 5
            else -> 0
        }
    }

    private fun formatMonthLabel(monthId: String): String =
        ReportAnalyticsEngine.monthLabel(monthId)
}
