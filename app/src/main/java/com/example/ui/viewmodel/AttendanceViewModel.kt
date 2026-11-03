package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.bunk.BunkPlannerEngine
import com.example.data.bunk.BunkPlannerResult
import com.example.data.holidays.AktuHolidays
import com.example.data.holidays.Holiday
import com.example.data.model.MonthlyAnalytics
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.model.SubjectCategory
import com.example.data.model.SubjectSummary
import com.example.data.repository.AttendanceRepository
import com.example.data.timetable.TimetableMatrix
import com.example.ui.components.AttendanceToast
import com.example.ui.components.DockTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        fun liveToday(): LocalDate = LocalDate.now(IST_ZONE)
        fun liveTodayStr(): String = liveToday().toString()
    }

    val repository = AttendanceRepository(application)

    /** What-If Predictor state holder (read-only over attendance; own storage for saved scenarios). */
    val whatIf: com.example.ui.whatif.WhatIfController by lazy {
        com.example.ui.whatif.WhatIfController(repository, application)
    }

    val profile: StateFlow<StudentProfile> = repository.profileState

    private val _selectedTab = MutableStateFlow(DockTab.HOME)
    val selectedTab: StateFlow<DockTab> = _selectedTab.asStateFlow()

    // Live "today": refreshed by the ticker below so a long-running session rolls over at midnight
    // (and at month change) instead of staying on the day/month the ViewModel was created on.
    private val _today = MutableStateFlow(liveToday())
    private val todayStr: String get() = _today.value.toString()
    private val currentMonthStr: String get() = todayStr.substring(0, 7)

    private val _selectedMonthId = MutableStateFlow(currentMonthStr)
    val selectedMonthId: StateFlow<String> = _selectedMonthId.asStateFlow()

    private val _selectedDate = MutableStateFlow(todayStr)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _quickActivitiesPage = MutableStateFlow(0)
    val quickActivitiesPage: StateFlow<Int> = _quickActivitiesPage.asStateFlow()


    private val _activeCategoryFilter = MutableStateFlow(SubjectCategory.ALL)
    val activeCategoryFilter: StateFlow<SubjectCategory> = _activeCategoryFilter.asStateFlow()

    private val _showMonthEndSummaryDialog = MutableStateFlow(false)
    val showMonthEndSummaryDialog: StateFlow<Boolean> = _showMonthEndSummaryDialog.asStateFlow()

    private val _showPdfPreviewDialog = MutableStateFlow(false)
    val showPdfPreviewDialog: StateFlow<Boolean> = _showPdfPreviewDialog.asStateFlow()

    private val _pdfMode = MutableStateFlow("SUMMARY")
    val pdfMode: StateFlow<String> = _pdfMode.asStateFlow()

    private val _showMidMonthBackfillModal = MutableStateFlow(false)
    val showMidMonthBackfillModal: StateFlow<Boolean> = _showMidMonthBackfillModal.asStateFlow()

    private val _toastMessage = MutableStateFlow<AttendanceToast?>(null)
    val toastMessage: StateFlow<AttendanceToast?> = _toastMessage.asStateFlow()

    private val _cascadingPeriodNumber = MutableStateFlow<Int?>(null)
    val cascadingPeriodNumber: StateFlow<Int?> = _cascadingPeriodNumber.asStateFlow()

    private val _immunityPulseSlotNumber = MutableStateFlow<Int?>(null)
    val immunityPulseSlotNumber: StateFlow<Int?> = _immunityPulseSlotNumber.asStateFlow()

    private var bulkCascadeJob: Job? = null

    init {
        // Re-arm reminder alarms on every app start (alarms do not survive force-stop / reboot).
        repository.rescheduleReminders()

        // Midnight / month-rollover watcher. Cheap: one comparison every 30 seconds.
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                val now = liveToday()
                val before = _today.value
                if (now != before) {
                    val oldMonth = before.toString().substring(0, 7)
                    val oldDay = before.toString()
                    _today.value = now
                    if (_selectedMonthId.value == oldMonth) _selectedMonthId.value = now.toString().substring(0, 7)
                    if (_selectedDate.value == oldDay) _selectedDate.value = now.toString()
                }
            }
        }
    }

    val monthlyAnalytics: StateFlow<MonthlyAnalytics> = combine(
        _selectedMonthId,
        repository.recordsMap,
        repository.holidayOverrides,
        profile,
        _today
    ) { monthId, _, _, _, _ ->
        repository.getMonthlyAnalytics(monthId)
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        repository.getMonthlyAnalytics(currentMonthStr)
    )

    val heroSessionStats: StateFlow<com.example.ui.components.AttendanceSessionStats> = combine(
        _selectedMonthId,
        repository.recordsMap,
        repository.holidayOverrides,
        profile,
        _today
    ) { monthId, _, _, _, _ ->
        repository.getHeroSessionStats(monthId)
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        repository.getHeroSessionStats(currentMonthStr)
    )

    val subjectSummaries: StateFlow<List<SubjectSummary>> = combine(
        _selectedMonthId,
        repository.recordsMap,
        repository.holidayOverrides,
        profile,
        _today
    ) { monthId, _, _, _, _ ->
        repository.getSubjectSummaries(monthId)
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        repository.getSubjectSummaries(currentMonthStr)
    )

    val currentScheduleSlots: StateFlow<List<PeriodSlot>> = combine(
        _selectedDate,
        profile
    ) { date, prof ->
        val dow = getDayOfWeek(date)
        if (dow in 1..5) {
            TimetableMatrix.getScheduleForDay(dow, prof.batchEnum, prof.courseSelectionEnum)
        } else {
            emptyList()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val currentPeriodStatuses: StateFlow<Map<Int, PeriodStatus>> = combine(
        _selectedDate,
        repository.recordsMap,
        repository.holidayOverrides,
        profile
    ) { date, _, _, _ ->
        repository.getPeriodsForDate(date)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyMap()
    )

    val isCurrentDateHoliday: StateFlow<Boolean> = combine(
        _selectedDate,
        repository.holidayOverrides
    ) { date, _ ->
        repository.isDateHoliday(date)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        false
    )

    val currentHolidayName: StateFlow<String> = combine(
        _selectedDate,
        isCurrentDateHoliday
    ) { date, isHol ->
        if (isHol) AktuHolidays.getHoliday(date)?.name ?: "Holiday / No Classes" else ""
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        ""
    )

    val upcomingHolidays: List<Holiday>
        get() = AktuHolidays.getUpcomingHolidays(liveTodayStr(), 5)

    // --- Navigation & View Actions ---

    fun selectTab(tab: DockTab) {
        _selectedTab.value = tab
    }

    fun selectMonth(monthId: String) {
        val today = liveToday()
        val currentMonth = today.toString().substring(0, 7)
        if (monthId > currentMonth && !profile.value.futureMarkingEnabled) {
            showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
            return
        }
        _selectedMonthId.value = monthId
        if (monthId == currentMonth) {
            _selectedDate.value = today.toString()
        } else {
            _selectedDate.value = "$monthId-01"
        }
    }

    fun selectDate(date: String) {
        val today = liveToday()
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        if (parsed.isAfter(today) && !profile.value.futureMarkingEnabled) {
            // Block future date when Future Marking is OFF
            showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
            return
        }
        _selectedDate.value = date
        if (date.length >= 7) {
            val monthPrefix = date.substring(0, 7)
            if (_selectedMonthId.value != monthPrefix) {
                _selectedMonthId.value = monthPrefix
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectDate(date.toString())
    }

    fun jumpToToday() {
        val today = liveToday()
        _selectedDate.value = today.toString()
        val monthPrefix = today.toString().substring(0, 7)
        if (_selectedMonthId.value != monthPrefix) {
            _selectedMonthId.value = monthPrefix
        }
    }

    fun setQuickActivitiesPage(page: Int) {
        _quickActivitiesPage.value = page
    }

    fun setCategoryFilter(filter: SubjectCategory) {
        _activeCategoryFilter.value = filter
    }

    fun toggleDarkMode() {
        val current = profile.value
        val nightMask = getApplication<Application>().resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val effectiveDark = when (current.themeModeEnum) {
            com.example.data.model.ThemeMode.DARK -> true
            com.example.data.model.ThemeMode.LIGHT -> false
            com.example.data.model.ThemeMode.SYSTEM -> nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        setThemeMode(if (effectiveDark) com.example.data.model.ThemeMode.LIGHT else com.example.data.model.ThemeMode.DARK)
    }

    fun setThemeMode(mode: com.example.data.model.ThemeMode) {
        val current = profile.value
        repository.updateProfile(
            current.copy(
                themeMode = mode.name,
                isDarkMode = when (mode) {
                    com.example.data.model.ThemeMode.DARK -> true
                    com.example.data.model.ThemeMode.LIGHT -> false
                    com.example.data.model.ThemeMode.SYSTEM -> current.isDarkMode
                }
            )
        )
    }

    fun updateProfile(newProfile: StudentProfile) {
        repository.updateProfile(newProfile)
        if (!newProfile.futureMarkingEnabled) {
            val parsed = runCatching { LocalDate.parse(_selectedDate.value) }.getOrNull()
            if (parsed != null && parsed.isAfter(liveToday())) {
                jumpToToday()
            }
        }
    }

    /**
     * Fixes Bug #5 & #18 + Central Future Restriction:
     * - Rejects future marking when `futureMarkingEnabled` is disabled.
     * - Prevents manual status changes from turning Auto-Present subjects into Absent/Pending,
     *   and pulses the immunity indicator if attempted.
     */
    fun markPeriodStatus(periodNumber: Int, status: PeriodStatus): Boolean {
        val targetDate = _selectedDate.value
        if (!repository.canModifyDate(targetDate)) {
            showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
            return false
        }

        val slot = currentScheduleSlots.value.find { it.periodNumber == periodNumber }
        if (slot?.subject?.isAutoPresent == true && status == PeriodStatus.ABSENT) {
            com.example.ui.settings.Haptics.perform(getApplication<Application>(), profile.value, com.example.ui.settings.HapticKind.WARNING)
            viewModelScope.launch {
                _immunityPulseSlotNumber.value = periodNumber
                repository.markPeriodStatus(targetDate, periodNumber, PeriodStatus.AUTO_PRESENT)
                showToast("🛡️ ${slot.subject.shortName} is Auto-Present Protected", Color(0xFF10B981), "🛡️")
                delay(300L)
                _immunityPulseSlotNumber.value = null
            }
            return false
        }

        val modified = repository.markPeriodStatus(targetDate, periodNumber, status)
        if (modified) {
            com.example.ui.settings.Haptics.perform(
                getApplication<Application>(), profile.value,
                when (status) {
                    PeriodStatus.PRESENT, PeriodStatus.AUTO_PRESENT -> com.example.ui.settings.HapticKind.CONFIRM
                    PeriodStatus.ABSENT -> com.example.ui.settings.HapticKind.WARNING
                    else -> com.example.ui.settings.HapticKind.CLICK
                }
            )
        } else {
            com.example.ui.settings.Haptics.perform(getApplication<Application>(), profile.value, com.example.ui.settings.HapticKind.WARNING)
            showToast("🔒 Cannot mark attendance for this date", Color(0xFF64748B), "🔒")
        }
        return modified
    }

    fun toggleFutureMarking(enabled: Boolean) {
        val current = profile.value
        repository.updateProfile(current.copy(futureMarkingEnabled = enabled))
        if (!enabled) {
            val parsed = runCatching { LocalDate.parse(_selectedDate.value) }.getOrNull()
            if (parsed != null && parsed.isAfter(liveToday())) {
                jumpToToday()
            }
        }
        showToast(
            if (enabled) "⚡ Future Marking ON • Pre-mark upcoming days" else "🔒 Future Marking OFF",
            if (enabled) Color(0xFF6366F1) else Color(0xFF64748B),
            if (enabled) "⚡" else "🔒"
        )
    }

    fun showToast(message: String, color: Color, emoji: String = "✨") {
        viewModelScope.launch {
            _toastMessage.value = AttendanceToast(message, color, emoji)
            delay(1800L)
            if (_toastMessage.value?.message == message) {
                _toastMessage.value = null
            }
        }
    }

    fun markAllPresent() {
        triggerBulkPresentCascade()
    }

    fun markAllAbsent() {
        triggerBulkAbsentCascade()
    }

    fun markAllCancelled() {
        triggerBulkCancelledCascade()
    }

    fun triggerBulkPresentCascade() {
        bulkCascadeJob?.cancel()
        bulkCascadeJob = viewModelScope.launch {
            val targetDate = _selectedDate.value
            val slots = currentScheduleSlots.value
            if (slots.isEmpty() || repository.isDateHoliday(targetDate)) return@launch

            if (!repository.markAllPresent(targetDate, slots)) {
                showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
                return@launch
            }
            com.example.ui.settings.Haptics.perform(getApplication<Application>(), profile.value, com.example.ui.settings.HapticKind.SUCCESS)
            var count = 0
            var credits = 0
            slots.forEach { slot ->
                _cascadingPeriodNumber.value = slot.periodNumber
                count++
                credits += slot.subject.creditHours
                delay(45L)
            }
            _cascadingPeriodNumber.value = null
            showToast("✓ All $count periods marked Present (+$credits Cr)", Color(0xFF10B981), "✓")
        }
    }

    fun triggerBulkAbsentCascade() {
        bulkCascadeJob?.cancel()
        bulkCascadeJob = viewModelScope.launch {
            val targetDate = _selectedDate.value
            val slots = currentScheduleSlots.value
            if (slots.isEmpty() || repository.isDateHoliday(targetDate)) return@launch

            if (!repository.markAllAbsent(targetDate, slots)) {
                showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
                return@launch
            }
            com.example.ui.settings.Haptics.perform(getApplication<Application>(), profile.value, com.example.ui.settings.HapticKind.WARNING)
            var autoProtected = 0
            slots.forEach { slot ->
                _cascadingPeriodNumber.value = slot.periodNumber
                if (slot.subject.isAutoPresent) {
                    _immunityPulseSlotNumber.value = slot.periodNumber
                    autoProtected++
                }
                delay(45L)
            }
            _cascadingPeriodNumber.value = null
            delay(200L)
            _immunityPulseSlotNumber.value = null
            showToast("✕ Marked Absent • $autoProtected Auto-Protected", Color(0xFFEF4444), "✕")
        }
    }

    fun triggerBulkCancelledCascade() {
        bulkCascadeJob?.cancel()
        bulkCascadeJob = viewModelScope.launch {
            val targetDate = _selectedDate.value
            val slots = currentScheduleSlots.value
            if (slots.isEmpty() || repository.isDateHoliday(targetDate)) return@launch

            if (!repository.markAllCancelled(targetDate, slots)) {
                showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
                return@launch
            }
            com.example.ui.settings.Haptics.perform(getApplication<Application>(), profile.value, com.example.ui.settings.HapticKind.CONFIRM)
            slots.forEach { slot ->
                _cascadingPeriodNumber.value = slot.periodNumber
                delay(45L)
            }
            _cascadingPeriodNumber.value = null
            showToast("⊘ All periods marked No Class (0 credits)", Color(0xFF64748B), "⊘")
        }
    }

    fun toggleHoliday(isHoliday: Boolean) {
        if (!repository.setHoliday(_selectedDate.value, isHoliday)) {
            showToast("🔒 Future Marking is disabled", Color(0xFF64748B), "🔒")
        }
    }

    fun showMonthEndSummary(show: Boolean) {
        _showMonthEndSummaryDialog.value = show
    }

    // --- Bunk Planner (read-only: nothing here writes to the repository) ---
    private val _bunkPlannerOpen = MutableStateFlow(false)
    val bunkPlannerOpen: StateFlow<Boolean> = _bunkPlannerOpen.asStateFlow()

    /** null while calculating (or when the planner is closed). */
    private val _bunkPlannerResult = MutableStateFlow<BunkPlannerResult?>(null)
    val bunkPlannerResult: StateFlow<BunkPlannerResult?> = _bunkPlannerResult.asStateFlow()

    private var bunkJob: Job? = null

    fun openBunkPlanner() {
        _bunkPlannerOpen.value = true
        recalculateBunkPlanner()
    }

    fun closeBunkPlanner() {
        bunkJob?.cancel()
        _bunkPlannerOpen.value = false
        _bunkPlannerResult.value = null
    }

    fun recalculateBunkPlanner() {
        bunkJob?.cancel()
        _bunkPlannerResult.value = null
        // Snapshot the inputs, then compute off the main thread. The engine only reads these values.
        val profileSnapshot = profile.value
        val records = repository.recordsMap.value
        val today = liveToday()
        bunkJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                BunkPlannerEngine.plan(
                    profile = profileSnapshot,
                    records = records,
                    isHoliday = { repository.isDateHoliday(it) },
                    today = today
                )
            }
            _bunkPlannerResult.value = result
        }
    }

    fun showPdfPreview(show: Boolean, mode: String = "SUMMARY") {
        _pdfMode.value = mode
        _showPdfPreviewDialog.value = show
    }

    fun showMidMonthBackfill(show: Boolean) {
        _showMidMonthBackfillModal.value = show
    }

    fun performBackfill(fromDate: String, toDate: String, markAllPresent: Boolean) {
        val ok = repository.bulkBackfillPastWorkingDays(
            fromDate = fromDate,
            toDate = toDate,
            markAllPresent = markAllPresent,
            confirmed = true,
            overwriteExisting = false
        )
        if (!ok) {
            showToast("⚠️ Invalid date range for backfill", Color(0xFFF59E0B), "⚠️")
        }
        _showMidMonthBackfillModal.value = false
    }

    fun completeOnboarding(profile: StudentProfile) {
        repository.updateProfile(profile.copy(isOnboardingCompleted = true))
    }

    fun replayOnboarding() {
        repository.updateProfile(profile.value.copy(isOnboardingCompleted = false))
    }

    fun resetAppDataAndReplayOnboarding() {
        runCatching { com.example.ui.settings.ProfilePhotoStore.delete(profile.value.photoPath) }
        repository.resetAppData()
        _selectedTab.value = DockTab.HOME
    }

    private fun getDayOfWeek(dateStr: String): Int {
        val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return 0
        return when (parsed.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> 1
            java.time.DayOfWeek.TUESDAY -> 2
            java.time.DayOfWeek.WEDNESDAY -> 3
            java.time.DayOfWeek.THURSDAY -> 4
            java.time.DayOfWeek.FRIDAY -> 5
            else -> 0
        }
    }
}
