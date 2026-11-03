package com.example.whatif

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.whatif.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatIfStoreAndComparisonTest {

    private lateinit var store: WhatIfScenarioStore
    private lateinit var prefs: android.content.SharedPreferences

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        prefs = ctx.getSharedPreferences(WhatIfScenarioStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = WhatIfScenarioStore(prefs)
    }

    private fun planDay(date: LocalDate, d: PlanDecision) =
        Fx.manual(date).map { PlannedClass(date.toString(), it.periodNumber, it.subject.code, d) }

    private fun snap(
        id: String, name: String, plan: WhatIfPlan, mode: ProjectionMode = ProjectionMode.PLAN_ONLY,
        records: Map<String, Map<Int, com.example.data.model.PeriodStatus>> = Fx.baseRecords(),
        profile: com.example.data.model.StudentProfile = com.example.data.model.StudentProfile()
    ): WhatIfSnapshot {
        val ctx = Fx.ctx(records = records, profile = profile)
        val base = WhatIfCalculator.buildBaseline(ctx)
        val r = WhatIfCalculator.calculate(ctx, plan, mode, base)
        val rec = WhatIfCalculator.buildRecovery(ctx, plan, mode, r)
        return WhatIfCalculator.createSnapshot(
            id, name, 1_700_000_000_000L, ctx, plan, mode, WhatIfSnapshot.STYLE_WHOLE_DAY, r, rec, WhatIfCalculator.bestCase(ctx, base)
        )
    }

    // ── JSON / store ──

    @Test fun snapshotJsonRoundTripIsLossless() {
        val s = snap("a", "Round trip", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)), ProjectionMode.MONTH_END)
        val back = WhatIfJson.fromJson(JSONObject(WhatIfJson.toJson(s).toString()))
        assertEquals(s, back)
    }

    @Test fun saveIsIdempotentPerId() {
        val s = snap("same", "X", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)))
        assertEquals(WhatIfScenarioStore.SaveOutcome.SAVED, store.saveNew(s))
        assertEquals(WhatIfScenarioStore.SaveOutcome.DUPLICATE_ID, store.saveNew(s))
        assertEquals(1, store.load().snapshots.size)
    }

    @Test fun openingSavedSnapshotReturnsStoredNumbersEvenAfterDataChanges() {
        val original = snap("a", "Frozen", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)))
        store.saveNew(original)
        // Data changes afterwards (a new past record appears); the stored snapshot must not move.
        val changed = Fx.baseRecords() + ("2026-11-04" to mapOf(1 to com.example.data.model.PeriodStatus.ABSENT))
        val recalculated = snap("b", "Later", original.plan, records = changed)
        assertNotEquals(original.result.baseline.conducted, recalculated.result.baseline.conducted)
        val loaded = store.load().snapshots.first { it.id == "a" }
        assertEquals(original.result, loaded.result)
        assertEquals(original.result.baseline, loaded.result.baseline)
        assertEquals(original.plan, loaded.plan)
    }

    @Test fun renamePreservesInputsAndResults() {
        val s = snap("a", "Old", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)))
        store.saveNew(s)
        assertTrue(store.rename("a", "New name"))
        val loaded = store.load().snapshots.single()
        assertEquals("New name", loaded.name)
        assertEquals(s.result, loaded.result)
        assertEquals(s.savedAtMillis, loaded.savedAtMillis)
    }

    @Test fun duplicateKeepsOriginalAndUsesUniqueName() {
        val s = snap("a", "Plan", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)))
        store.saveNew(s)
        val copy = store.duplicate("a", "b")!!
        assertEquals(2, store.load().snapshots.size)
        assertEquals("a", copy.derivedFromId)
        assertEquals(s.result, copy.result)
        assertNotEquals(s.name, copy.name)
    }

    @Test fun uniqueNameAddsSuffixForDuplicates() {
        val s = snap("a", "Plan", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)))
        assertEquals("Plan (2)", store.uniqueName("Plan", listOf(s)))
        assertEquals("Other", store.uniqueName("Other", listOf(s)))
    }

    @Test fun deleteRemovesOnlyThatSnapshot() {
        store.saveNew(snap("a", "A", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))))
        store.saveNew(snap("b", "B", WhatIfPlan(planDay(Fx.FRI, PlanDecision.ABSENT))))
        assertTrue(store.delete("a"))
        assertEquals(listOf("b"), store.load().snapshots.map { it.id })
    }

    @Test fun unreadableEntriesAreCountedPreservedAndNotDeleted() {
        store.saveNew(snap("a", "A", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))))
        val root = JSONObject(prefs.getString(WhatIfScenarioStore.KEY, null)!!)
        root.getJSONArray("items").put(JSONObject().put("id", "broken").put("schema", 1))
        prefs.edit().putString(WhatIfScenarioStore.KEY, root.toString()).commit()
        val r = store.load()
        assertEquals(1, r.snapshots.size)
        assertEquals(1, r.unreadableCount)
        store.saveNew(snap("c", "C", WhatIfPlan(planDay(Fx.FRI, PlanDecision.ABSENT))))
        assertTrue(prefs.getString(WhatIfScenarioStore.KEY, "")!!.contains("broken")) // still there
    }

    @Test fun newerSchemaEntriesAreHiddenButKept() {
        store.saveNew(snap("a", "A", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))))
        val root = JSONObject(prefs.getString(WhatIfScenarioStore.KEY, null)!!)
        root.getJSONArray("items").put(JSONObject().put("id", "future").put("schema", 99))
        prefs.edit().putString(WhatIfScenarioStore.KEY, root.toString()).commit()
        val r = store.load()
        assertEquals(1, r.newerVersionCount)
        assertEquals(1, r.snapshots.size)
        store.delete("a")
        assertTrue(prefs.getString(WhatIfScenarioStore.KEY, "")!!.contains("future"))
    }

    @Test fun corruptRootIsBackedUpAndDoesNotCrash() {
        prefs.edit().putString(WhatIfScenarioStore.KEY, "{not json").commit()
        assertEquals(0, store.load().snapshots.size)
        store.saveNew(snap("a", "A", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))))
        assertEquals(1, store.load().snapshots.size)
        assertEquals("{not json", prefs.getString("whatif_snapshots_v1_corrupt_backup", null))
    }

    // ── comparison ──

    @Test fun sameBaselineAndAssumptionsIsControlledAndLeaderIsExact() {
        val a = snap("a", "More absent", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT) + planDay(Fx.FRI, PlanDecision.ABSENT)))
        val b = snap("b", "One absent", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT) + planDay(Fx.FRI, PlanDecision.PRESENT)))
        val rep = WhatIfComparison.compare(listOf(a, b))
        assertTrue(rep.isControlled)
        assertEquals(1, rep.leaderIndex)
        assertTrue(rep.explanation.contains("One absent"))
    }

    @Test fun differentBaselinesAreFlaggedAndNoLeaderIsDeclared() {
        val a = snap("a", "A", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)))
        val more = Fx.baseRecords() + ("2026-11-04" to mapOf(1 to com.example.data.model.PeriodStatus.ABSENT))
        val b = snap("b", "B", WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT)), records = more)
        val rep = WhatIfComparison.compare(listOf(a, b))
        assertFalse(rep.isControlled)
        assertNull(rep.leaderIndex)
        assertTrue(rep.differences.any { it.title.contains("baseline", ignoreCase = true) })
    }

    @Test fun differentTargetsAndHorizonsAreFlagged() {
        val plan = WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))
        val a = snap("a", "A", plan)
        val b = snap("b", "B", plan, mode = ProjectionMode.MONTH_END, profile = com.example.data.model.StudentProfile(examTarget = 80))
        val rep = WhatIfComparison.compare(listOf(a, b))
        assertTrue(rep.differences.any { it.title.contains("targets", ignoreCase = true) })
        assertTrue(rep.differences.any { it.title.contains("horizon", ignoreCase = true) })
        assertNull(rep.leaderIndex)
    }

    @Test fun identicalScenariosTieWithoutALeader() {
        val plan = WhatIfPlan(planDay(Fx.THU, PlanDecision.ABSENT))
        val rep = WhatIfComparison.compare(listOf(snap("a", "A", plan), snap("b", "B", plan)))
        assertTrue(rep.isControlled)
        assertNull(rep.leaderIndex)
    }
}
