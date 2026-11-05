package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.StudentProfile
import com.example.data.model.ThemeMode
import com.example.data.repository.AttendanceRepository
import com.example.ui.settings.AppearanceCard
import com.example.ui.settings.AttendanceRulesCard
import com.example.ui.settings.CloudCard
import com.example.ui.settings.CloudUi
import com.example.ui.settings.DataManagementCard
import com.example.ui.settings.DeveloperBanner
import com.example.ui.settings.HapticCard
import com.example.ui.settings.FxHost
import com.example.ui.settings.FxOverlay
import com.example.ui.settings.ProvideFx
import com.example.ui.settings.rememberMotionEnabled
import com.example.ui.settings.Haptics
import com.example.ui.settings.NotificationsCard
import com.example.ui.settings.ProfileEditDialog
import com.example.ui.settings.ProfilePhotoStore
import com.example.ui.settings.RestoreConfirmDialog
import com.example.ui.settings.ResetConfirmDialog
import com.example.ui.settings.SText
import com.example.ui.settings.SettingsFooter
import com.example.ui.settings.StipendDialog
import com.example.ui.settings.StudentProfileCard
import com.example.ui.settings.ThankYouCard
import com.example.ui.settings.rememberSettingsPalette
import com.example.ui.components.AppScreenHeader
import com.example.ui.components.EntranceTracker
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.viewmodel.AttendanceViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun safeFirebaseAuth(context: Context): FirebaseAuth? = runCatching {
    if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
    }
    Firebase.auth
}.getOrNull()

/**
 * Settings & Profile — native Compose port of the Stitch "luxury glass" design.
 * All state lives in the existing [AttendanceViewModel] / [AttendanceRepository]; this file only wires UI to them.
 */
