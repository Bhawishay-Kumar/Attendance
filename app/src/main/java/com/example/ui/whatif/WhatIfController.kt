package com.example.ui.whatif

import android.content.Context
import com.example.data.model.PeriodSlot
import com.example.data.repository.AttendanceRepository
import com.example.data.whatif.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.util.UUID

enum class WhatIfScreen { INTRO, CALENDAR, CLASSES, RESULTS, BREAKDOWN, SAVED, SNAPSHOT, COMPARE }

data class WhatIfUiState(
    val screen: WhatIfScreen = WhatIfScreen.INTRO,
    val backStack: List<WhatIfScreen> = emptyList(),
    val plan: WhatIfPlan = WhatIfPlan(),
    val undoStack: List<WhatIfPlan> = emptyList(),
    val mode: ProjectionMode = ProjectionMode.PLAN_ONLY,
    val planningStyle: String = WhatIfSnapshot.STYLE_WHOLE_DAY,
    val visibleMonth: YearMonth = YearMonth.now(),
    val focusDate: String? = null,
    val baseline: WhatIfBaseline? = null,
    val result: WhatIfResult? = null,
    val recovery: List<RecoverySuggestion> = emptyList(),
    val bestCase: BestCaseSummary? = null,
    val saved: List<WhatIfSnapshot> = emptyList(),
    val unreadableSaved: Int = 0,
    val newerVersionSaved: Int = 0,
    val openSnapshotId: String? = null,
    /** Non-null only while the user is looking at "recalculate with current data" for the open snapshot. */
    val recalculated: WhatIfSnapshot? = null,
    val saveDraftId: String? = null,
    val compareSelection: List<String> = emptyList(),
    val compareReport: ComparisonReport? = null,
    val message: String? = null
)

/**
 * UI-side holder for the predictor. Read-only with respect to attendance: it only reads repository
 * state and writes snapshots into its own preference file.
 */
class WhatIfController(private val repository: AttendanceRepository, context: Context) {

    companion object {
        const val CURRENT_ID = "current"
        const val MAX_COMPARE = 3
    }

    private val store = WhatIfScenarioStore(context.getSharedPreferences(WhatIfScenarioStore.PREFS_NAME, Context.MODE_PRIVATE))

    private val _open = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _open.asStateFlow()

    private val _ui = MutableStateFlow(WhatIfUiState())
    val ui: StateFlow<WhatIfUiState> = _ui.asStateFlow()

    fun context(): WhatIfContext = WhatIfContext(
        profile = repository.profileState.value,
        records = repository.recordsMap.value,
        isHoliday = { repository.isDateHoliday(it) },
        today = repository.liveToday()
    )

    fun open() {
        val ctx = context()
        val loaded = store.load()
        _ui.value = WhatIfUiState(
            visibleMonth = YearMonth.from(ctx.today),
            baseline = WhatIfCalculator.buildBaseline(ctx),
            saved = loaded.snapshots, unreadableSaved = loaded.unreadableCount, newerVersionSaved = loaded.newerVersionCount
        )
        _open.value = true
    }

    /** Leaves the predictor and discards the unsaved plan (the UI confirms first when a plan exists). */
    fun close() {
        _open.value = false
        _ui.value = WhatIfUiState()
    }

    fun hasUnsavedPlan(): Boolean = _ui.value.plan.items.isNotEmpty()

    // ───────────────────────── navigation ─────────────────────────

    fun go(screen: WhatIfScreen) {
        if (screen == WhatIfScreen.RESULTS || screen == WhatIfScreen.BREAKDOWN || screen == WhatIfScreen.SAVED) recompute()
        _ui.update { it.copy(screen = screen, backStack = it.backStack + it.screen, message = null) }
    }

