package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.example.R
import com.example.data.milestone.MILESTONE_LOCAL_SCOPE
import com.example.data.milestone.MilestoneDates
import com.example.data.milestone.MilestonePresets
import com.example.data.milestone.MilestoneReminderScheduler
import com.example.data.milestone.MilestoneStore
import com.example.data.model.Milestone
import com.example.data.model.MilestoneAccent
import com.example.data.model.MilestoneCategory
import com.example.data.model.MilestoneDateSource
import com.example.data.model.MilestoneRecurrence
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Local-first milestone repository.
 *
 *  - Every mutation runs on one single-threaded dispatcher, so state changes are serialised
 *    without locks and the in-memory list is the single source of truth for the UI.
 *  - Data is saved per account scope (Firebase uid, or "local" while signed out). Guest milestones are
 *    adopted into the account when the user signs in.
 *  - Cloud sync (users/{uid}/milestones/{id}) is last-write-wins on [Milestone.updatedAt], with
 *    tombstones for deletes. Document ids equal milestone ids, so repeated syncs never duplicate.
 */
class MilestoneRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = MilestoneStore(appContext)
    private val scheduler = MilestoneReminderScheduler(appContext)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "milestone-repo").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + worker.asCoroutineDispatcher())

    private var scopeKey: String = MILESTONE_LOCAL_SCOPE
    private var all: List<Milestone> = emptyList() // only touched on [worker]

    private val _visible = MutableStateFlow<List<Milestone>>(emptyList())
    val visible: StateFlow<List<Milestone>> = _visible.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val syncMutex = Mutex()
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        scope.launch {
            scopeKey = currentUid() ?: MILESTONE_LOCAL_SCOPE
            store.activeScope = scopeKey
            all = store.load(scopeKey)
            adoptGuestMilestones()
            commit(all, all) // persist adoption, publish, and re-arm every reminder
            _loaded.value = true
            registerListeners()
            requestSync()
        }
    }

    fun close() {
        runCatching { authListener?.let { Firebase.auth.removeAuthStateListener(it) } }
        runCatching {
            networkCallback?.let {
                (appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(it)
            }
        }
        scope.cancel()
        worker.shutdown()
    }

    private fun registerListeners() {
        runCatching {
            val l = FirebaseAuth.AuthStateListener { auth ->
                val newKey = auth.currentUser?.uid ?: MILESTONE_LOCAL_SCOPE
                scope.launch {
                    if (newKey != scopeKey) switchScope(newKey)
                    requestSync()
                }
            }
            authListener = l
            Firebase.auth.addAuthStateListener(l)
        }.onFailure { Log.w(TAG, "Auth listener unavailable: ${it.message}") }

        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { requestSync() }
            }
            networkCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        }.onFailure { Log.w(TAG, "Network callback unavailable: ${it.message}") }
    }

    private fun currentUid(): String? = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    private fun switchScope(newKey: String) {
        all.forEach { scheduler.cancel(it.id) }
        scopeKey = newKey
        store.activeScope = newKey
        all = store.load(newKey)
        adoptGuestMilestones()
        commit(all, all)
    }

    /** When signed in, move any guest milestones into the account (marked dirty so they upload once). */
    private fun adoptGuestMilestones() {
        if (scopeKey == MILESTONE_LOCAL_SCOPE) return
        val guest = store.load(MILESTONE_LOCAL_SCOPE)
        if (guest.isEmpty()) return
        val known = all.map { it.id }.toSet()
        all = all + guest.filter { it.id !in known }.map { it.copy(dirty = true) }
        store.save(MILESTONE_LOCAL_SCOPE, emptyList())
    }

    private fun commit(newAll: List<Milestone>, touched: Collection<Milestone>) {
        all = newAll
        _visible.value = newAll.filter { !it.deleted }
        store.save(scopeKey, newAll)
        scheduler.apply(touched)
    }

    // ---------------------------------------------------------------- mutations

    fun upsert(milestone: Milestone) {
        scope.launch {
            val stamped = milestone.copy(updatedAt = nextStamp(milestone.id), dirty = true, deleted = false)
            commit(all.filter { it.id != stamped.id } + stamped, listOf(stamped))
            requestSync()
        }
    }

    fun delete(id: String) {
        scope.launch {
            val current = all.firstOrNull { it.id == id } ?: return@launch
            val tomb = current.copy(deleted = true, pinned = false, updatedAt = nextStamp(id), dirty = true)
            commit(all.map { if (it.id == id) tomb else it }, listOf(tomb))
            requestSync()
        }
    }

    fun setPinned(id: String, pinned: Boolean) = change(id) {
        it.copy(pinned = pinned, pinnedAt = if (pinned) System.currentTimeMillis() else 0L)
    }

    fun setCompleted(id: String, completed: Boolean) = change(id) { it.copy(completed = completed) }

    private fun change(id: String, transform: (Milestone) -> Milestone) {
        scope.launch {
            val current = all.firstOrNull { it.id == id && !it.deleted } ?: return@launch
            val updated = transform(current).copy(updatedAt = nextStamp(id), dirty = true)
            commit(all.map { if (it.id == id) updated else it }, listOf(updated))
            requestSync()
        }
    }

    /** Keeps the profile-linked birthday milestone in step with StudentProfile.dateOfBirth. */
    fun syncBirthdayWithProfile(dobRaw: String) {
        val dob = MilestoneDates.parseDateOfBirth(dobRaw) ?: return
        scope.launch {
            val b = all.firstOrNull { it.presetKey == MilestonePresets.BIRTHDAY && !it.deleted } ?: return@launch
            if (b.targetDate == dob.toString()) return@launch
            val updated = b.copy(targetDate = dob.toString(), updatedAt = nextStamp(b.id), dirty = true)
            commit(all.map { if (it.id == b.id) updated else it }, listOf(updated))
            requestSync()
        }
    }

    /** Strictly increasing per-item timestamp so rapid edits always win the last-write comparison. */
    private fun nextStamp(id: String): Long {
        val prev = all.firstOrNull { it.id == id }?.updatedAt ?: 0L
        return maxOf(System.currentTimeMillis(), prev + 1)
    }

    // ---------------------------------------------------------------- sync

    fun requestSync() {
        scope.launch {
            syncMutex.withLock {
                runCatching { syncOnce() }.onFailure { Log.w(TAG, "Milestone sync skipped: ${it.message}") }
            }
        }
    }

    private suspend fun syncOnce() {
        val uid = currentUid() ?: return
        if (scopeKey != uid) return
        val col = firestore.collection("users").document(uid).collection("milestones")

        // Pull, then merge by updatedAt (newer wins; ties keep local).
        val snap = withTimeoutOrNull(NET_TIMEOUT_MS) { col.get().awaitTask() } ?: return
        val remote = snap.documents.mapNotNull { it.toMilestone(uid) }
        if (scopeKey != uid) return

        val byId = all.associateBy { it.id }.toMutableMap()
        val touched = ArrayList<Milestone>()
        for (r in remote) {
            val l = byId[r.id]
            if (l == null || r.updatedAt > l.updatedAt) {
                val adopted = r.copy(dirty = false)
                byId[r.id] = adopted
                touched.add(adopted)
            }
        }
        if (touched.isNotEmpty()) {
            val order = all.map { it.id }
            val merged = order.mapNotNull { byId[it] } + byId.values.filter { it.id !in order }
            commit(merged, touched)
        }

        // Push local changes in batches.
        val dirty = all.filter { it.dirty }
        for (chunk in dirty.chunked(400)) {
            val batch = firestore.batch()
            chunk.forEach { batch.set(col.document(it.id), it.toCloud(uid)) }
            val ok = withTimeoutOrNull(NET_TIMEOUT_MS) { batch.commit().awaitTask(); true } ?: false
            if (!ok) return // offline: stays dirty and is retried on reconnect / next change
            if (scopeKey != uid) return
            val pushed = chunk.associate { it.id to it.updatedAt }
            // Only clear the flag if the item wasn't edited again while the upload was in flight.
            commit(
                all.map { if (pushed[it.id] == it.updatedAt) it.copy(dirty = false) else it },
                emptyList()
            )
        }

        // Drop old, fully-synced tombstones locally.
        val cutoff = System.currentTimeMillis() - TOMBSTONE_TTL_MS
        val pruned = all.filter { !(it.deleted && !it.dirty && it.updatedAt < cutoff) }
        if (pruned.size != all.size) commit(pruned, emptyList())
    }

    private fun Milestone.toCloud(uid: String): Map<String, Any> = hashMapOf(
        "userId" to uid,
        "title" to title,
        "description" to description,
        "category" to category.name,
        "targetDate" to targetDate,
        "accent" to accent.name,
        "recurrence" to recurrence.name,
        "reminderDays" to reminderDays,
        "pinned" to pinned,
        "pinnedAt" to pinnedAt,
        "createdAt" to createdAt,
        "startDate" to startDate,
        "completed" to completed,
        "presetKey" to (presetKey ?: ""),
        "dateSource" to dateSource.name,
        "sourceLabel" to (sourceLabel ?: ""),
        "updatedAtMs" to updatedAt,
        "deleted" to deleted
    )

    private fun DocumentSnapshot.toMilestone(uid: String): Milestone? {
        val d = data ?: return null
        if (d["userId"] != uid) return null
        val title = d["title"] as? String ?: return null
        val date = d["targetDate"] as? String ?: return null
        fun str(k: String) = d[k] as? String ?: ""
        fun num(k: String) = (d[k] as? Number)?.toLong() ?: 0L
        return Milestone(
            id = id,
            title = title,
            description = str("description"),
            category = MilestoneCategory.fromKey(str("category")),
            targetDate = date,
            accent = MilestoneAccent.fromKey(str("accent")),
            recurrence = MilestoneRecurrence.fromKey(str("recurrence")),
            reminderDays = (d["reminderDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
                ?.filter { it in com.example.data.model.MilestoneLimits.REMINDER_OPTIONS }
                ?.distinct()?.sortedDescending() ?: emptyList(),
            pinned = d["pinned"] as? Boolean ?: false,
            pinnedAt = num("pinnedAt"),
            createdAt = num("createdAt"),
            startDate = str("startDate"),
            completed = d["completed"] as? Boolean ?: false,
            presetKey = str("presetKey").ifBlank { null },
            dateSource = MilestoneDateSource.fromKey(str("dateSource")),
            sourceLabel = str("sourceLabel").ifBlank { null },
            updatedAt = num("updatedAtMs"),
            deleted = d["deleted"] as? Boolean ?: false,
            dirty = false
        )
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        const val TAG = "MilestoneRepo"
        const val NET_TIMEOUT_MS = 20_000L
        const val TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