@Composable
fun SettingsScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val p = rememberSettingsPalette(glass.isDark)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by viewModel.profile.collectAsState()
    val repo = viewModel.repository
    val credentialManager = remember { CredentialManager.create(context) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun update(new: StudentProfile) = viewModel.updateProfile(new)
    fun tick() = Haptics.tick(context, viewModel.profile.value)

    // ── dialogs / transient state ──
    var showEdit by remember { mutableStateOf(false) }
    var showStipend by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<AttendanceRepository.BackupPreview?>(null) }

    // ── cloud state ──
    var currentUser by remember { mutableStateOf(safeFirebaseAuth(context)?.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var busyOp by remember { mutableStateOf<String?>(null) }
    var cloudMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var lastBackup by remember { mutableStateOf(repo.lastCloudBackupMs()) }

    // ── notification permission (Android 13+) ──
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Notifications are blocked. Enable them in system settings to receive reminders.")
    }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ── system Photo Picker (no storage permission) ──
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = ProfilePhotoStore.save(context, uri)
            if (path != null) {
                toast("Profile photo updated")
                val current = viewModel.profile.value
                val old = current.photoPath
                viewModel.updateProfile(current.copy(photoPath = path))
                ProfilePhotoStore.delete(old)
            } else toast("Couldn't read that image.")
        }
    }
    fun pickPhoto() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // ── restore from file ──
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busyOp = "restore"; cloudMsg = null
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                        val t = r.readText()
                        if (t.length > 20_000_000) null else t
                    }
                }.getOrNull()
            }
            if (text == null) {
                cloudMsg = false to "Couldn't read that file."
            } else {
                repo.parseBackup(text)
                    .onSuccess { pendingRestore = it }
                    .onFailure { cloudMsg = false to "Invalid backup: ${it.message ?: "unrecognised format"}" }
            }
            busyOp = null
        }
    }

    // ── export to a user-chosen file (Storage Access Framework) ──
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busyOp = "export"; cloudMsg = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repo.exportBackupJson()
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Couldn't open the destination file.")
                    repo.recordsMap.value.size
                }
            }
            result.onSuccess { cloudMsg = true to "Exported $it attendance days to your chosen file." }
                .onFailure { cloudMsg = false to "Export failed: ${it.message}" }
            busyOp = null
        }
    }

    val statusLine: String
    val statusOk: Boolean
    when {
        currentUser == null -> { statusLine = "Sign in to enable cloud backup"; statusOk = false }
        busyOp == "backup" -> { statusLine = "Backing up…"; statusOk = true }
        lastBackup == 0L -> { statusLine = "Cloud sync on • no manual backup yet"; statusOk = true }
        else -> {
            val mins = ((System.currentTimeMillis() - lastBackup) / 60_000L).coerceAtLeast(0)
            statusLine = "Cloud Backup: " + when {
                mins < 1 -> "Synced just now"
                mins < 60 -> "Synced $mins min ago"
                mins < 1440 -> "Synced ${mins / 60} h ago"
                else -> "Last backup " + SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(lastBackup))
            }
            statusOk = true
        }
    }

    val fxHost = remember { FxHost { Haptics.tick(context, viewModel.profile.value) } }
    val seen = rememberEntranceTracker()

    // Transparent root: the shared app background + starfield (MainActivity) show through, same as Home.
    ProvideFx(fxHost) {
    Box(modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Shared header (same component, top offset and toggle size as every other tab)
            item(key = "header") {
                AppScreenHeader(
                    title = "Settings & Profile",
                    subtitle = "Personalization & App Information",
                    isDark = glass.isDark,
                    onToggleTheme = { viewModel.toggleDarkMode(); tick() },
                    modifier = Modifier.padding(horizontal = 4.dp) // list has 16dp -> title sits 20dp from the edge
                )
            }

            // 2. Student profile
            item(key = "profile") {
                Reveal(1, seen) {
                    StudentProfileCard(p, profile, onEdit = { showEdit = true }, onPickPhoto = ::pickPhoto, onChange = ::update)
                }
            }

            // 3. Attendance rules
            item(key = "rules") {
                Reveal(2, seen) {
                    AttendanceRulesCard(
                        p, profile, onChange = ::update,
                        onFutureMarking = { viewModel.toggleFutureMarking(it) },
                        onEditStipend = { showStipend = true },
                        onTick = ::tick
                    )
                }
            }

            // 4. Appearance
            item(key = "appearance") {
                Reveal(3, seen) {
                    AppearanceCard(
                        p, profile,
                        onThemeMode = { viewModel.setThemeMode(it) },
                        onStarfield = { update(viewModel.profile.value.copy(starfieldAnimationEnabled = it)) },
                        onTick = ::tick
                    )
                }
            }

            // 5. Notifications
            item(key = "notifications") {
                Reveal(4, seen) {
                    NotificationsCard(p, profile, onChange = ::update, onNeedsPermission = ::ensureNotificationPermission, onTick = ::tick)
                }
            }

            // 6. Haptics (home-screen widget row intentionally deferred to a future update)
            item(key = "haptics") {
                Reveal(5, seen) {
                    HapticCard(
                        p, profile,
                        onChange = ::update,
                        onPreview = { Haptics.play(context, it) }
                    )
                }
            }

            // 7. Cloud
            item(key = "cloud") {
                Reveal(6, seen) {
                    CloudCard(
                        p,
                        CloudUi(currentUser?.email, statusLine, statusOk, busyOp, cloudMsg, isSigningIn),
                        onSignIn = {
                            isSigningIn = true
                            val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
                            if (clientId == null) {
                                toast("Google Client ID missing"); isSigningIn = false
                            } else {
                            val option = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                            val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
                            scope.launch {
                                try {
                                    val res = credentialManager.getCredential(context as Activity, req)
                                    val cred = res.credential
                                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                                        val auth = safeFirebaseAuth(context) ?: error("Firebase unavailable")
                                        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                                        currentUser = auth.currentUser
                                        toast("Signed in successfully!")
                                    }
                                } catch (e: GetCredentialCancellationException) {
                                    // dismissed
                                } catch (e: Exception) {
                                    toast("Sign in failed: ${e.message}")
                                } finally {
                                    isSigningIn = false
                                }
                            }
                            }
                        },
                        onSignOut = {
                            safeFirebaseAuth(context)?.signOut()
                            scope.launch {
                                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                                currentUser = null
                                cloudMsg = null
                            }
                        },
                        onBackup = {
                            val auth = safeFirebaseAuth(context)
                            if (auth?.currentUser == null) {
                                cloudMsg = false to "Sign in with Google first to back up to the cloud."
                            } else scope.launch {
                                busyOp = "backup"; cloudMsg = null
                                val r = withTimeoutOrNull(30_000L) { repo.backupToCloud() }
                                    ?: Result.failure(Exception("Timed out — check your connection and try again."))
                                r.onSuccess {
                                    lastBackup = repo.lastCloudBackupMs()
                                    cloudMsg = true to "Backed up your profile and $it attendance days to the cloud."
                                }.onFailure { cloudMsg = false to (it.message ?: "Backup failed.") }
                                busyOp = null
                            }
                        },
                        onRestore = { cloudMsg = null; openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onExport = {
                            cloudMsg = null
                            createBackup.launch("attendance-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json")
                        }
                    )
                }
            }

            // Existing maintenance actions kept from the previous Settings page
            item(key = "data") {
                Reveal(7, seen) { DataManagementCard(p, onReplay = { viewModel.replayOnboarding() }, onReset = { showReset = true }) }
            }

            // 8–10. Developer (banner + thank-you; the name / role / location / focus strip was removed)
            item(key = "devBanner") { Reveal(8, seen) { DeveloperBanner(p) } }
            item(key = "thanks") { Reveal(9, seen) { ThankYouCard(p) } }

            // 11. Footer (+ clearance for the shared bottom dock)
            item(key = "footer") { SettingsFooter(p) }
            item(key = "dockSpace") { Spacer(Modifier.height(150.dp)) }
        }

        // Sparkle particles fly above everything (not clipped by cards)
        FxOverlay(fxHost)
    }
    }

    // ── dialogs ──
    if (showEdit) {
        ProfileEditDialog(
            p, profile,
            onDismiss = { showEdit = false },
            onSave = { name, roll, session, dob ->
                update(viewModel.profile.value.copy(name = name, rollNumber = roll, academicSession = session, dateOfBirth = dob))
                showEdit = false
            },
            onChangePhoto = ::pickPhoto,
            onRemovePhoto = {
                val cur = viewModel.profile.value
                ProfilePhotoStore.delete(cur.photoPath)
                ProfilePhotoStore.clear(context)
                update(cur.copy(photoPath = ""))
                toast("Reset to BK Monogram Preset")
            }
        )
    }
    if (showStipend) {
        StipendDialog(
            p, profile.stipendTarget,
            onDismiss = { showStipend = false },
            onSave = { update(viewModel.profile.value.copy(stipendTarget = it)); showStipend = false },
            onTick = ::tick
        )
    }
    pendingRestore?.let { preview ->
        RestoreConfirmDialog(
            p, preview,
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                val r = runCatching { repo.applyBackup(preview) }
                cloudMsg = r.fold(
                    { true to "Restored $it attendance days. A snapshot of your previous data was saved first." },
                    { false to "Restore failed: ${it.message}" }
                )
            }
        )
    }
    if (showReset) {
        ResetConfirmDialog(
            p,
            onDismiss = { showReset = false },
            onConfirm = { showReset = false; viewModel.resetAppDataAndReplayOnboarding() }
        )
    }
}

/** Same fade + rise entrance as every other tab (see ui/components/ScreenMotion.kt). */
@Composable
private fun Reveal(index: Int, tracker: EntranceTracker, content: @Composable () -> Unit) {
    Column(Modifier.loadIn(index, tracker)) { content() }
}