    /** Returns false when already at the first screen. */
    fun back(): Boolean {
        val s = _ui.value
        if (s.backStack.isEmpty()) return false
        _ui.update {
            val prev = it.backStack.last()
            it.copy(
                screen = prev, backStack = it.backStack.dropLast(1), message = null,
                recalculated = if (prev == WhatIfScreen.SNAPSHOT) it.recalculated else null,
                compareReport = if (prev == WhatIfScreen.COMPARE) it.compareReport else null
            )
        }
        return true
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    // ───────────────────────── planning ─────────────────────────

    private fun pushUndo(s: WhatIfUiState): List<WhatIfPlan> = (s.undoStack + s.plan).takeLast(20)

    fun setVisibleMonth(m: YearMonth) = _ui.update { it.copy(visibleMonth = m) }
    fun setStyle(style: String) = _ui.update { it.copy(planningStyle = style) }

    fun setMode(mode: ProjectionMode) {
        _ui.update { it.copy(mode = mode) }
        recompute()
    }

    fun dayInfo(date: java.time.LocalDate): DayInfo = WhatIfCalculator.dayInfo(context(), date)

    fun openClasses(date: String) {
        _ui.update { it.copy(focusDate = date) }
        go(WhatIfScreen.CLASSES)
    }

    fun applyWholeDay(date: String, decision: PlanDecision?) {
        val info = runCatching { dayInfo(java.time.LocalDate.parse(date)) }.getOrNull() ?: return
        if (info.availability != DayAvailability.PLANNABLE) return
        _ui.update { s ->
            var p = s.plan.withoutDate(date)
            if (decision != null) info.manual.forEach { p = p.with(PlannedClass(date, it.periodNumber, it.subject.code, decision)) }
            s.copy(plan = p, undoStack = pushUndo(s), focusDate = date)
        }
        recompute()
    }

    fun setClass(date: String, slot: PeriodSlot, decision: PlanDecision?) {
        _ui.update { s ->
            val p = if (decision == null) s.plan.without(date, slot.periodNumber)
            else s.plan.with(PlannedClass(date, slot.periodNumber, slot.subject.code, decision))
            s.copy(plan = p, undoStack = pushUndo(s))
        }
        recompute()
    }

    fun clearAll() {
        _ui.update { it.copy(plan = WhatIfPlan(), undoStack = pushUndo(it)) }
        recompute()
    }

    fun undo() {
        _ui.update { s -> if (s.undoStack.isEmpty()) s else s.copy(plan = s.undoStack.last(), undoStack = s.undoStack.dropLast(1)) }
        recompute()
    }

    fun recompute() {
        val ctx = context()
        val s = _ui.value
        val baseline = WhatIfCalculator.buildBaseline(ctx)
        val result = WhatIfCalculator.calculate(ctx, s.plan, s.mode, baseline)
        val recovery = WhatIfCalculator.buildRecovery(ctx, s.plan, s.mode, result)
        val best = WhatIfCalculator.bestCase(ctx, baseline)
        _ui.update { it.copy(baseline = baseline, result = result, recovery = recovery, bestCase = best) }
    }

    // ───────────────────────── snapshots ─────────────────────────

    fun beginSave() = _ui.update { it.copy(saveDraftId = UUID.randomUUID().toString()) }
    fun cancelSave() = _ui.update { it.copy(saveDraftId = null) }

    fun save(name: String) {
        val s = _ui.value
        val id = s.saveDraftId ?: return
        val result = s.result ?: return
        val ctx = context()
        val finalName = store.uniqueName(name.ifBlank { "Scenario" }, s.saved)
        val snap = WhatIfCalculator.createSnapshot(
            id, finalName, System.currentTimeMillis(), ctx, s.plan, s.mode, s.planningStyle, result, s.recovery, s.bestCase
        )
        val outcome = store.saveNew(snap)
        val loaded = store.load()
        _ui.update {
            it.copy(
                saveDraftId = null, saved = loaded.snapshots,
                message = when (outcome) {
                    WhatIfScenarioStore.SaveOutcome.SAVED -> "Saved “$finalName”"
                    WhatIfScenarioStore.SaveOutcome.DUPLICATE_ID -> "Already saved"
                    WhatIfScenarioStore.SaveOutcome.FAILED -> "Couldn’t save this scenario"
                }
            )
        }
    }

    fun openSnapshot(id: String) {
        _ui.update { it.copy(openSnapshotId = id, recalculated = null) }
        go(WhatIfScreen.SNAPSHOT)
    }

    /** Opens the stored values exactly as saved; recalculation is always an explicit, separate action. */
    fun recalculateOpenSnapshot() {
        val s = _ui.value
        val snap = s.saved.firstOrNull { it.id == s.openSnapshotId } ?: return
        val ctx = context()
        val baseline = WhatIfCalculator.buildBaseline(ctx)
        val r = WhatIfCalculator.calculate(ctx, snap.plan, snap.mode, baseline)
        val rec = WhatIfCalculator.buildRecovery(ctx, snap.plan, snap.mode, r)
        val best = WhatIfCalculator.bestCase(ctx, baseline)
        val re = snap.copy(
            id = UUID.randomUUID().toString(), name = snap.name + " (recalculated)",
            savedAtMillis = System.currentTimeMillis(), isRecalculation = true, derivedFromId = snap.id,
            result = r, recovery = rec, bestCase = best
        )
        _ui.update { it.copy(recalculated = re) }
    }

    fun dismissRecalculation() = _ui.update { it.copy(recalculated = null) }

    fun saveRecalculationAsNew() {
        val s = _ui.value
        val re = s.recalculated ?: return
        val named = re.copy(name = store.uniqueName(re.name, s.saved))
        val outcome = store.saveNew(named)
        val loaded = store.load()
        _ui.update {
            it.copy(
                saved = loaded.snapshots, recalculated = null,
                message = if (outcome == WhatIfScenarioStore.SaveOutcome.SAVED) "Saved as a new scenario; the original is unchanged" else "Couldn’t save"
            )
        }
    }

    fun rename(id: String, name: String) {
        store.rename(id, name)
        _ui.update { it.copy(saved = store.load().snapshots) }
    }

    fun duplicate(id: String) {
        val copy = store.duplicate(id, UUID.randomUUID().toString())
        _ui.update { it.copy(saved = store.load().snapshots, message = if (copy != null) "Duplicated" else "Couldn’t duplicate") }
    }

    fun delete(id: String) {
        store.delete(id)
        _ui.update {
            it.copy(
                saved = store.load().snapshots, compareSelection = it.compareSelection - id,
                openSnapshotId = if (it.openSnapshotId == id) null else it.openSnapshotId
            )
        }
    }

    // ───────────────────────── comparison ─────────────────────────

    fun currentAsSnapshot(): WhatIfSnapshot? {
        val s = _ui.value
        val r = s.result ?: return null
        return WhatIfCalculator.createSnapshot(
            CURRENT_ID, "Current plan", System.currentTimeMillis(), context(), s.plan, s.mode, s.planningStyle, r, s.recovery, s.bestCase
        )
    }

    fun toggleCompare(id: String) = _ui.update {
        val sel = if (id in it.compareSelection) it.compareSelection - id
        else if (it.compareSelection.size >= MAX_COMPARE) it.compareSelection else it.compareSelection + id
        it.copy(compareSelection = sel)
    }

    fun setCompareWithCurrent() = _ui.update { it.copy(compareSelection = listOf(CURRENT_ID)) }

    fun startCompare() {
        val s = _ui.value
        val entries = s.compareSelection.mapNotNull { id ->
            if (id == CURRENT_ID) currentAsSnapshot() else s.saved.firstOrNull { it.id == id }
        }
        if (entries.size < 2) return
        _ui.update { it.copy(compareReport = WhatIfComparison.compare(entries)) }
        go(WhatIfScreen.COMPARE)
    }
}
