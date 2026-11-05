package com.example.ui.screens

import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.model.SubjectCategory
import com.example.data.holidays.AktuHolidays
import com.example.ui.components.AppScreenHeader
import com.example.ui.components.AttendanceDateStrip
import com.example.ui.components.DayAttendanceStatus
import com.example.ui.components.BulkAttendanceCard
import com.example.ui.components.ClassSessionCard
import java.time.DayOfWeek
import java.time.LocalDate
import com.example.ui.components.FloatingAttendanceToast
import com.example.ui.components.MarkState
import com.example.ui.components.MidMonthBackfillModal
import com.example.ui.components.NoClassesTodayCard
import com.example.ui.components.RedesignBulkAttendanceCard
import com.example.ui.components.RedesignSubjectAttendanceCard
import com.example.ui.components.ScenicSkyToggle
import com.example.ui.components.ShakeBox
import com.example.ui.components.SubjectIcon
import com.example.ui.components.rememberLiveToday
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.components.DockTab
import com.example.ui.settings.GlobalProfileAvatar
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val profile by viewModel.profile.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val slots by viewModel.currentScheduleSlots.collectAsState()
    val periodStatuses by viewModel.currentPeriodStatuses.collectAsState()
    val isHoliday by viewModel.isCurrentDateHoliday.collectAsState()
    val holidayName by viewModel.currentHolidayName.collectAsState()
    val activeFilter by viewModel.activeCategoryFilter.collectAsState()
    val recordsMap by viewModel.repository.recordsMap.collectAsState()
    val holidayOverrides by viewModel.repository.holidayOverrides.collectAsState()
    val monthlyAnalytics by viewModel.monthlyAnalytics.collectAsState()
    val showBackfillModal by viewModel.showMidMonthBackfillModal.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var shakeTriggerKey by remember { mutableIntStateOf(0) }

    // Live IST today and parsed date (never stuck on Oct 30)
    val liveToday = rememberLiveToday()
    val parsedDate = remember(selectedDate, liveToday) {
        runCatching { LocalDate.parse(selectedDate) }.getOrElse { liveToday }
    }
    val isFutureDate = parsedDate.isAfter(liveToday)
    val isWeekend = parsedDate.dayOfWeek == DayOfWeek.SATURDAY || parsedDate.dayOfWeek == DayOfWeek.SUNDAY
    val isMarkingAllowed = !isFutureDate || profile.futureMarkingEnabled
    val isNoClasses = isHoliday || isWeekend || slots.isEmpty()

    val filteredSlots = slots.filter { slot ->
        when (activeFilter) {
            SubjectCategory.ALL -> true
            SubjectCategory.MAJOR -> slot.subject.category == SubjectCategory.MAJOR
            SubjectCategory.MINOR -> slot.subject.category == SubjectCategory.MINOR
            SubjectCategory.LIBRARY -> slot.subject.isAutoPresent || slot.subject.category == SubjectCategory.LIBRARY
        }
    }

    // Tally metrics for today
    val totalSlots = slots.size
    val recordedCount = slots.count { slot ->
        val status = periodStatuses[slot.periodNumber]
        status != null && status != PeriodStatus.NO_CLASS && status != PeriodStatus.PENDING
    }
    val presentCreditsCount = slots.sumOf { slot ->
        val status = periodStatuses[slot.periodNumber] ?: if (slot.subject.isAutoPresent) PeriodStatus.AUTO_PRESENT else PeriodStatus.PENDING
        if (status == PeriodStatus.PRESENT || status == PeriodStatus.AUTO_PRESENT) slot.subject.creditHours else 0
    }
    val totalConductedCredits = slots.sumOf { slot ->
        val status = periodStatuses[slot.periodNumber] ?: if (slot.subject.isAutoPresent) PeriodStatus.AUTO_PRESENT else PeriodStatus.PENDING
        if (status == PeriodStatus.PRESENT || status == PeriodStatus.ABSENT || status == PeriodStatus.AUTO_PRESENT) slot.subject.creditHours else 0
    }
    val autoCreditedCount = slots.count { it.subject.isAutoPresent }

    val dateStatusResolver = remember(recordsMap, holidayOverrides) {
        { d: LocalDate ->
            val dStr = d.toString()
            val isHol = AktuHolidays.isHoliday(dStr) || (holidayOverrides[dStr] == true)
            if (isHol) {
                DayAttendanceStatus.HOLIDAY
            } else {
                val recs = recordsMap[dStr]
                if (recs != null && recs.isNotEmpty()) {
                    val conducted = recs.filter { it.value != PeriodStatus.NO_CLASS }
                    if (conducted.isNotEmpty() && conducted.values.all { it == PeriodStatus.PRESENT || it == PeriodStatus.AUTO_PRESENT }) {
                        DayAttendanceStatus.ALL_PRESENT
                    } else if (conducted.values.any { it == PeriodStatus.ABSENT }) {
                        DayAttendanceStatus.ABSENT_OR_MIXED
                    } else {
                        DayAttendanceStatus.UNMARKED
                    }
                } else {
                    DayAttendanceStatus.UNMARKED
                }
            }
        }
    }

    val isLockedDate = remember(profile.futureMarkingEnabled, liveToday) {
        { d: LocalDate -> !profile.futureMarkingEnabled && d.isAfter(liveToday) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val entrance = rememberEntranceTracker()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SECTION 1: Shared screen header (same component on Attendance / Timetable / Reports / Settings)
            item {
                AppScreenHeader(
                    title = "Mark Attendance",
                    subtitle = "BHTT 5th Sem \u2022 ${profile.batchEnum.displayName}",
                    isDark = glass.isDark,
                    onToggleTheme = { viewModel.toggleDarkMode() },
                    modifier = Modifier.padding(horizontal = 6.dp) // list has 14dp -> title sits 20dp from the edge
                )
            }

            // SECTION 2: Horizontally Scrollable Date Strip (Past to Future)
            item {
 Box(Modifier.loadIn(1, entrance)) {
                AttendanceDateStrip(
                    selectedDate = parsedDate,
                    onDateSelected = { newDate ->
                        viewModel.selectDate(newDate)
                    },
                    today = liveToday,
                    dateStatusResolver = dateStatusResolver,
                    isLocked = isLockedDate,
                    onLockedClick = {
                        shakeTriggerKey++
                        viewModel.showToast(
                            "🔒 Future dates are locked. Enable Future Marking in Settings to open",
                            Color(0xFFEF4444),
                            "🔒"
                        )
                    },
                    darkTheme = glass.isDark
                )
            
 }
}

            // SECTION 3: Future Attendance Marking Card with Horizontal Lock Shake
            item {
 Box(Modifier.loadIn(2, entrance)) {
                ShakeBox(trigger = shakeTriggerKey) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Future Attendance Marking",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.textPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = glass.textMuted,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            viewModel.showToast(
                                                "Allows marking attendance in advance for upcoming dates",
                                                glass.royalIndigo,
                                                "ℹ️"
                                            )
                                        }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = profile.futureMarkingEnabled,
                                    onCheckedChange = { viewModel.toggleFutureMarking(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF6366F1),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFF64748B).copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("future_marking_toggle")
                                )

                                Text(
                                    text = if (profile.futureMarkingEnabled) "ON" else "OFF",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (profile.futureMarkingEnabled) Color(0xFF6366F1) else glass.textMuted,
                                    modifier = Modifier.width(26.dp)
                                )
                            }
                        }

                        // Future Locked / Unlocked Banner
                        if (isFutureDate) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!profile.futureMarkingEnabled) {
                                ShakeBox(trigger = shakeTriggerKey) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Future Date Locked • Turn ON switch above to pre-mark",
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF6366F1).copy(alpha = 0.14f))
                                        .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "⚡ Future Pre-Marking Active • Changes count once date arrives",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (glass.isDark) Color(0xFFC7D2FE) else Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        
 }
}

        // SECTION 4: Bulk Attendance Actions Card (Redesigned with Zap icon, progress bar, 3-col actions)
        if (!isNoClasses) {
            item {
 Box(Modifier.loadIn(3, entrance)) {
                RedesignBulkAttendanceCard(
                    markedCount = recordedCount,
                    totalCount = totalSlots,
                    isDark = glass.isDark,
                    isLockedFutureDate = !isMarkingAllowed,
                    onMarkAllPresent = {
                        if (!isMarkingAllowed) {
                            shakeTriggerKey++
                            viewModel.showToast("Enable Future Marking to pre-mark upcoming days", Color(0xFFEF4444), "🔒")
                        } else {
                            viewModel.markAllPresent()
                        }
                    },
                    onMarkAllAbsent = {
                        if (!isMarkingAllowed) {
                            shakeTriggerKey++
                            viewModel.showToast("Enable Future Marking to pre-mark upcoming days", Color(0xFFEF4444), "🔒")
                        } else {
                            viewModel.markAllAbsent()
                        }
                    },
                    onResetDay = {
                        if (!isMarkingAllowed) {
                            shakeTriggerKey++
                            viewModel.showToast("Enable Future Marking to pre-mark upcoming days", Color(0xFFEF4444), "🔒")
                        } else {
                            viewModel.markAllCancelled()
                        }
                    }
                )
            
 }
}
        }

            // SECTION 5: Holiday / No Classes Today Strip
            item {
 Box(Modifier.loadIn(4, entrance)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🏖️", fontSize = 18.sp)
                            }

                            Column {
                                Text(
                                    text = "Holiday / No Classes Today",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.textPrimary
                                )
                                Text(
                                    text = if (isHoliday && holidayName.isNotEmpty()) {
                                        holidayName
                                    } else {
                                        "Auto-enabled on AKTU holidays. Freezes all 7 periods (0 counted)."
                                    },
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 10.sp,
                                    color = if (isHoliday) Color(0xFFF59E0B) else glass.textMuted,
                                    lineHeight = 13.sp
                                )
                            }
                        }

                        Switch(
                            checked = isHoliday,
                            onCheckedChange = { viewModel.toggleHoliday(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFF59E0B)
                            ),
                            modifier = Modifier.testTag("holiday_toggle")
                        )
                    }
                }
            
 }
}

            // SECTION 6: Category Filter Chips
            if (!isNoClasses) {
                item {
 Box(Modifier.loadIn(5, entrance)) {
                    com.example.ui.components.CategoryFilterChips(
                        activeFilter = activeFilter,
                        onFilterSelected = { viewModel.setCategoryFilter(it) }
                    )
                
 }
}
            }

            // SECTION 7: Chronological 7-Period List with Timeline Dividers
            if (isNoClasses) {
                item {
 Box(Modifier.loadIn(6, entrance)) {
                    NoClassesTodayCard(
                        date = parsedDate,
                        holidayName = if (isHoliday) holidayName.ifEmpty { "AKTU College Holiday" } else null,
                        isDark = glass.isDark,
                        onJumpToToday = if (parsedDate != liveToday) {
                            { viewModel.selectDate(liveToday) }
                        } else null
                    )
                
 }
}
            } else {
                items(filteredSlots, key = { it.periodNumber }) { slot ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .loadIn(filteredSlots.indexOf(slot) + 5, entrance, key = 100 + slot.periodNumber),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    val status = periodStatuses[slot.periodNumber]
                        ?: if (slot.subject.isAutoPresent) PeriodStatus.AUTO_PRESENT else PeriodStatus.PENDING

                    val markState = when {
                        slot.subject.isAutoPresent -> MarkState.AUTO_PRESENT
                        status == PeriodStatus.PRESENT -> MarkState.PRESENT
                        status == PeriodStatus.ABSENT -> MarkState.ABSENT
                        else -> MarkState.NONE
                    }

                    val periodLabel = if (slot.isTwoHourBlock) {
                        val nextPeriod = slot.periodNumber + 1
                        val r1 = toRoman(slot.periodNumber)
                        val r2 = toRoman(nextPeriod)
                        "$r1–$r2 (2x)"
                    } else {
                        toRoman(slot.periodNumber)
                    }

                    val subjectType = if (slot.isTwoHourBlock) {
                        "2 HRS • 2X CREDIT LAB"
                    } else if (slot.subject.isAutoPresent) {
                        "AUTO-CREDIT • 1 CR"
                    } else {
                        "THEORY LECTURE • 1 CR"
                    }

                    val accentColor = when (slot.subject.iconKey) {
                        "flask" -> Color(0xFF8B5CF6)
                        "microscope" -> Color(0xFFEC4899)
                        "grid" -> Color(0xFF3B82F6)
                        "scissors" -> Color(0xFFF59E0B)
                        "workflow" -> Color(0xFF10B981)
                        "book", "library" -> Color(0xFF06B6D4)
                        "presentation", "seminar" -> Color(0xFF14B8A6)
                        else -> Color(0xFF6366F1)
                    }

                    RedesignSubjectAttendanceCard(
                        periodLabel = periodLabel,
                        timeRange = slot.timeRange,
                        subjectTitle = slot.subject.fullName,
                        subjectCode = slot.subject.code,
                        subjectType = subjectType,
                        accentColor = accentColor,
                        markState = markState,
                        isDark = glass.isDark,
                        isLockedFutureDate = !isMarkingAllowed,
                        subjectIcon = { tint ->
                            SubjectIcon(iconKey = slot.subject.iconKey, tint = tint)
                        },
                        onMarkPresent = {
                            if (!isMarkingAllowed) {
                                shakeTriggerKey++
                                viewModel.showToast("Enable Future Marking to pre-mark upcoming days", Color(0xFFEF4444), "🔒")
                            } else {
                                viewModel.markPeriodStatus(slot.periodNumber, PeriodStatus.PRESENT)
                                viewModel.showToast(
                                    "✓ ${slot.subject.shortName} Marked Present (+${slot.subject.creditHours} Cr)",
                                    Color(0xFF10B981),
                                    "✓"
                                )
                            }
                        },
                        onMarkAbsent = {
                            if (!isMarkingAllowed) {
                                shakeTriggerKey++
                                viewModel.showToast("Enable Future Marking to pre-mark upcoming days", Color(0xFFEF4444), "🔒")
                            } else {
                                viewModel.markPeriodStatus(slot.periodNumber, PeriodStatus.ABSENT)
                                viewModel.showToast(
                                    "✕ ${slot.subject.shortName} Marked Absent",
                                    Color(0xFFEF4444),
                                    "✕"
                                )
                            }
                        },
                        onUndoMark = {
                            if (!isMarkingAllowed) {
                                shakeTriggerKey++
                                viewModel.showToast("Enable Future Marking to pre-mark upcoming days", Color(0xFFEF4444), "🔒")
                            } else {
                                viewModel.markPeriodStatus(slot.periodNumber, PeriodStatus.PENDING)
                                viewModel.showToast(
                                    "↺ ${slot.subject.shortName} Attendance Reset",
                                    Color(0xFF64748B),
                                    "↺"
                                )
                            }
                        }
                    )

                    // Timeline Divider for Water Break 1 after P2
                    if (slot.periodNumber == 2) {
                        AttendanceTimelineBreak(
                            emoji = "💧",
                            title = "Water Break (10m)",
                            time = "11:10 AM – 11:20 AM",
                            color = Color(0xFF38BDF8)
                        )
                    }

                    // Timeline Divider for Lunch Break after P4 (or P3_P4 block)
                    if (slot.periodNumber == 4 || (slot.isTwoHourBlock && slot.periodNumber == 3)) {
                        AttendanceTimelineBreak(
                            emoji = "🍱",
                            title = "Lunch Break (1 Hr)",
                            time = "01:00 PM – 02:00 PM",
                            color = Color(0xFFF59E0B)
                        )
                    }

                    // Timeline Divider for Water Break 2 after P6
                    if (slot.periodNumber == 6 && !slot.isTwoHourBlock) {
                        AttendanceTimelineBreak(
                            emoji = "💧",
                            title = "Water Break (10m)",
                            time = "03:40 PM – 03:50 PM",
                            color = Color(0xFF38BDF8)
                        )
                    }
                    }
                }
            }

            // SECTION 8: Live Daily Summary Strip (Matching Stitch Bottom Live Card)
            item {
 Box(Modifier.loadIn(7, entrance)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                        .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = glass.royalIndigo.copy(alpha = 0.3f))
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(glass.royalIndigo.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📊", fontSize = 16.sp)
                            }

                            Column {
                                Text(
                                    text = "Today's Tally: $presentCreditsCount / $totalConductedCredits Hrs Present",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.textPrimary
                                )
                                Text(
                                    text = "(${slots.count { periodStatuses[it.periodNumber] == PeriodStatus.NO_CLASS }} Cancelled • $autoCreditedCount Auto-Credited)",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 10.sp,
                                    color = glass.textMuted
                                )
                            }
                        }

                        // Right: Monthly Percentage Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(glass.royalIndigo.copy(alpha = 0.16f))
                                .border(1.dp, glass.royalIndigo.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", monthlyAnalytics.percentage)}%",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (glass.isDark) Color(0xFFC7D2FE) else glass.royalIndigo
                            )
                        }
                    }
                }
            
 }
}

            item {
                Spacer(modifier = Modifier.height(150.dp)) // Floating dock padding (same as Reports / Settings)
            }
        }

        // Floating Confirmation Toast Banner
        FloatingAttendanceToast(
            toast = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )

        // Mid-Month Backfill Modal
        if (showBackfillModal) {
            MidMonthBackfillModal(
                onDismiss = { viewModel.showMidMonthBackfill(false) },
                onConfirm = { from, to, markPresent ->
                    viewModel.performBackfill(from, to, markPresent)
                    viewModel.showToast(
                        if (markPresent) "✓ Backfilled Past Days as Present" else "✕ Backfilled Past Days as Absent",
                        if (markPresent) Color(0xFF10B981) else Color(0xFFEF4444),
                        if (markPresent) "✓" else "✕"
                    )
                }
            )
        }
    }
}

@Composable
private fun AttendanceTimelineBreak(
    emoji: String,
    title: String,
    time: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, color.copy(alpha = 0.4f))
                    )
                )
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = emoji, fontSize = 11.sp)
                Text(
                    text = "$time • $title",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
    }
}

private fun toRoman(number: Int): String = when (number) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    6 -> "VI"
    7 -> "VII"
    8 -> "VIII"
    else -> number.toString()
}
