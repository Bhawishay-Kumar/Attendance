package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.holidays.AktuHolidays
import com.example.data.holidays.Holiday
import com.example.data.model.PeriodSlot
import com.example.data.timetable.TimetableMatrix
import com.example.ui.components.DockTab
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.timetable.HolidayUi
import com.example.ui.timetable.TimetableContent
import com.example.ui.timetable.TimetableHeader
import com.example.ui.timetable.TimetableMapper
import com.example.ui.viewmodel.AttendanceViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TimetableScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val profile by viewModel.profile.collectAsState()
    val subjectSummaries by viewModel.subjectSummaries.collectAsState()

    // Default to today's weekday (Mon=0 .. Fri=4; default to Mon=0 on weekends)
    val initialDayIndex = remember {
        val dow = LocalDate.now().dayOfWeek.value // 1 = Mon .. 7 = Sun
        if (dow in 1..5) dow - 1 else 0
    }
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(initialDayIndex) }

    // Dialog states for interactive modals
    var showMatrixDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }

    // Map real BHTT 5th Sem data into UI state
    val timetableUiState = remember(profile, subjectSummaries, selectedDayIndex) {
        TimetableMapper.buildTimetableUiState(
            profile = profile,
            subjectSummaries = subjectSummaries,
            selectedDayIndex = selectedDayIndex,
            today = LocalDate.now()
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        TimetableContent(
            state = timetableUiState,
            darkTheme = glass.isDark,
            onToggleTheme = { viewModel.toggleDarkMode() },
            onSelectDay = { selectedDayIndex = it },
            onPeriodClick = { /* No-op, expansion handled internally */ },
            onViewAttendance = { viewModel.selectTab(DockTab.ATTENDANCE) },
            onHolidayClick = { showCalendarDialog = true },
            onViewMatrix = { showMatrixDialog = true },
            onOpenAcademicCalendar = { showCalendarDialog = true },
            drawOwnBackground = false,
            header = {
                TimetableHeader(
                    title = "Weekly Timetable",
                    subtitle = "BHTT 5th Sem • ${profile.batchEnum.displayName} • ${profile.courseSelectionEnum.shortTitle}",
                    darkTheme = glass.isDark,
                    onToggleTheme = { viewModel.toggleDarkMode() }
                )
            }
        )

        // 1. Full Matrix Grid Modal
        if (showMatrixDialog) {
            TimetableMatrixDialog(
                viewModel = viewModel,
                isDark = glass.isDark,
                onDismiss = { showMatrixDialog = false }
            )
        }

        // 2. Official AKTU Academic Calendar Modal
        if (showCalendarDialog) {
            AktuAcademicCalendarDialog(
                isDark = glass.isDark,
                onDismiss = { showCalendarDialog = false }
            )
        }
    }
}

/**
 * Interactive Glass Dialog showing the full Monday–Friday 5×7 timetable matrix.
 */
@Composable
private fun TimetableMatrixDialog(
    viewModel: AttendanceViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val batch = profile.batchEnum
    val selection = profile.courseSelectionEnum

    var activeTab by remember { mutableIntStateOf(1) } // 1..5
    val days = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri")

    val bgBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF040713)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF8FAFC), Color(0xFFEDF2F7)))
    }
    val borderColor = if (isDark) Color(0x3338BDF8) else Color(0x330284C7)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val mutedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {} // Catch click
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(bgBrush).padding(18.dp)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.GridView,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Timetable Matrix",
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "BHTT 5th Sem • ${batch.displayName} • ${selection.shortTitle}",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 11.sp,
                                        color = mutedColor
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = mutedColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Day selector pill tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0x331E293B) else Color(0x33E2E8F0))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            days.forEach { (d, label) ->
                                val active = d == activeTab
                                val tabModifier = if (active) {
                                    Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF06B6D4))))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(tabModifier)
                                        .clickable { activeTab = d }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) Color.White else mutedColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Periods list for active day
                        val slots = remember(activeTab, batch, selection) {
                            TimetableMatrix.getScheduleForDay(activeTab, batch, selection)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(slots) { slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0x661E293B) else Color(0x66F1F5F9))
                                        .border(
                                            1.dp,
                                            if (slot.isTwoHourBlock) Color(0xFF06B6D4).copy(alpha = 0.5f)
                                            else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (slot.isTwoHourBlock) "Period ${slot.periodNumber} & ${slot.periodNumber + 1}" else "Period ${slot.periodNumber}",
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                            if (slot.isTwoHourBlock) {
                                                Text(
                                                    text = "2 HRS",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF0284C7))
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    val extras = remember(slot) { com.example.ui.timetable.resolveBhttSlotExtras(slot) }
                                    Text(
                                        text = slot.subject.fullName,
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    val teacherLine = extras.faculty?.let { " • $it" } ?: ""
                                    Text(
                                        text = "${slot.subject.code} • ${slot.timeRange}$teacherLine",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 11.sp,
                                        color = mutedColor
                                    )
                                    if (extras.venue != null) {
                                        Text(
                                            text = extras.venue,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color(0xFF67E8F9) else Color(0xFF0284C7),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Glass Dialog showing all official AKTU 2026 Academic Calendar holidays.
 */
@Composable
fun AktuAcademicCalendarDialog(
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val holidays = AktuHolidays.OFFICIAL_HOLIDAYS_2026
    val today = LocalDate.now()

    val bgBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF040713)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF8FAFC), Color(0xFFEDF2F7)))
    }
    val borderColor = if (isDark) Color(0x33F59E0B) else Color(0x33D97706)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val mutedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {}
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(bgBrush).padding(18.dp)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Academic Calendar 2026",
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "Official AKTU List of Holidays",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 11.sp,
                                        color = mutedColor
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = mutedColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(holidays) { h ->
                                val hDate = runCatching { LocalDate.parse(h.date) }.getOrNull()
                                val isPassed = hDate?.isBefore(today) == true
                                val daysDiff = if (hDate != null && !isPassed) {
                                    ChronoUnit.DAYS.between(today, hDate)
                                } else null

                                val countdown = when {
                                    isPassed -> "Passed"
                                    daysDiff == 0L -> "Today"
                                    daysDiff == 1L -> "Tomorrow"
                                    daysDiff != null -> "In $daysDiff days"
                                    else -> ""
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isPassed) (if (isDark) Color(0x221E293B) else Color(0x22F1F5F9))
                                            else (if (isDark) Color(0x661E293B) else Color(0x66FFFFFF))
                                        )
                                        .border(
                                            1.dp,
                                            if (!isPassed && h.isActiveSemesterHoliday) Color(0xFFF59E0B).copy(alpha = 0.4f)
                                            else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = h.name,
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPassed) mutedColor else textColor
                                            )
                                            if (h.isActiveSemesterHoliday) {
                                                Text(
                                                    text = "5th Sem",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF59E0B),
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${h.date} • ${h.dayOfWeek}",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 11.sp,
                                            color = mutedColor,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    if (countdown.isNotEmpty()) {
                                        Text(
                                            text = countdown,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPassed) mutedColor else Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
