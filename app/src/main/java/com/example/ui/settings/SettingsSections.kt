@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.settings

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import android.app.TimePickerDialog
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.HapticIntensity
import com.example.data.model.StudentProfile
import com.example.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

// ───────────────────────── shared pieces ─────────────────────────

@Composable
private fun CardHeader(
    p: SettingsPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, iconBg, iconTint, size = 34.dp, iconSize = 18.dp, corner = 11.dp)
            Spacer(Modifier.width(10.dp))
            SText(title, 16.sp, p.text, Modifier.weight(1f), FontWeight.Bold, maxLines = 2)
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        }
        // accent hairline: icon colour fading into the divider colour
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(iconTint.copy(alpha = 0.55f), p.divider, Color.Transparent)))
        )
    }
}

@Composable
private fun ToggleRow(
    p: SettingsPalette,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    titleSuffix: @Composable (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth().activeInset(p, checked).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SText(title, 12.sp, p.text, weight = if (checked) FontWeight.Bold else FontWeight.SemiBold)
                titleSuffix?.invoke()
            }
            SText(subtitle, 10.5.sp, p.textSecondary, Modifier.padding(top = 2.dp), lineHeight = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        LuxuryToggle(checked, onChange, p, enabled = enabled)
    }
}

private fun formatDob(raw: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw)!!
    SimpleDateFormat("dd MMM yyyy", Locale.US).format(d)
}.getOrDefault(raw)

private fun format12h(hhmm: String): String = runCatching {
    val d = SimpleDateFormat("HH:mm", Locale.US).parse(hhmm)!!
    SimpleDateFormat("hh:mm a", Locale.US).format(d)
}.getOrDefault(hhmm)

// ───────────────────────── 1. Student profile ─────────────────────────

