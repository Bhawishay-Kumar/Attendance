package com.example.ui.countdown

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.milestone.MilestoneDates
import com.example.data.milestone.MilestonePreset
import com.example.data.milestone.MilestonePresets
import com.example.data.milestone.OfficialSchedules
import com.example.data.model.Milestone
import com.example.data.model.MilestoneAccent
import com.example.data.model.MilestoneCategory
import com.example.data.model.MilestoneLimits
import com.example.data.model.MilestoneRecurrence
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MilestoneDraft
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.launch
import java.time.ZoneOffset

/**
 * Compact Add / Edit sheet with two animated tabs: Readymade (preset grid) and Custom (form).
 * Editing opens straight on the Custom form with the stored values filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMilestoneSheet(
    editing: Milestone?,
    dateOfBirth: String,
    birthdayExists: Boolean,
    onDismiss: () -> Unit,
    /** Returns an error message to show, or null when saved. */
    onSave: (MilestoneDraft) -> String?,
    /** Adds the profile-based birthday. Returns false when no usable date of birth exists. */
    onAddBirthday: () -> Boolean,
    onOpenProfile: () -> Unit
) {
    val colors = rememberMilestoneColors()
    val motion = rememberMotionEnabled()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // One id per opened form: tapping Save twice can only ever create one milestone.
    val clientId = rememberSaveable { UUID.randomUUID().toString() }
    var closing by remember { mutableStateOf(false) }

    /** Slides the sheet down first, then tells the host it is gone. Ignores repeat taps. */
    fun closeThen(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            sheetState.hide()
            after()
            onDismiss()
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(if (editing != null) 1 else 0) }
    var title by rememberSaveable { mutableStateOf(editing?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(editing?.description ?: "") }
    var dateIso by rememberSaveable { mutableStateOf(editing?.targetDate ?: "") }
    var categoryKey by rememberSaveable { mutableStateOf((editing?.category ?: MilestoneCategory.OTHER).name) }
    var accentKey by rememberSaveable { mutableStateOf((editing?.accent ?: MilestoneAccent.VIOLET).name) }
    var recurrenceKey by rememberSaveable { mutableStateOf((editing?.recurrence ?: MilestoneRecurrence.NONE).name) }
    var remindersCsv by rememberSaveable { mutableStateOf(editing?.reminderDays?.joinToString(",") ?: "") }
    var presetKey by rememberSaveable { mutableStateOf<String?>(editing?.presetKey) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showProfilePrompt by remember { mutableStateOf(false) }

    fun applyPreset(p: MilestonePreset) {
        presetKey = p.key
        title = p.title
        description = p.description
        categoryKey = p.category.name
        accentKey = p.accent.name
        recurrenceKey = p.recurrence.name
        dateIso = ""
        remindersCsv = ""
        error = null
        tab = 1
    }

    fun handlePreset(preset: MilestonePreset) {
        notice = null
        if (preset.key != MilestonePresets.BIRTHDAY) {
            applyPreset(preset)
            return
        }
        if (birthdayExists) {
            notice = "Your birthday is already added."
        } else if (MilestoneDates.parseDateOfBirth(dateOfBirth) == null) {
            showProfilePrompt = true
        } else if (!closing) {
            if (onAddBirthday()) closeThen() else showProfilePrompt = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .auroraBackground(colors, motion)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (editing != null) "Edit Milestone" else "Add Milestone",
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).pressableScale(pressedScale = 0.9f, onClick = { closeThen() }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }

            if (editing == null) {
                TabSwitcher(tab = tab, onSelect = { tab = it; error = null }, colors = colors)
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 5 * dir } + fadeOut(tween(150)))
                },
                label = "milestoneTabs"
            ) { current ->
                if (current == 0) {
                    ReadymadeTab(
                        colors = colors,
                        notice = notice,
                        onPreset = { preset -> handlePreset(preset) },
                        motion = motion,
                        onCustom = {
                            presetKey = null
                            tab = 1
                        }
                    )
                } else {
                    CustomForm(
                        colors = colors,
                        isEditing = editing != null,
                        title = title, onTitle = { if (it.length <= MilestoneLimits.MAX_TITLE) title = it },
                        description = description, onDescription = { if (it.length <= MilestoneLimits.MAX_DESCRIPTION) description = it },
                        dateIso = dateIso, onDate = { dateIso = it },
                        categoryKey = categoryKey, onCategory = { categoryKey = it },
                        accentKey = accentKey, onAccent = { accentKey = it },
                        recurrenceKey = recurrenceKey, onRecurrence = { recurrenceKey = it },
                        remindersCsv = remindersCsv, onReminders = { remindersCsv = it },
                        presetKey = presetKey,
                        error = error,
                        saving = closing,
                        motion = motion,
                        onSave = {
                            val reminders = remindersCsv.split(",").mapNotNull { it.toIntOrNull() }
                            if (!closing) error = onSave(
                                MilestoneDraft(
                                    editingId = editing?.id,
                                    clientId = clientId,
                                    title = title,
                                    description = description,
                                    category = MilestoneCategory.fromKey(categoryKey),
                                    targetDate = dateIso,
                                    accent = MilestoneAccent.fromKey(accentKey),
                                    recurrence = MilestoneRecurrence.fromKey(recurrenceKey),
                                    reminderDays = reminders,
                                    presetKey = presetKey
                                )
                            )
                            if (error == null) closeThen()
                        }
                    )
                }
            }
        }
    }

    if (showProfilePrompt) {
        AlertDialog(
            onDismissRequest = { showProfilePrompt = false },
            containerColor = colors.surfaceStrong,
            title = { Text("Add your date of birth", fontFamily = OutfitFontFamily, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Complete your profile with a date of birth and your next birthday is counted down automatically, every year.",
                    fontFamily = PlusJakartaSansFontFamily,
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showProfilePrompt = false; closeThen { onOpenProfile() } }) { Text("Open profile") }
            },
            dismissButton = {
                TextButton(onClick = { showProfilePrompt = false }) { Text("Not now") }
            }
        )
    }
}

