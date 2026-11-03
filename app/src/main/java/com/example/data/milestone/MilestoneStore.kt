package com.example.data.milestone

import android.content.Context
import com.example.data.model.Milestone
import com.example.data.model.MilestoneAccent
import com.example.data.model.MilestoneCategory
import com.example.data.model.MilestoneDateSource
import com.example.data.model.MilestoneRecurrence
import org.json.JSONArray
import org.json.JSONObject

const val MILESTONE_LOCAL_SCOPE = "local"

/**
 * Offline storage for milestones, one list per account scope (the Firebase uid, or "local" when
 * signed out). Uses its own preferences file so attendance data and its reset logic are untouched.
 */
class MilestoneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bhtt_milestones_prefs", Context.MODE_PRIVATE)

    /** Scope currently shown in the UI; read by the reminder receiver. */
    var activeScope: String
        get() = prefs.getString(KEY_ACTIVE_SCOPE, MILESTONE_LOCAL_SCOPE) ?: MILESTONE_LOCAL_SCOPE
        set(value) { prefs.edit().putString(KEY_ACTIVE_SCOPE, value).apply() }

    fun load(scope: String): List<Milestone> {
        val raw = prefs.getString(listKey(scope), null)
        if (raw.isNullOrBlank()) return emptyList()
        val decoded = MilestoneCodec.decode(raw)
        if (decoded == null) {
            // Corrupt payload: keep a copy instead of silently overwriting it on the next save.
            prefs.edit().putString(listKey(scope) + "_corrupt_backup", raw).apply()
            return emptyList()
        }
        return decoded
    }

    fun save(scope: String, list: List<Milestone>) {
        prefs.edit().putString(listKey(scope), MilestoneCodec.encode(list)).apply()
    }

    private fun listKey(scope: String) = "milestones_v1_$scope"

    private companion object {
        const val KEY_ACTIVE_SCOPE = "active_scope"
    }
}

internal object MilestoneCodec {

    fun encode(list: List<Milestone>): String {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    /** Returns null when the whole payload is unreadable; individual bad entries are skipped. */
    fun decode(raw: String): List<Milestone>? {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val out = ArrayList<Milestone>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            fromJson(o)?.let { out.add(it) }
        }
        return out
    }

    fun toJson(m: Milestone): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("title", m.title)
        put("description", m.description)
        put("category", m.category.name)
        put("targetDate", m.targetDate)
        put("accent", m.accent.name)
        put("recurrence", m.recurrence.name)
        put("reminderDays", JSONArray(m.reminderDays))
        put("pinned", m.pinned)
        put("pinnedAt", m.pinnedAt)
        put("createdAt", m.createdAt)
        put("startDate", m.startDate)
        put("completed", m.completed)
        put("presetKey", m.presetKey ?: "")
        put("dateSource", m.dateSource.name)
        put("sourceLabel", m.sourceLabel ?: "")
        put("updatedAt", m.updatedAt)
        put("deleted", m.deleted)
        put("dirty", m.dirty)
    }

    fun fromJson(o: JSONObject): Milestone? {
        val id = o.optString("id")
        val title = o.optString("title")
        if (id.isBlank() || title.isBlank()) return null
        val reminders = o.optJSONArray("reminderDays")
        val days = buildList {
            if (reminders != null) for (i in 0 until reminders.length()) add(reminders.optInt(i))
        }.filter { it in MilestoneLimitsAccess.options }.distinct().sortedDescending()
        return Milestone(
            id = id,
            title = title,
            description = o.optString("description"),
            category = MilestoneCategory.fromKey(o.optString("category")),
            targetDate = o.optString("targetDate"),
            accent = MilestoneAccent.fromKey(o.optString("accent")),
            recurrence = MilestoneRecurrence.fromKey(o.optString("recurrence")),
            reminderDays = days,
            pinned = o.optBoolean("pinned"),
            pinnedAt = o.optLong("pinnedAt"),
            createdAt = o.optLong("createdAt"),
            startDate = o.optString("startDate"),
            completed = o.optBoolean("completed"),
            presetKey = o.optString("presetKey").ifBlank { null },
            dateSource = MilestoneDateSource.fromKey(o.optString("dateSource")),
            sourceLabel = o.optString("sourceLabel").ifBlank { null },
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted"),
            dirty = o.optBoolean("dirty")
        )
    }
}

private object MilestoneLimitsAccess {
    val options: Set<Int> = com.example.data.model.MilestoneLimits.REMINDER_OPTIONS.toSet()
}