@Composable
fun StudentProfileCard(
    p: SettingsPalette,
    profile: StudentProfile,
    onEdit: () -> Unit,
    onPickPhoto: () -> Unit,
    onChange: (StudentProfile) -> Unit
) {
    val photo by produceState<ImageBitmap?>(null, profile.photoPath) {
        value = withContext(Dispatchers.IO) { ProfilePhotoStore.load(profile.photoPath) }
    }
    Column(
        Modifier.fillMaxWidth().glassCard(p).glowAura(Color(0x263B82F6), atTop = true, atStart = false).padding(16.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(if (p.dark) listOf(p.indigoLight, p.cyanSoft) else listOf(Color(0xFF2563EB), p.indigo)))
                    .padding(1.5.dp)
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(if (p.dark) Color(0xFF0F172A) else com.example.ui.theme.LightPearl),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.School, null, tint = p.accentText, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                SText("Student Profile", 14.sp, p.text, weight = FontWeight.Bold, maxLines = 1)
                SText(
                    profile.academicSession.ifBlank { "Add your academic session" },
                    10.sp, p.textMuted, maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (p.dark) Color(0x661E3A8A) else Color(0xCCEFF6FF))
                    .border(1.dp, if (p.dark) Color(0x4D06B6D4) else Color(0xB3BFDBFE), RoundedCornerShape(50))
                    .tapClick(onClick = onEdit)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, null, tint = if (p.dark) Color(0xFF67E8F9) else Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                SText("Edit", 12.sp, if (p.dark) Color(0xFF67E8F9) else Color(0xFF2563EB), weight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.padding(top = 10.dp, bottom = 14.dp).fillMaxWidth().height(1.dp).background(p.divider))

        // Hero
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val ringSpin by rememberLoop(9000, "avatarRing")
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                // animated gradient outline + matte gap around the avatar
                Box(
                    Modifier.size(78.dp).rotate(ringSpin * 360f).graphicsLayer { alpha = 0.9f }.clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(p.violet, Color(0xFF2563EB), Color(0xFF00F0FF), Color(0xFF3B82F6), p.violet)))
                )
                Box(Modifier.size(73.dp).clip(CircleShape).background(if (p.dark) Color(0xFF131A2A) else com.example.ui.theme.LightPearl))
                Box(Modifier.size(66.dp)) {
                    Box(
                        Modifier.fillMaxSize()
                            .tapClick(role = androidx.compose.ui.semantics.Role.Button, onClick = onPickPhoto)
                    ) {
                        GlobalProfileAvatar(
                            photoPath = profile.photoPath,
                            isDark = p.dark,
                            size = 66.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 44dp touch target around the 24dp camera badge
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp).size(40.dp)
                            .tapClick(role = androidx.compose.ui.semantics.Role.Button, onClick = onPickPhoto),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF2563EB))
                                .border(2.dp, if (p.dark) Color(0xFF0F172A) else com.example.ui.theme.LightPearl, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.CameraAlt, "Change photo", tint = Color.White, modifier = Modifier.size(12.dp)) }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SText(profile.name.ifBlank { "Student" }, 20.sp, p.text, Modifier.weight(1f, fill = false), FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Verified, null, tint = p.cyan, modifier = Modifier.size(16.dp))
                }
                if (profile.rollNumber.isNotBlank()) {
                    SText("Roll: ${profile.rollNumber}", 11.sp, if (p.dark) Color(0xFFCBD5E1) else Color(0xFF475569), Modifier.padding(top = 2.dp), maxLines = 1)
                }
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (profile.dateOfBirth.isNotBlank()) {
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(if (p.dark) Color(0xCC1E293B) else com.example.ui.theme.LightSurfaceAlt)
                                .border(1.dp, if (p.dark) Color(0x99334155) else Color(0x99E2E8F0), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cake, null, tint = p.textSecondary, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            SText(formatDob(profile.dateOfBirth), 10.sp, if (p.dark) Color(0xFFCBD5E1) else Color(0xFF334155), weight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    val bob by rememberPulse(3000, -1.5f, 1.5f, "bob")
                    val breathe by rememberPulse(2600, 0.96f, 1.03f, "breathe")
                    Box(
                        Modifier.graphicsLayer { translationY = bob.dp.toPx(); scaleX = breathe; scaleY = breathe }
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0x26F59E0B), Color(0x26A855F7))))
                            .border(1.dp, if (p.dark) Color(0x33FBBF24) else Color(0x66FCD34D), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) { SText("Birthday Magic ✨", 12.sp, if (p.dark) Color(0xFFFCD34D) else Color(0xFFB45309), weight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }

        // Academic configuration
        Column(Modifier.fillMaxWidth().insetBox(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SText("BATCH", 10.sp, p.textSecondary, weight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    SText("Semester Section", 10.sp, p.textMuted)
                }
                SegmentedSelector(
                    listOf(Batch.BATCH_A.displayName, Batch.BATCH_B.displayName),
                    if (profile.batchEnum == Batch.BATCH_A) 0 else 1,
                    { onChange(profile.copy(batch = if (it == 0) Batch.BATCH_A.name else Batch.BATCH_B.name)) },
                    p, Modifier.fillMaxWidth(), itemHeight = 44.dp, textSize = 13.sp, gap = 8.dp
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SText("ELECTIVE COURSE", 10.sp, p.textSecondary, weight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    TagPill("3 Options", if (p.dark) Color(0xB3172554) else Color(0xFFE0E7FF), if (p.dark) Color(0xFF67E8F9) else Color(0xFF4F46E5), if (p.dark) Color(0x99312E81) else Color(0x80C7D2FE), 9.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val labels = mapOf(
                        CourseSelection.MAJOR_HONOURS to "Major: HTFT 501",
                        CourseSelection.MINOR_ELECTIVE to "Minor: MNPM 501",
                        CourseSelection.DIGITAL_LIBRARY to "Digital Library"
                    )
                    CourseSelection.values().forEach { c ->
                        val sel = profile.courseSelectionEnum == c
                        val bg by animateColorAsState(if (sel) (if (p.dark) Color(0xB3172554) else Color(0xCCEEF2FF)) else p.chipBg, label = "chipBg")
                        val bd by animateColorAsState(if (sel) (if (p.dark) Color(0xB367E8F9) else Color(0x994F46E5)) else p.chipBorder, label = "chipBd")
                        Row(
                            Modifier.height(38.dp).clip(RoundedCornerShape(if (sel) 14.dp else 10.dp)).background(bg)
                                .border(if (sel) 1.5.dp else 1.dp, bd, RoundedCornerShape(if (sel) 14.dp else 10.dp))
                                .tapClick(role = androidx.compose.ui.semantics.Role.RadioButton) { onChange(profile.copy(courseSelection = c.name)) }
                                .padding(horizontal = if (sel) 14.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (sel) { PulseDot(if (p.dark) p.cyanSoft else p.indigo); Spacer(Modifier.width(6.dp)) }
                            SText(
                                labels.getValue(c), 11.5.sp,
                                if (sel) (if (p.dark) Color(0xFF67E8F9) else Color(0xFF4F46E5)) else (if (p.dark) Color(0xFFCBD5E1) else Color(0xFF334155)),
                                weight = if (sel) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 2. Attendance rules ─────────────────────────

private class Zone(val label: String, val color: Color)

private fun zoneFor(v: Int, p: SettingsPalette) = when {
    v < 65 -> Zone("Risk Zone", p.rose)
    v < 75 -> Zone("Condonation", p.amber)
    v < 85 -> Zone("Safe Zone", p.emerald)
    else -> Zone("Honors Zone", p.violet)
}

@Composable
fun AttendanceRulesCard(
    p: SettingsPalette,
    profile: StudentProfile,
    onChange: (StudentProfile) -> Unit,
    onFutureMarking: (Boolean) -> Unit,
    onEditStipend: () -> Unit,
    onTick: () -> Unit
) {
    // Local values keep the slider perfectly smooth while dragging; they are committed to the profile once the
    // finger rests (debounced, so cloud sync isn't spammed). They are NOT re-keyed on the profile value any more:
    // that used to reset the thumb to an older committed value in the middle of a drag.
    var exam by remember { mutableIntStateOf(profile.examTarget.coerceIn(50, 100)) }
    var buffer by remember { mutableIntStateOf(profile.safetyBuffer.coerceIn(3, 5)) }
    var examDragging by remember { mutableStateOf(false) }
    var bufferDragging by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(profile)
    // Changes that come from elsewhere (stipend dialog, restore, onboarding) flow in, but never during a drag.
    LaunchedEffect(profile.examTarget) { if (!examDragging) exam = profile.examTarget.coerceIn(50, 100) }
    LaunchedEffect(profile.safetyBuffer) { if (!bufferDragging) buffer = profile.safetyBuffer.coerceIn(3, 5) }
    LaunchedEffect(exam, examDragging) {
        if (examDragging) return@LaunchedEffect
        delay(200)
        if (exam != latest.examTarget) onChange(latest.copy(examTarget = exam))
    }
    LaunchedEffect(buffer, bufferDragging) {
        if (bufferDragging) return@LaunchedEffect
        delay(200)
        if (buffer != latest.safetyBuffer) onChange(latest.copy(safetyBuffer = buffer))
    }

    val zone = zoneFor(exam, p)
    val zoneColor by animateColorAsState(zone.color, label = "zone")

    Column(Modifier.fillMaxWidth().glassCard(p).glowAura(Color(0x2610B981), atTop = true, atStart = true).padding(16.dp)) {
        CardHeader(p, Icons.Default.Tune, if (p.dark) Color(0x80172554) else Color(0xFFEFF6FF), if (p.dark) Color(0xFF60A5FA) else Color(0xFF2563EB), "Attendance Rules & Targets")

        Column(Modifier.fillMaxWidth().insetBox(p, 18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    SText("MINIMUM ATTENDANCE", 11.5.sp, p.text, weight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    SText("Mandatory academic requirement for semester exam eligibility", 10.5.sp, p.textSecondary, Modifier.padding(top = 2.dp), lineHeight = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(zoneColor.copy(alpha = if (p.dark) 0.18f else 0.15f))
                            .border(1.dp, zoneColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulseDot(zoneColor, 6.dp)
                        Spacer(Modifier.width(4.dp))
                        Crossfade(zone.label, label = "zoneLabel") { l ->
                            SText(l.uppercase(), 9.sp, zoneColor, weight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1)
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(p.accentBrush).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SText("$exam%", 14.sp, Color.White, weight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(4.dp))
                        SText("REQ", 9.sp, Color.White.copy(alpha = 0.8f), weight = FontWeight.Medium)
                    }
                }
            }

            FlowSlider(exam, { exam = it }, 50, 100, p, Modifier.fillMaxWidth(), onStep = onTick, onDragStateChange = { examDragging = it })

            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("50%", "65%", "75%", "85%", "100%").forEach { SText(it, 10.sp, p.textMuted, weight = FontWeight.SemiBold) }
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(65 to "65% Condonation", 75 to "75% Mandatory Exam", 80 to "80% Stipend", 85 to "85% Honors").forEach { (v, label) ->
                    val sel = exam == v
                    val bg by animateColorAsState(if (sel) p.indigo else p.chipBg, label = "presetBg")
                    val fg by animateColorAsState(if (sel) Color.White else (if (p.dark) Color(0xFFCBD5E1) else Color(0xFF475569)), label = "presetFg")
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
                            .border(1.dp, if (sel) Color(0x994F46E5) else p.chipBorder, RoundedCornerShape(10.dp))
                            .tapClick { if (exam != v) { exam = v; onTick() } }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) { SText(label, 10.5.sp, fg, weight = if (sel) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // IntrinsicSize.Max + fillMaxHeight on every cell => Exam, Stipend and Buffer are always the same size
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shownExam by animateIntAsState(profile.examTarget, tween(300), label = "examShown")
            val shownStipend by animateIntAsState(profile.stipendTarget, tween(300), label = "stipendShown")
            MetricCell(p, Modifier.weight(1f).fillMaxHeight(), "Exam Target", "$shownExam%", {
                Box(Modifier.size(20.dp).clip(CircleShape).background(if (p.dark) Color(0x99022C22) else Color(0xFFD1FAE5)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = if (p.dark) Color(0xFF34D399) else Color(0xFF059669), modifier = Modifier.size(12.dp))
                }
            })
            MetricCell(p, Modifier.weight(1f).fillMaxHeight(), "Stipend Target", "$shownStipend%", {
                Box(Modifier.size(20.dp).clip(CircleShape).background(if (p.dark) Color(0x99172554) else Color(0xFFE0E7FF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, tint = if (p.dark) Color(0xFF818CF8) else Color(0xFF4F46E5), modifier = Modifier.size(11.dp))
                }
            }, onClick = onEditStipend)
            Column(Modifier.weight(1f).fillMaxHeight().insetBox(p, 14.dp).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    SText("Buffer (3%-5%)", 10.sp, p.textSecondary, Modifier.weight(1f), FontWeight.SemiBold, lineHeight = 12.sp)
                    SText("$buffer%", 12.sp, if (p.dark) Color(0xFF818CF8) else p.indigo, Modifier.padding(start = 4.dp), FontWeight.Bold)
                }
                FlowSlider(buffer, { buffer = it }, 3, 5, p, Modifier.fillMaxWidth().padding(top = 4.dp), trackHeight = 6.dp, flowing = false, onStep = onTick, onDragStateChange = { bufferDragging = it })
            }
        }

        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(p.divider))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.DateRange, if (p.dark) Color(0x99172554) else Color(0xFFEEF2FF), if (p.dark) Color(0xFF818CF8) else p.indigo, 32.dp, 17.dp, corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SText("Future Attendance Marking", 12.5.sp, p.text, weight = FontWeight.SemiBold)
                SText("Allow marking future dates", 10.5.sp, p.textSecondary)
            }
            LuxuryToggle(profile.futureMarkingEnabled, { onFutureMarking(it); onTick() }, p)
        }
    }
}

@Composable
private fun MetricCell(
    p: SettingsPalette,
    modifier: Modifier,
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier.insetBox(p, 14.dp).let { if (onClick != null) it.tapClick(onClick = onClick) else it }.padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.Top) {
            icon()
            Spacer(Modifier.width(6.dp))
            SText(label, 10.sp, p.textSecondary, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 12.sp)
        }
        SText(value, 20.sp, p.text, Modifier.padding(top = 4.dp), FontWeight.ExtraBold)
    }
}

// ───────────────────────── 3. Appearance ─────────────────────────

@Composable
fun AppearanceCard(
    p: SettingsPalette,
    profile: StudentProfile,
    onThemeMode: (ThemeMode) -> Unit,
    onStarfield: (Boolean) -> Unit,
    onTick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().glassCard(p).glowAura(if (p.dark) Color(0x338B5CF6) else Color(0x26A78BFA), atTop = true, atStart = false, radiusDp = 120f).padding(16.dp)) {
        CardHeader(
            p, Icons.Default.Palette, if (p.dark) Color(0x80581C87) else Color(0xFFFAF5FF), if (p.dark) Color(0xFFC084FC) else Color(0xFF9333EA), "Appearance & Theme",
            trailing = { TagPill("Theme & Visuals", if (p.dark) Color(0xB3172554) else Color(0xFFEEF2FF), if (p.dark) Color(0xFF818CF8) else p.indigo, if (p.dark) Color(0x99312E81) else Color(0x80C7D2FE)) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                SText("THEME SELECTOR", 10.sp, p.textSecondary, Modifier.padding(bottom = 6.dp), FontWeight.SemiBold, letterSpacing = 0.8.sp)
                val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
                SegmentedSelector(
                    listOf("☀️ Light", "🌙 Dark", "⚙️ System"),
                    modes.indexOf(profile.themeModeEnum),
                    { onThemeMode(modes[it]); onTick() },
                    p, Modifier.fillMaxWidth(), itemHeight = 44.dp, textSize = 12.sp, gap = 6.dp
                )
            }
            ToggleRow(
                p,
                leading = { IconBadge(Icons.Default.AutoAwesome, if (p.dark) Color(0xFF172554) else Color(0xFFE0E7FF), if (p.dark) p.cyanSoft else p.indigo, 24.dp, 13.dp, round = true) },
                title = "Moving Starfield",
                subtitle = "Animated nebula & drifting stars in dark mode",
                checked = profile.starfieldAnimationEnabled,
                onChange = { onStarfield(it); onTick() }
            )
        }
    }
}

// ───────────────────────── 4. Notifications ─────────────────────────

@Composable
fun NotificationsCard(
    p: SettingsPalette,
    profile: StudentProfile,
    onChange: (StudentProfile) -> Unit,
    onNeedsPermission: () -> Unit,
    onTick: () -> Unit
) {
    val ctx = LocalContext.current
    val activeCount = listOf(profile.dailyReminderEnabled, profile.weeklyReminderEnabled, profile.classScheduleAlertsEnabled).count { it }
    fun enable(update: StudentProfile, on: Boolean) { if (on) onNeedsPermission(); onChange(update); onTick() }

    Column(Modifier.fillMaxWidth().glassCard(p).padding(16.dp)) {
        CardHeader(
            p, Icons.Default.Notifications, if (p.dark) Color(0x99451A03) else Color(0xFFFFFBEB), if (p.dark) Color(0xFFFBBF24) else Color(0xFFD97706), "Notifications & Reminders",
            trailing = {
                val on = activeCount > 0
                TagPill(
                    if (on) "Active Alerts" else "Alerts Off",
                    if (on) (if (p.dark) Color(0xB3022C22) else Color(0xFFECFDF5)) else p.inset,
                    if (on) (if (p.dark) Color(0xFF34D399) else Color(0xFF059669)) else p.textSecondary,
                    if (on) (if (p.dark) Color(0x99064E3B) else Color(0x80A7F3D0)) else p.insetBorder
                )
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Daily
            ToggleRow(
                p,
                leading = { IconBadge(Icons.Default.Notifications, if (p.dark) Color(0xFF172554) else Color(0xFFDBEAFE), if (p.dark) Color(0xFF60A5FA) else Color(0xFF2563EB), 28.dp, 14.dp, round = true) },
                title = "Daily Attendance Reminder",
                subtitle = "Prompt to log daily lectures before evening",
                checked = profile.dailyReminderEnabled,
                onChange = { enable(profile.copy(dailyReminderEnabled = it), it) },
                titleSuffix = {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (p.dark) Color(0x991E3A8A) else Color(0xFFDBEAFE))
                            .tapClick {
                                val parts = profile.dailyReminderTime.split(":")
                                val h = parts.getOrNull(0)?.toIntOrNull() ?: 16
                                val m = parts.getOrNull(1)?.toIntOrNull() ?: 45
                                TimePickerDialog(ctx, { _, hh, mm ->
                                    onChange(profile.copy(dailyReminderTime = "%02d:%02d".format(hh, mm)))
                                }, h, m, false).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { SText(format12h(profile.dailyReminderTime), 10.5.sp, if (p.dark) Color(0xFF93C5FD) else Color(0xFF2563EB), weight = FontWeight.SemiBold, maxLines = 1) }
                }
            )
            // Weekly (taller variant)
            Row(Modifier.fillMaxWidth().activeInset(p, profile.weeklyReminderEnabled).padding(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Default.DateRange, if (p.dark) Color(0x99334155) else Color(0xFFEFF6FF), if (p.dark) Color(0xFF93C5FD) else Color(0xFF2563EB), 32.dp, 16.dp, round = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SText("Weekly Attendance Report", 12.sp, p.text, weight = if (profile.weeklyReminderEnabled) FontWeight.Bold else FontWeight.SemiBold, lineHeight = 17.sp)
                    SText("Comprehensive summary & semester trajectory", 10.5.sp, p.textSecondary, Modifier.padding(top = 2.dp), lineHeight = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                LuxuryToggle(profile.weeklyReminderEnabled, { enable(profile.copy(weeklyReminderEnabled = it), it) }, p, Modifier.padding(top = 2.dp))
            }
            // Class alerts
            ToggleRow(
                p,
                leading = { IconBadge(Icons.Default.Bolt, if (p.dark) Color(0xFF022C22) else Color(0xFFD1FAE5), if (p.dark) Color(0xFF34D399) else Color(0xFF059669), 28.dp, 14.dp, round = true) },
                title = "Class Schedule Alerts",
                subtitle = "Smart reminders before each scheduled lecture",
                checked = profile.classScheduleAlertsEnabled,
                onChange = { enable(profile.copy(classScheduleAlertsEnabled = it), it) },
                titleSuffix = { TagPill("10m Before", if (p.dark) Color(0x99064E3B) else Color(0xFFD1FAE5), if (p.dark) Color(0xFF6EE7B7) else Color(0xFF059669), Color.Transparent) }
            )
        }
    }
}

// ───────────────────────── 5. Haptics (widgets deferred) ─────────────────────────

@Composable
fun HapticCard(
    p: SettingsPalette,
    profile: StudentProfile,
    onChange: (StudentProfile) -> Unit,
    onPreview: (HapticIntensity) -> Unit
) {
    val levels = listOf(HapticIntensity.SOFT, HapticIntensity.MEDIUM, HapticIntensity.HARD)
    val label = when (profile.hapticIntensityEnum) {
        HapticIntensity.SOFT -> "Soft (Subtle)"
        HapticIntensity.MEDIUM -> "Medium (Standard)"
        HapticIntensity.HARD -> "Hard (Distinct)"
    }
    Column(Modifier.fillMaxWidth().glassCard(p).padding(16.dp)) {
        CardHeader(p, Icons.Default.Vibration, if (p.dark) Color(0x99083344) else Color(0xFFECFEFF), if (p.dark) Color(0xFF22D3EE) else Color(0xFF0891B2), "Haptic Feedback")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow(
                p,
                leading = { IconBadge(Icons.Default.Vibration, if (p.dark) Color(0xFF3B0764) else Color(0xFFF3E8FF), if (p.dark) Color(0xFFC084FC) else Color(0xFF9333EA), 28.dp, 14.dp, round = true) },
                title = "Haptic Feedback Master",
                subtitle = "Vibrations for taps, attendance marks & sliders",
                checked = profile.hapticFeedbackEnabled,
                onChange = { onChange(profile.copy(hapticFeedbackEnabled = it)); if (it) onPreview(profile.hapticIntensityEnum) }
            )
            val enabled = profile.hapticFeedbackEnabled
            Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.5f }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 0.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SText("VIBRATION INTENSITY", 10.sp, p.textSecondary, weight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Crossfade(label, label = "hapticLabel") { l -> SText(l, 10.sp, if (p.dark) Color(0xFF818CF8) else p.indigo, weight = FontWeight.SemiBold) }
                }
                SegmentedSelector(
                    listOf("Soft", "Medium", "Hard"),
                    levels.indexOf(profile.hapticIntensityEnum),
                    { onChange(profile.copy(hapticIntensity = levels[it].name)); onPreview(levels[it]) },
                    p, Modifier.fillMaxWidth(), itemHeight = 44.dp, textSize = 12.sp, gap = 6.dp, enabled = enabled
                )
            }
        }
    }
}

// ───────────────────────── 6. Cloud ─────────────────────────

class CloudUi(
    val email: String?,
    val statusLine: String,
    val statusOk: Boolean,
    val busyOp: String?,
    val message: Pair<Boolean, String>?,
    val isSigningIn: Boolean
)

@Composable
fun CloudCard(
    p: SettingsPalette,
    ui: CloudUi,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExport: () -> Unit
) {
    val narrow = LocalConfiguration.current.screenWidthDp < 380
    val btnText = if (narrow) 11.sp else 12.sp
    Column(Modifier.fillMaxWidth().glassCard(p).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(32.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)
                    .border(1.dp, if (p.dark) Color(0xFF334155) else Color(0xFFE2E8F0), CircleShape),
                contentAlignment = Alignment.Center
            ) { SText("G", 16.sp, Color(0xFF4285F4), weight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SText("Cloud Sync & Data Preservation", 16.sp, p.text, weight = FontWeight.Bold, maxLines = 2, lineHeight = 20.sp)
                SText(ui.email ?: "Not signed in", 11.sp, p.textSecondary, maxLines = 1)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(if (ui.statusOk) p.emerald else p.amber)
                    Spacer(Modifier.width(6.dp))
                    SText(ui.statusLine, 10.sp, if (ui.statusOk) (if (p.dark) Color(0xFF34D399) else Color(0xFF059669)) else (if (p.dark) Color(0xFFFBBF24) else Color(0xFFB45309)), maxLines = 2)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (ui.email != null) {
                SText("Sign out", 11.sp, p.rose, Modifier.tapClick(onClick = onSignOut).padding(4.dp), FontWeight.Bold)
            }
        }
        if (ui.email == null) {
            Box(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).height(48.dp)
                    .shadow(if (p.dark) 0.dp else 6.dp, RoundedCornerShape(14.dp), ambientColor = p.indigo, spotColor = p.indigo)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.accentBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .tapClick(enabled = !ui.isSigningIn, onClick = onSignIn),
                contentAlignment = Alignment.Center
            ) { SText(if (ui.isSigningIn) "Connecting Google Account..." else "Sign in with Google to Sync", 12.sp, Color.White, weight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudButton(p, Modifier.weight(1f), "Backup Now", Icons.Default.Backup, primary = true, busy = ui.busyOp == "backup", enabled = ui.busyOp == null, size = btnText, onClick = onBackup)
            CloudButton(p, Modifier.weight(1f), "Restore Data", Icons.Default.Restore, primary = false, busy = ui.busyOp == "restore", enabled = ui.busyOp == null, size = btnText, onClick = onRestore)
            CloudButton(p, Modifier.weight(1f), "Export JSON", Icons.Default.Download, primary = false, busy = ui.busyOp == "export", enabled = ui.busyOp == null, size = btnText, onClick = onExport)
        }
        AnimatedVisibility(ui.message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val m = ui.message
            if (m != null) {
                SText(
                    (if (m.first) "✓ " else "⚠ ") + m.second, 11.sp,
                    if (m.first) (if (p.dark) Color(0xFF34D399) else Color(0xFF059669)) else p.rose,
                    Modifier.padding(top = 10.dp), FontWeight.Medium, lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CloudButton(
    p: SettingsPalette,
    modifier: Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    busy: Boolean,
    enabled: Boolean,
    size: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (primary) Color.White else (if (p.dark) Color(0xFFE2E8F0) else Color(0xFF283056))
    Row(
        modifier.height(46.dp)
            .graphicsLayer { alpha = if (enabled || busy) 1f else 0.55f }
            .let { if (primary) it.shadow(6.dp, shape, ambientColor = p.indigo, spotColor = p.indigo) else it }
            .clip(shape)
            .let {
                if (primary) it.background(p.accentBrush).border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                else it.background(if (p.dark) Color(0xFF141C3F) else Color(0xFFF1F2FD)).border(1.dp, p.chipBorder, shape)
            }
            .tapClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = fg, strokeWidth = 2.dp)
        else Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        SText(label, size, fg, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ───────────────────────── 7. Data management (existing features kept) ─────────────────────────

@Composable
fun DataManagementCard(p: SettingsPalette, onReplay: () -> Unit, onReset: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().glassCard(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardHeader(p, Icons.Default.Upload, if (p.dark) Color(0x80312E81) else Color(0xFFEEF2FF), if (p.dark) Color(0xFF818CF8) else p.indigo, "Data Management")
        // Normal action — calm indigo outline
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(shape).background(p.indigo.copy(alpha = if (p.dark) 0.16f else 0.10f))
                .border(1.dp, p.indigo.copy(alpha = 0.40f), shape).tapClick(role = androidx.compose.ui.semantics.Role.Button, onClick = onReplay),
            contentAlignment = Alignment.Center
        ) { SText("Replay 5-Screen Onboarding Flow", 12.5.sp, if (p.dark) Color(0xFFB4BEFF) else p.indigo, weight = FontWeight.Bold) }
        // Destructive action — red wash, stronger border, bold label
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(shape).background(p.rose.copy(alpha = if (p.dark) 0.16f else 0.10f))
                .border(1.5.dp, p.rose.copy(alpha = 0.55f), shape).tapClick(role = androidx.compose.ui.semantics.Role.Button, onClick = onReset),
            contentAlignment = Alignment.Center
        ) { SText("Reset App Data & Replay Onboarding", 12.5.sp, if (p.dark) Color(0xFFFF8FA3) else Color(0xFFBE123C), weight = FontWeight.ExtraBold) }
    }
}