// ------------------------------------------------------------------ tabs

@Composable
private fun TabSwitcher(tab: Int, onSelect: (Int) -> Unit, colors: MilestoneColors) {
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(colors.fieldBg)
            .border(1.dp, colors.border, shape)
            .padding(3.dp)
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (tab == 0) 0.dp else half,
            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
            label = "tabIndicator"
        )
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(half)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(colors.buttonBrush)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            listOf("Readymade", "Custom").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .pressableScale(pressedScale = 0.97f, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (tab == index) Color.White else colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadymadeTab(
    colors: MilestoneColors,
    notice: String?,
    motion: Boolean,
    onPreset: (MilestonePreset) -> Unit,
    onCustom: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (notice != null) {
            Text(notice, color = colors.danger, fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp)
        }
        MilestonePresets.all.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { col, preset ->
                    PresetCard(
                        preset, colors,
                        Modifier.weight(1f).staggerIn(index = rowIndex * 2 + col, motion = motion)
                    ) { onPreset(preset) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .staggerIn(index = 10, motion = motion)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colors.accent(MilestoneAccent.BLUE).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .pressableScale(onClick = onCustom),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = colors.accent(MilestoneAccent.BLUE), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Add Custom Milestone",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.accent(MilestoneAccent.BLUE)
            )
        }
    }
}

