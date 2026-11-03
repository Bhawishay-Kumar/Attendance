package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.milestone.MilestoneDates
import com.example.data.milestone.MilestonePresets
import com.example.data.milestone.OfficialSchedules
import com.example.data.milestone.ResolvedMilestone
import com.example.data.model.Milestone
import com.example.data.model.MilestoneAccent
import com.example.data.model.MilestoneCategory
import com.example.data.model.MilestoneDateSource
import com.example.data.model.MilestoneLimits
import com.example.data.model.MilestoneRecurrence
import com.example.data.repository.MilestoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.UUID

data class MilestoneUiState(
    val loading: Boolean = true,
    val items: List<ResolvedMilestone> = emptyList(),
    val today: LocalDate = LocalDate.now()
)

/** What the user typed in the Add/Edit form. */
data class MilestoneDraft(
    val editingId: String? = null,
    /** Generated when the form opens. Saving the same draft twice (double tap) can only ever create one milestone. */
    val clientId: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val category: MilestoneCategory,
    val targetDate: String,
    val accent: MilestoneAccent,
    val recurrence: MilestoneRecurrence,
    val reminderDays: List<Int>,
    val presetKey: String? = null
)

sealed interface MilestoneSheetRequest {
    data object Add : MilestoneSheetRequest
    data class Edit(val id: String) : MilestoneSheetRequest
}

class MilestoneViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MilestoneRepository(application)

    private val today = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<MilestoneUiState> = combine(repo.visible, today, repo.loaded) { list, day, loaded ->
        val resolved = list.mapNotNull { MilestoneDates.resolve(it, day) } // entries with invalid dates are skipped
        MilestoneUiState(loading = !loaded, items = MilestoneDates.sortForDisplay(resolved), today = day)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MilestoneUiState())

    private val _sheet = MutableStateFlow<MilestoneSheetRequest?>(null)
    val sheet: StateFlow<MilestoneSheetRequest?> = _sheet.asStateFlow()

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess.asStateFlow()

    private val _pendingDelete = MutableStateFlow<String?>(null)
    val pendingDelete: StateFlow<String?> = _pendingDelete.asStateFlow()

    /** Milestone the carousel should scroll to (set after the success popup is confirmed). */
    private val _focusId = MutableStateFlow<String?>(null)
    val focusId: StateFlow<String?> = _focusId.asStateFlow()
    private var pendingFocus: String? = null

    /** Milestone that was just created: its card plays the pop-in + confetti celebration. */
    private val _celebrateId = MutableStateFlow<String?>(null)
    val celebrateId: StateFlow<String?> = _celebrateId.asStateFlow()
    private val savedClientIds = HashSet<String>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        repo.start()
    }

    override fun onCleared() {
        repo.close()
        super.onCleared()
    }

    // ---- ui requests
    fun openAdd() { _sheet.value = MilestoneSheetRequest.Add }
    fun openEdit(id: String) { _sheet.value = MilestoneSheetRequest.Edit(id) }
    fun closeSheet() { _sheet.value = null }
    fun requestDelete(id: String) { _pendingDelete.value = id }
    fun cancelDelete() { _pendingDelete.value = null }
    fun consumeFocus() { _focusId.value = null }
    fun clearCelebration(id: String) { if (_celebrateId.value == id) _celebrateId.value = null }
    fun refreshToday() { today.value = LocalDate.now() }

    fun dismissSuccess() {
        _showSuccess.value = false
        _focusId.value = pendingFocus
        _celebrateId.value = pendingFocus
        pendingFocus = null
    }

    fun find(id: String): Milestone? = uiState.value.items.firstOrNull { it.id == id }?.milestone

    fun hasActive(presetKey: String): Boolean =
        uiState.value.items.any { it.milestone.presetKey == presetKey }

    // ---- actions
    fun onProfileDobChanged(dob: String) = repo.syncBirthdayWithProfile(dob)

    fun confirmDelete() {
        val id = _pendingDelete.value ?: return
        _pendingDelete.value = null
        savedClientIds.remove(id) // lets a deleted preset (e.g. birthday) be added again
        repo.delete(id)
        _messages.tryEmit("Milestone deleted")
    }

    fun togglePin(id: String) {
        val m = find(id) ?: return
        repo.setPinned(id, !m.pinned)
        _messages.tryEmit(if (m.pinned) "Unpinned" else "Pinned to the front")
    }

    fun toggleCompleted(id: String) {
        val m = find(id) ?: return
        if (m.recurrence != MilestoneRecurrence.NONE) return
        repo.setCompleted(id, !m.completed)
    }

    /** Birthday preset: no form, the date comes from the student profile. Returns false if DOB is unusable. */
    fun addBirthdayFromProfile(dobRaw: String): Boolean {
        val dob = MilestoneDates.parseDateOfBirth(dobRaw) ?: return false
        if (hasActive(MilestonePresets.BIRTHDAY) || BIRTHDAY_CLIENT_ID in savedClientIds) {
            _messages.tryEmit("Birthday is already added")
            return true
        }
        val preset = MilestonePresets.byKey(MilestonePresets.BIRTHDAY)!!
        saveNew(
            MilestoneDraft(
                clientId = BIRTHDAY_CLIENT_ID,
                title = preset.title,
                description = preset.description,
                category = preset.category,
                targetDate = dob.toString(),
                accent = preset.accent,
                recurrence = MilestoneRecurrence.YEARLY,
                reminderDays = listOf(1),
                presetKey = preset.key
            ),
            dateSource = MilestoneDateSource.PROFILE
        )
        return true
    }

    /** Validates and saves the form. Returns an error message, or null on success. */
    fun save(draft: MilestoneDraft): String? {
        val title = draft.title.trim()
        if (title.isEmpty()) return "Please enter a title"
        if (title.length > MilestoneLimits.MAX_TITLE) return "Title is too long"
        val date = MilestoneDates.parse(draft.targetDate) ?: return "Please choose a valid date"
        if (draft.description.length > MilestoneLimits.MAX_DESCRIPTION) return "Description is too long"
        val clean = draft.copy(title = title, description = draft.description.trim(), targetDate = date.toString())

        if (clean.editingId == null && clean.clientId in savedClientIds) return null // already saved (double tap)
        val editing = clean.editingId?.let { find(it) }
        if (clean.editingId != null && editing == null) return "This milestone no longer exists"
        if (editing == null && uiState.value.items.size >= MilestoneLimits.MAX_MILESTONES) {
            return "You can keep up to ${MilestoneLimits.MAX_MILESTONES} milestones"
        }

        val official = OfficialSchedules.isOfficial(clean.presetKey ?: editing?.presetKey, clean.targetDate)
        val source = when {
            official -> MilestoneDateSource.OFFICIAL
            (clean.presetKey ?: editing?.presetKey) == MilestonePresets.BIRTHDAY &&
                editing?.dateSource == MilestoneDateSource.PROFILE && editing.targetDate == clean.targetDate -> MilestoneDateSource.PROFILE
            else -> MilestoneDateSource.MANUAL
        }

        if (editing != null) {
            val dateChanged = editing.targetDate != clean.targetDate
            val updated = editing.copy(
                title = clean.title,
                description = clean.description,
                category = clean.category,
                targetDate = clean.targetDate,
                accent = clean.accent,
                recurrence = clean.recurrence,
                reminderDays = clean.reminderDays.distinct().sortedDescending(),
                dateSource = source,
                sourceLabel = if (official) OfficialSchedules.forPreset(editing.presetKey)?.sourceName else null,
                startDate = if (dateChanged) LocalDate.now().toString() else editing.startDate,
                // Moving an event to a new date reopens it.
                completed = if (dateChanged) false else editing.completed
            )
            repo.upsert(updated)
            _messages.tryEmit("Milestone updated")
            pendingFocus = updated.id
            _focusId.value = updated.id
        } else {
            saveNew(clean, source)
        }
        return null
    }

    private fun saveNew(draft: MilestoneDraft, dateSource: MilestoneDateSource) {
        if (!savedClientIds.add(draft.clientId)) return
        val now = System.currentTimeMillis()
        val official = dateSource == MilestoneDateSource.OFFICIAL
        val m = Milestone(
            id = draft.clientId,
            title = draft.title,
            description = draft.description,
            category = draft.category,
            targetDate = draft.targetDate,
            accent = draft.accent,
            recurrence = draft.recurrence,
            reminderDays = draft.reminderDays.distinct().sortedDescending(),
            createdAt = now,
            startDate = LocalDate.now().toString(),
            presetKey = draft.presetKey,
            dateSource = dateSource,
            sourceLabel = if (official) OfficialSchedules.forPreset(draft.presetKey)?.sourceName else null
        )
        repo.upsert(m)
        pendingFocus = m.id
        _showSuccess.value = true
    }

    private companion object {
        const val BIRTHDAY_CLIENT_ID = "birthday-from-profile"
    }
}
