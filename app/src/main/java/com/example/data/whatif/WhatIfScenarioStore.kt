package com.example.data.whatif

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved scenario snapshots. Own SharedPreferences file, separate from real attendance data, so
 * snapshots can never alter or be mistaken for attendance records. Unreadable or newer-schema
 * entries are preserved untouched, never silently deleted.
 */
class WhatIfScenarioStore(private val prefs: SharedPreferences) {

    companion object {
        const val PREFS_NAME = "bhtt_whatif_prefs"
        const val KEY = "whatif_snapshots_v1"
        private const val KEY_CORRUPT_BACKUP = "whatif_snapshots_v1_corrupt_backup"
    }

    data class LoadResult(val snapshots: List<WhatIfSnapshot>, val unreadableCount: Int, val newerVersionCount: Int)
    enum class SaveOutcome { SAVED, DUPLICATE_ID, FAILED }

    private fun readRaw(): MutableList<JSONObject> {
        val text = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONObject(text).getJSONArray("items")
            val out = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(it) }
            out
        }.getOrElse {
            // Root is damaged: keep the raw text so nothing is lost, then start clean.
            prefs.edit().putString(KEY_CORRUPT_BACKUP, text).apply()
            mutableListOf()
        }
    }

    private fun writeRaw(items: List<JSONObject>): Boolean = runCatching {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(KEY, JSONObject().put("v", WhatIfSnapshot.CURRENT_SCHEMA).put("items", arr).toString()).apply()
        true
    }.getOrDefault(false)

    fun load(): LoadResult {
        var unreadable = 0
        var newer = 0
        val ok = mutableListOf<WhatIfSnapshot>()
        for (raw in readRaw()) {
            val parsed = runCatching { WhatIfJson.fromJson(raw) }
            when {
                parsed.isFailure -> unreadable++
                parsed.getOrNull() == null -> newer++
                else -> parsed.getOrNull()?.let { ok.add(it) }
            }
        }
        return LoadResult(ok.sortedByDescending { it.savedAtMillis }, unreadable, newer)
    }

    fun uniqueName(base: String, existing: List<WhatIfSnapshot>): String {
        val clean = base.trim().ifBlank { "Scenario" }.take(48)
        val names = existing.map { it.name.lowercase() }.toSet()
        if (clean.lowercase() !in names) return clean
        var n = 2
        while ("$clean ($n)".lowercase() in names) n++
        return "$clean ($n)"
    }

    /** Idempotent per snapshot id: a double tap on Save cannot create two entries. */
    fun saveNew(snapshot: WhatIfSnapshot): SaveOutcome {
        val raw = readRaw()
        if (raw.any { it.optString("id") == snapshot.id }) return SaveOutcome.DUPLICATE_ID
        raw.add(WhatIfJson.toJson(snapshot))
        return if (writeRaw(raw)) SaveOutcome.SAVED else SaveOutcome.FAILED
    }

    /** Renaming changes only the label; the saved inputs and results are untouched. */
    fun rename(id: String, newName: String): Boolean {
        val raw = readRaw()
        val idx = raw.indexOfFirst { it.optString("id") == id }
        if (idx < 0) return false
        val snap = runCatching { WhatIfJson.fromJson(raw[idx]) }.getOrNull() ?: return false
        val others = load().snapshots.filter { it.id != id }
        raw[idx] = WhatIfJson.toJson(snap.copy(name = uniqueName(newName, others)))
        return writeRaw(raw)
    }

    fun duplicate(id: String, newId: String): WhatIfSnapshot? {
        val all = load().snapshots
        val src = all.firstOrNull { it.id == id } ?: return null
        val copy = src.copy(id = newId, name = uniqueName(src.name + " copy", all), derivedFromId = src.id)
        return if (saveNew(copy) == SaveOutcome.SAVED) copy else null
    }

    fun delete(id: String): Boolean {
        val raw = readRaw()
        val kept = raw.filterNot { it.optString("id") == id }
        if (kept.size == raw.size) return false
        return writeRaw(kept)
    }
}