@Composable
private fun PresetCard(preset: MilestonePreset, colors: MilestoneColors, modifier: Modifier, onClick: () -> Unit) {
    val accent = colors.accent(preset.accent)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(72.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f))))
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .pressableScale(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(preset.category.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                preset.title,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                preset.subtitle,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 10.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ------------------------------------------------------------------ custom form

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomForm(
    colors: MilestoneColors,
    isEditing: Boolean,
    title: String, onTitle: (String) -> Unit,
    description: String, onDescription: (String) -> Unit,
    dateIso: String, onDate: (String) -> Unit,
    categoryKey: String, onCategory: (String) -> Unit,
    accentKey: String, onAccent: (String) -> Unit,
    recurrenceKey: String, onRecurrence: (String) -> Unit,
    remindersCsv: String, onReminders: (String) -> Unit,
    presetKey: String?,
    error: String?,
    saving: Boolean,
    motion: Boolean,
    onSave: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val category = MilestoneCategory.fromKey(categoryKey)
    val accent = colors.accent(MilestoneAccent.fromKey(accentKey))
    val selectedReminders = remindersCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val dateValue = MilestoneDates.parse(dateIso)
    val official = remember(presetKey) { OfficialSchedules.forPreset(presetKey) }
    val preset = MilestonePresets.byKey(presetKey)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = accent,
        focusedContainerColor = colors.fieldBg,
        unfocusedContainerColor = colors.fieldBg,
        focusedPlaceholderColor = colors.textMuted,
        unfocusedPlaceholderColor = colors.textMuted
    )
    val fieldText = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontSize = 14.sp)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (preset != null && !isEditing) {
            Text(
                "Based on: ${preset.title}",
                fontFamily = PlusJakartaSansFontFamily,
                color = colors.textSecondary,
                fontSize = 11.5.sp
            )
        }

        FieldLabel("Title", required = true, colors = colors)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().staggerIn(index = 0, motion = motion),
            value = title,
            onValueChange = onTitle,
            singleLine = true,
            placeholder = { Text("e.g. Workshop / Personal Goal") },
            textStyle = fieldText,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors
        )

        FieldLabel("Date", required = true, colors = colors)
        PickerField(
            text = dateValue?.cardText() ?: "Select date",
            isPlaceholder = dateValue == null,
            icon = Icons.Rounded.CalendarMonth,
            colors = colors,
            onClick = { showDatePicker = true }
        )
        if (official != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Official dates",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.success
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    official.dates.forEach { d ->
                        FilterChip(
                            selected = dateIso == d.iso,
                            onClick = { onDate(d.iso) },
                            label = { Text(d.label, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }
                val verified = if (official.verifiedOn.isNotBlank()) " Checked ${official.verifiedOn}." else ""
                Text(
                    "${official.note} Source: ${official.sourceName}.$verified",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = colors.textMuted
                )
            }
        } else if (preset?.manualNote != null) {
            Text(
                preset.manualNote,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = colors.textMuted
            )
        }

        FieldLabel("Category", required = true, colors = colors)
        Box {
            PickerField(
                text = category.label,
                isPlaceholder = false,
                icon = Icons.Rounded.KeyboardArrowDown,
                leading = category.icon(),
                colors = colors,
                onClick = { categoryMenu = true }
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                MilestoneCategory.entries.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.label) },
                        leadingIcon = { Icon(c.icon(), null) },
                        onClick = { onCategory(c.name); categoryMenu = false }
                    )
                }
            }
        }

        FieldLabel("Description (optional)", required = false, colors = colors)
        OutlinedTextField(
            value = description,
            onValueChange = onDescription,
            maxLines = 3,
            placeholder = { Text("Add more details...") },
            textStyle = fieldText,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        FieldLabel("Color", required = false, colors = colors)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MilestoneAccent.entries.forEach { a ->
                AccentSwatch(a, selected = a.name == accentKey, colors = colors) { onAccent(a.name) }
            }
        }

        FieldLabel("Reminders (optional)", required = false, colors = colors)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Notifications, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            MilestoneLimits.REMINDER_OPTIONS.forEach { n ->
                FilterChip(
                    selected = n in selectedReminders,
                    onClick = {
                        val next = if (n in selectedReminders) selectedReminders - n else selectedReminders + n
                        onReminders(next.sortedDescending().joinToString(","))
                    },
                    label = { Text(if (n == 1) "1 day before" else "$n days before", fontSize = 11.sp, maxLines = 1) }
                )
            }
        }

        FieldLabel("Repeat (optional)", required = false, colors = colors)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MilestoneRecurrence.entries.forEach { r ->
                FilterChip(
                    selected = r.name == recurrenceKey,
                    onClick = { onRecurrence(r.name) },
                    label = { Text(r.label, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }

        if (error != null) {
            Text(error, color = colors.danger, fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        GradientButton(
            text = if (isEditing) "Save Changes" else "Save Milestone",
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.staggerIn(index = 8, motion = motion)
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateValue?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        // The picker works in UTC midnight; convert back without any zone shift.
                        onDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun FieldLabel(text: String, required: Boolean, colors: MilestoneColors) {
    Row {
        Text(
            text,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
        if (required) Text(" *", color = colors.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PickerField(
    text: String,
    isPlaceholder: Boolean,
    icon: ImageVector,
    colors: MilestoneColors,
    onClick: () -> Unit,
    leading: ImageVector? = null
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.fieldBg)
            .border(1.dp, colors.border, shape)
            .pressableScale(pressedScale = 0.99f, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leading != null) Icon(leading, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 14.sp,
            color = if (isPlaceholder) colors.textMuted else colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AccentSwatch(accent: MilestoneAccent, selected: Boolean, colors: MilestoneColors, onClick: () -> Unit) {
    val c = colors.accent(accent)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "swatchScale"
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(c)
            .then(if (selected) Modifier.border(2.5.dp, colors.textPrimary, CircleShape) else Modifier)
            .semantics { contentDescription = "${accent.name.lowercase()} color" + if (selected) ", selected" else "" }
            .pressableScale(pressedScale = 0.9f, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
