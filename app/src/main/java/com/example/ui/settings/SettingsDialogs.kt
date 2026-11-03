@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudentProfile
import com.example.data.repository.AttendanceRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun dialogBg(p: SettingsPalette) = if (p.dark) Color(0xFF121826) else com.example.ui.theme.LightPearl

// ───────────────────────── Edit Profile (bottom sheet) ─────────────────────────

/**
 * Edit Profile — premium bottom sheet.
 * Behaviour is unchanged: same fields, same input limits (60 / 30 / 40), same "name required" rule,
 * same date picker (max = today, yyyy-MM-dd), same photo callbacks and same onSave/onDismiss contracts.
 */
@Composable
fun ProfileEditDialog(
    p: SettingsPalette,
    profile: StudentProfile,
    onDismiss: () -> Unit,
    onSave: (name: String, roll: String, session: String, dob: String) -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(profile.name) }
    var roll by remember { mutableStateOf(profile.rollNumber) }
    var session by remember { mutableStateOf(profile.academicSession) }
    var dob by remember { mutableStateOf(profile.dateOfBirth) }
    val nameInvalid = name.isBlank()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = dialogBg(p),
        scrimColor = Color(0x99050A1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.textMuted.copy(alpha = 0.5f)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header
            Column {
                SText("Edit Profile", 22.sp, p.text, weight = FontWeight.Bold)
                SText("Update your name, roll number, academic session and date of birth.", 12.sp, p.textSecondary, Modifier.padding(top = 2.dp), lineHeight = 17.sp)
            }

            // 2. Avatar preview with gradient ring + photo actions
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val spin by rememberLoop(9000, "editRing")
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(112.dp).rotate(spin * 360f).graphicsLayer { alpha = 0.95f }.clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(p.violet, Color(0xFF2563EB), Color(0xFF00F0FF), Color(0xFF3B82F6), p.violet)))
                    )
                    Box(Modifier.size(106.dp).clip(CircleShape).background(dialogBg(p)))
                    GlobalProfileAvatar(photoPath = profile.photoPath, isDark = p.dark, size = 98.dp)
                }
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SheetPill(p, "Change Photo", filled = true, onClick = onChangePhoto)
                    if (profile.photoPath.isNotBlank()) {
                        SheetPill(p, "Reset to BK Preset", filled = false, onClick = onRemovePhoto)
                    }
                }
            }

            // 3. Fields
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.text, unfocusedTextColor = p.text, errorTextColor = p.text,
                focusedContainerColor = p.inset, unfocusedContainerColor = p.inset, errorContainerColor = p.inset,
                focusedBorderColor = p.indigoLight, unfocusedBorderColor = p.insetBorder, errorBorderColor = p.rose,
                focusedLabelColor = if (p.dark) p.cyanSoft else p.indigo, unfocusedLabelColor = p.textSecondary, errorLabelColor = p.rose,
                cursorColor = p.indigoLight, errorCursorColor = p.rose,
                focusedSupportingTextColor = p.textSecondary, unfocusedSupportingTextColor = p.textSecondary, errorSupportingTextColor = p.rose
            )
            val fieldShape = RoundedCornerShape(16.dp)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true, isError = nameInvalid,
                label = { Text("Full name") },
                supportingText = { if (nameInvalid) Text("Name is required") else Text("${name.length}/60") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = fieldShape, colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = roll, onValueChange = { roll = it.take(30) }, singleLine = true,
                label = { Text("Roll number (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = fieldShape, colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = session, onValueChange = { session = it.take(40) }, singleLine = true,
                label = { Text("Academic session (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = fieldShape, colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Date of birth row (same picker, same label strings)
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(fieldShape).background(p.inset)
                    .border(1.dp, p.insetBorder, fieldShape)
                    .tapClick(role = Role.Button) {
                        val cal = Calendar.getInstance()
                        runCatching { fmt.parse(dob)?.let { cal.time = it } }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            dob = "%04d-%02d-%02d".format(y, m + 1, d)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SText(
                    if (dob.isBlank()) "Set date of birth" else "Date of birth: $dob  (change)",
                    13.sp, if (dob.isBlank()) (if (p.dark) p.cyanSoft else p.indigo) else p.text,
                    Modifier.weight(1f), FontWeight.SemiBold
                )
                SText("›", 20.sp, p.textMuted, weight = FontWeight.Bold)
            }

            // 5. Actions
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(p.inset)
                        .border(1.dp, p.insetBorder, RoundedCornerShape(16.dp))
                        .tapClick(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { SText("Cancel", 14.sp, p.text, weight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(52.dp)
                        .alpha(if (nameInvalid) 0.45f else 1f)
                        .clip(RoundedCornerShape(16.dp)).background(p.accentBrush)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .tapClick(enabled = !nameInvalid, role = Role.Button) { onSave(name.trim(), roll.trim(), session.trim(), dob) },
                    contentAlignment = Alignment.Center
                ) { SText("Save", 14.sp, Color.White, weight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SheetPill(p: SettingsPalette, label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(if (filled) p.indigo.copy(alpha = if (p.dark) 0.28f else 0.12f) else Color.Transparent)
            .border(1.dp, if (filled) p.indigoLight.copy(alpha = 0.6f) else p.insetBorder, shape)
            .tapClick(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        SText(label, 12.sp, if (filled) (if (p.dark) Color(0xFFB4BEFF) else p.indigo) else p.textSecondary, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ───────────────────────── other dialogs (restyled; logic untouched) ─────────────────────────

private val dialogShape = RoundedCornerShape(28.dp)

@Composable
fun StipendDialog(p: SettingsPalette, current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit, onTick: () -> Unit) {
    var v by remember { mutableIntStateOf(current.coerceIn(50, 100)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg(p),
        shape = dialogShape,
        title = { SText("Stipend Target", 18.sp, p.text, weight = FontWeight.Bold) },
        text = {
            Column {
                SText("$v%", 32.sp, p.accentText, weight = FontWeight.ExtraBold)
                SText("Monthly attendance needed for stipend eligibility. This is independent of the exam target.", 12.sp, p.textSecondary, Modifier.padding(bottom = 14.dp), lineHeight = 17.sp)
                FlowSlider(v, { v = it }, 50, 100, p, Modifier.fillMaxWidth(), onStep = onTick)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text("Save", color = p.indigoLight, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.textSecondary) } }
    )
}

@Composable
fun RestoreConfirmDialog(p: SettingsPalette, preview: AttendanceRepository.BackupPreview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg(p),
        shape = dialogShape,
        title = { SText("Restore this backup?", 18.sp, p.text, weight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SText("The file passed validation.", 13.sp, p.textSecondary)
                if (preview.profileName.isNotBlank()) SText("Profile: ${preview.profileName}", 13.sp, p.text, weight = FontWeight.SemiBold)
                SText("${preview.recordDays} attendance days • ${preview.periodEntries} period entries", 13.sp, p.text, weight = FontWeight.SemiBold)
                preview.exportedAt?.let { SText("Exported: $it", 12.sp, p.textMuted) }
                SText(
                    "Days in this file will replace the same days on this device. Days that exist only on this device are kept. " +
                        "A snapshot of your current data is saved first.",
                    12.sp, p.textSecondary, lineHeight = 17.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore", color = Color(0xFFEF4444)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.textSecondary) } }
    )
}

@Composable
fun ResetConfirmDialog(p: SettingsPalette, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg(p),
        shape = dialogShape,
        title = { SText("Reset All App Data?", 18.sp, p.text, weight = FontWeight.Bold) },
        text = {
            SText(
                "This will permanently wipe all local attendance records, custom settings, and return the app to a clean initial state. This cannot be undone.",
                13.sp, p.textSecondary, lineHeight = 18.sp
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Reset Everything", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.textSecondary) } }
    )
}
