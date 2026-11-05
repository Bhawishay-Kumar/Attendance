package com.example.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MonthlyAnalytics
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.data.model.TierStatus
import com.example.ui.components.DockTab
import com.example.ui.components.FloatingAttendanceToast
import com.example.ui.components.ScenicSkyToggle
import com.example.ui.components.QuickStatCard
import com.example.ui.components.TodaysClassesSnapshotSection
import com.example.ui.components.attendanceShine
import com.example.ui.components.UpgradedHeroCardContainer
import com.example.ui.components.UpgradedHeroTopSection
import com.example.ui.components.UpgradedHeroBottomTargetBar
import com.example.ui.countdown.CountdownSection
import com.example.ui.theme.CaveatFontFamily
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.settings.GlobalProfileAvatar
import com.example.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val profile by viewModel.profile.collectAsState()
    val analytics by viewModel.monthlyAnalytics.collectAsState()
    val slots by viewModel.currentScheduleSlots.collectAsState()
    val statuses by viewModel.currentPeriodStatuses.collectAsState()
    val isHoliday by viewModel.isCurrentDateHoliday.collectAsState()
    val holidayName by viewModel.currentHolidayName.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val sessionStats by viewModel.heroSessionStats.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showNotificationSheet by remember { mutableStateOf(false) }
    // Badge shows how many alerts are waiting; it clears once the sheet has been opened
    var notificationsSeen by remember { mutableStateOf(false) }
    val notificationAlerts = listOf(
        Triple(
            "Weekly Attendance Report",
            "Your weekly summary is now available with verified weighted credits.",
            glass.royalIndigo
        ),
        Triple(
            "Daily Marking Reminder",
            "Mark your afternoon lab attendance (Quality / CPT-II Lab).",
            glass.amberGold
        ),
        Triple(
            "Holiday Notice",
            "AKTU 2026 Academic Calendar verified. Auto-Present granted on working days.",
            glass.crimsonRose
        )
    )

    // Waving hand animation trigger & state
    val infiniteTransition = rememberInfiniteTransition(label = "ambientWave")
    val ambientWaveAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientWaveAngle"
    )

    var tapWaveCount by remember { mutableStateOf(0) }
    val tapWaveAnim = remember { Animatable(0f) }

    val currentDow = remember(selectedDate) {
        runCatching { LocalDate.parse(selectedDate).dayOfWeek }.getOrDefault(DayOfWeek.MONDAY)
    }
    val isWeekend = currentDow == DayOfWeek.SATURDAY || currentDow == DayOfWeek.SUNDAY

    // Ticking clock (30 s) so "Next class" updates on its own while the screen stays open
    var nowTick by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowTick = LocalTime.now()
        }
    }
    val now = nowTick
    val isTodaySelected = selectedDate == LocalDate.now().toString()

    // Active / next period: first class that has not finished yet. When every class is over this is null, so the
    // card says "Concluded" (it used to fall back to the FIRST class of the day and show it as "next").
    val activeSlot = remember(slots, now, isTodaySelected) {
        if (!isTodaySelected) {
            slots.firstOrNull()
        } else {
            val timed = slots.mapNotNull { slot -> parseSlotClock(slot.endTime)?.let { slot to it } }
            if (timed.isEmpty()) slots.firstOrNull() else timed.firstOrNull { now.isBefore(it.second) }?.first
        }
    }
    val activeStart = activeSlot?.let { parseSlotClock(it.startTime) }
    val activeEnd = activeSlot?.let { parseSlotClock(it.endTime) }
    val classOngoing = isTodaySelected && activeStart != null && activeEnd != null &&
        !now.isBefore(activeStart) && now.isBefore(activeEnd)
    val classPhaseLabel = when {
        activeSlot == null -> "ALL DONE"
        classOngoing -> "ONGOING NOW"
        else -> "NEXT CLASS"
    }
    val classStatusText: String? = when {
        activeSlot == null -> null
        !isTodaySelected || activeStart == null || activeEnd == null -> null
        classOngoing -> "${java.time.Duration.between(now, activeEnd).toMinutes().coerceAtLeast(1)} min left"
        else -> java.time.Duration.between(now, activeStart).toMinutes().let { m ->
            if (m >= 60) "Starts in ${m / 60}h ${m % 60}m" else "Starts in ${m.coerceAtLeast(1)} min"
        }
    }
    val classProgress = if (classOngoing && activeStart != null && activeEnd != null) {
        val total = java.time.Duration.between(activeStart, activeEnd).seconds.coerceAtLeast(1)
        (java.time.Duration.between(activeStart, now).seconds.toFloat() / total).coerceIn(0f, 1f)
    } else 0f

    // Live counts for Today
    val todayPresent = slots.count { slot ->
        val st = statuses[slot.periodNumber]
        st == PeriodStatus.PRESENT || (slot.subject.isAutoPresent && st != PeriodStatus.ABSENT)
    }
    val todayAbsent = slots.count { slot ->
        statuses[slot.periodNumber] == PeriodStatus.ABSENT
    }
    val todayAuto = slots.count { it.subject.isAutoPresent }
    val todayCancelled = slots.count { slot ->
        statuses[slot.periodNumber] == PeriodStatus.NO_CLASS
    }

    val entrance = rememberEntranceTracker()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // SECTION 1: COMPACT USER HEADER (SectionOne_CompactHeader)
            // =========================================================================
            item(key = "compact_user_header") {
                Row(
                    modifier = Modifier
                        // Same top offset as every other tab's AppScreenHeader: status bar + 14dp (first list item, no spacer)
                        .statusBarsPadding()
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp) // list has 16dp -> header sits 20dp from the edge, like every other tab
                        .heightIn(min = 50.dp) // matches AppScreenHeader's row height so the vertical centre lines up
                        .testTag("compact_user_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Avatar + Cursive Greeting + Pill Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Universal Profile Avatar (Custom Photo if uploaded, or theme-aware BK Monogram Preset)
                        Box(
                            modifier = Modifier.clickable {
                                viewModel.selectTab(DockTab.SETTINGS)
                            }
                        ) {
                            GlobalProfileAvatar(
                                photoPath = profile.photoPath,
                                isDark = glass.isDark,
                                size = 44.dp
                            )
                        }

                        Column {
                            val firstName = profile.name.trim().split(" ").firstOrNull()?.ifEmpty { "Student" } ?: "Student"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Hi, ",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = glass.textPrimary
                                )
                                Text(
                                    text = firstName,
                                    fontFamily = CaveatFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 23.sp,
                                    color = if (glass.isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
                                )
                                // Interactive Waving Hand Emoji
                                Text(
                                    text = "👋",
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .graphicsLayer { rotationZ = if (tapWaveAnim.value != 0f) tapWaveAnim.value else ambientWaveAngle } // draw-phase read: no per-frame recomposition
                                        .clickable {
                                            tapWaveCount++
                                            coroutineScope.launch {
                                                tapWaveAnim.snapTo(0f)
                                                tapWaveAnim.animateTo(
                                                    targetValue = 28f,
                                                    animationSpec = spring(dampingRatio = 0.35f, stiffness = 800f)
                                                )
                                                tapWaveAnim.animateTo(0f, tween(300))
                                            }
                                        }
                                        .testTag("waving_hand_emoji")
                                )
                            }

                            // Subtitle Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(
                                        if (glass.isDark) Color(0xFF1E1B4B).copy(alpha = 0.7f)
                                        else Color(0xFFE0E7FF).copy(alpha = 0.8f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                val rawBatch = profile.batchEnum.displayName
                                val cleanBatch = rawBatch
                                    .replace("BATCH_", "", ignoreCase = true)
                                    .replace("Batch", "", ignoreCase = true)
                                    .trim()
                                Text(
                                    text = "BHTT 5th Sem • Batch $cleanBatch",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (glass.isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA)
                                )
                            }
                        }
                    }

                    // Right: Notification Bell Button (with pulse counter) + Day/Night Pill Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Notification Bell: 36dp glass circle inside a 40dp box so the count badge is NOT clipped by the circle
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(glass.cardSurface)
                                    .border(1.dp, glass.cardBorder, CircleShape)
                                    .clickable { showNotificationSheet = true; notificationsSeen = true }
                                    .testTag("notification_bell_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = glass.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val unread = if (notificationsSeen) 0 else notificationAlerts.size
                            if (unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48))))
                                        .border(1.5.dp, if (glass.isDark) Color(0xFF0B1020) else com.example.ui.theme.LightPearl, CircleShape)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (unread > 9) "9+" else unread.toString(),
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Animated Day/Night Pill Switch (68px x 36px)
                        ScenicSkyToggle(
                            isDarkMode = glass.isDark,
                            onToggle = { viewModel.toggleDarkMode() }
                        )
                    }
                }
            }

            // =========================================================================
            // SECTION 2: ATTENDANCE SAFETY BADGE PILL (SectionTwo_AttendanceSafetyBadge)
            // =========================================================================
            item(key = "safety_badge") {
 Column(Modifier.loadIn(1, entrance)) {
                SectionTwo_AttendanceSafetyBadge(
                    tierStatus = analytics.tierStatus,
                    conducted = analytics.totalConductedCredits,
                    target = profile.examTarget,
                    buffer = profile.safetyBuffer
                )
            
 }
}

            // =========================================================================
            // SECTION 3: ATMOSPHERIC MULTI-LAYERED MOUNTAIN LANDSCAPE BANNER
            // =========================================================================
            item(key = "atmospheric_banner") {
 Column(Modifier.loadIn(2, entrance)) {
                AtmosphericBanner(
                    percentage = analytics.percentage,
                    hasConducted = analytics.totalConductedCredits > 0
                )
            
 }
}

            // =========================================================================
            // SECTION 4: HERO MONTHLY ATTENDANCE CARD (SectionThree_HeroAttendanceCard)
            // =========================================================================
            item(key = "hero_attendance_card") {
 Column(Modifier.loadIn(3, entrance)) {
                SectionThree_HeroAttendanceCard(
                    analytics = analytics,
                    profileExamTarget = profile.examTarget,
                    profileStipendTarget = profile.stipendTarget,
                    sessionStats = sessionStats
                )
            
 }
}

            // =========================================================================
            // SECTION 5: FLOATING RECOVERY STATUS CARD (SectionFour_RecoveryStatusCard)
            // =========================================================================
            item(key = "recovery_status_card") {
 Column(Modifier.loadIn(4, entrance)) {
                SectionFour_RecoveryStatusCard(
                    analytics = analytics,
                    stipendTarget = profile.stipendTarget,
                    onOpenReports = { viewModel.selectTab(DockTab.REPORTS) }
                )
            
 }
}

            // =========================================================================
            // SECTION 6: QUICK ACTIVITIES (3 VIVID CARDS)
            // =========================================================================
            item(key = "quick_activities") {
 Column(Modifier.loadIn(5, entrance)) {
                SectionFive_QuickActivities(
                    onWhatIfClicked = { viewModel.whatIf.open() },
                    onPdfClicked = { viewModel.showPdfPreview(true, "SUMMARY") },
                    onBunkPlannerClicked = { viewModel.openBunkPlanner() }
                )
            
 }
}

            // =========================================================================
            // SECTION 7: TODAY'S CLASSES SNAPSHOT (2 SIDE-BY-SIDE CARDS)
            // =========================================================================
            item(key = "todays_classes_snapshot") {
 Column(Modifier.loadIn(6, entrance)) {
                SectionSix_TodaysClassesSnapshot(
                    activeSlot = activeSlot,
                    todayPresent = todayPresent,
                    todayTotal = slots.size,
                    todayAuto = todayAuto,
                    phaseLabel = classPhaseLabel,
                    statusText = classStatusText,
                    classProgress = classProgress
                )
            
 }
}

            // =========================================================================
            // SECTION 8: TODAY'S CLASSES (ALL) INTERACTIVE LIST + BULK BAR
            // =========================================================================
            item(key = "todays_classes_list") {
 Column(Modifier.loadIn(7, entrance)) {
                SectionSeven_TodaysClassesListAndInteractiveControls(
                    slots = slots,
                    statuses = statuses,
                    isHoliday = isHoliday,
                    holidayName = holidayName,
                    isWeekend = isWeekend,
                    onMarkPeriod = { pNum, st ->
                        viewModel.markPeriodStatus(pNum, st)
                        val slot = slots.firstOrNull { it.periodNumber == pNum }
                        val name = slot?.subject?.shortName ?: "Period $pNum"
                        val cr = slot?.subject?.creditHours ?: 1
                        viewModel.showToast(
                            when (st) {
                                PeriodStatus.PRESENT -> "✓ $name Marked Present (+$cr Cr)"
                                PeriodStatus.ABSENT -> "✕ $name Marked Absent"
                                PeriodStatus.NO_CLASS -> "⊘ $name Marked No Class"
                                PeriodStatus.AUTO_PRESENT -> "🔒 $name Auto-Present"
                                PeriodStatus.PENDING -> "⏳ $name Reset to Pending"
                            },
                            when (st) {
                                PeriodStatus.PRESENT -> Color(0xFF10B981)
                                PeriodStatus.ABSENT -> Color(0xFFEF4444)
                                PeriodStatus.NO_CLASS -> Color(0xFF64748B)
                                PeriodStatus.AUTO_PRESENT -> Color(0xFF06B6D4)
                                PeriodStatus.PENDING -> Color(0xFF94A3B8)
                            },
                            when (st) {
                                PeriodStatus.PRESENT -> "✓"
                                PeriodStatus.ABSENT -> "✕"
                                PeriodStatus.NO_CLASS -> "⊘"
                                PeriodStatus.AUTO_PRESENT -> "🔒"
                                PeriodStatus.PENDING -> "⏳"
                            }
                        )
                    },
                    onBulkPresent = { viewModel.markAllPresent() },
                    onBulkAbsent = { viewModel.markAllAbsent() },
                    onBulkCancelled = { viewModel.markAllCancelled() }
                )
            
 }
}

            // =========================================================================
            // SECTION 9: QUICK STATS (4-COL), NOTIFICATIONS & MOTIVATIONAL FOOTER
            // =========================================================================
            item(key = "quick_stats_and_milestones") {
 Column(Modifier.loadIn(8, entrance)) {
                QuickStatsAndNotifications(
                    todayPresent = todayPresent,
                    todayAbsent = todayAbsent,
                    todayLibrary = todayAuto,
                    todayCancelled = todayCancelled,
                    onViewAllNotifications = { showNotificationSheet = true },
                    onOpenPdfReport = { viewModel.showPdfPreview(true, "WEEKLY_SUMMARY") },
                    dateOfBirth = profile.dateOfBirth,
                    onOpenProfile = { viewModel.selectTab(DockTab.SETTINGS) },
                    onMilestoneMessage = { msg -> viewModel.showToast(msg, Color(0xFF6366F1), "⏳") }
                )
            
 }
}

            item(key = "bottom_dock_spacer") {
                Spacer(modifier = Modifier.height(150.dp)) // Padding for floating bottom dock (same as Reports / Settings)
            }
        }

        // Smart Notification Bottom Sheet
        if (showNotificationSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showNotificationSheet = false },
                sheetState = sheetState,
                containerColor = if (glass.isDark) Color(0xFF0F172A) else com.example.ui.theme.LightPearl,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = glass.royalIndigo,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Recent Notifications",
                                fontFamily = OutfitFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = glass.textPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(glass.cardBorder.copy(alpha = 0.3f))
                                .clickable { showNotificationSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = glass.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    notificationAlerts.forEach { (title, desc, color) ->
                        val opensReport = title.contains("Weekly", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color.copy(alpha = 0.10f))
                                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .then(
                                    if (opensReport) Modifier
                                        .clickable {
                                            showNotificationSheet = false
                                            viewModel.showPdfPreview(true, "WEEKLY_SUMMARY")
                                        }
                                        .testTag("notification_open_pdf_report")
                                    else Modifier
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = title,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = color
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = desc,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 11.sp,
                                    color = glass.textSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Floating Attendance Toast Banner
        FloatingAttendanceToast(
            toast = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }
}

// =============================================================================
// SECTION 2: ATTENDANCE SAFETY BADGE PILL
// =============================================================================
@Composable
fun SectionTwo_AttendanceSafetyBadge(
    tierStatus: TierStatus,
    conducted: Int,
    target: Int,
    buffer: Int,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val hasConducted = conducted > 0

    val (statusText, statusColor) = when {
        !hasConducted || tierStatus == TierStatus.NO_DATA -> Pair("No Data Yet", Color(0xFF94A3B8))
        tierStatus == TierStatus.COMPLETELY_SAFE -> Pair("Completely Safe", glass.emeraldNeon)
        tierStatus == TierStatus.SAFE_ZONE -> Pair("Safe Zone", glass.amberGold)
        else -> Pair("Danger Zone", glass.crimsonRose)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseDot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9999.dp))
            .background(statusColor.copy(alpha = 0.12f))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(9999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("attendance_safety_badge_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing status dot
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = pulseAlpha }
                            .background(statusColor.copy(alpha = 0.4f))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }

                Text(
                    text = statusText,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Target: $target% • Buffer: $buffer%",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    color = glass.textMuted
                )
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// =============================================================================
// SECTION 3: ATMOSPHERIC MULTI-LAYERED MOUNTAIN LANDSCAPE BANNER
// =============================================================================
@Composable
fun AtmosphericBanner(
    percentage: Double,
    hasConducted: Boolean,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current

    val infiniteTransition = rememberInfiniteTransition(label = "mountainAura")
    val sunPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sunPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (glass.isDark) Color(0xFF151D2E).copy(alpha = 0.8f)
                else Color(0xFFEEF2FF).copy(alpha = 0.9f)
            )
            .border(
                1.dp,
                if (glass.isDark) Color(0xFF312E81).copy(alpha = 0.6f) else Color(0xFFC7D2FE).copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .testTag("atmospheric_mountain_banner")
    ) {
        // Multi-Layered Vector Mountain Range Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Sun/Dawn Aura Glow at top right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = 0.45f),
                        Color(0xFFFB923C).copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.82f, h * 0.28f),
                    radius = 38.dp.toPx() * sunPulse
                ),
                radius = 38.dp.toPx() * sunPulse,
                center = Offset(w * 0.82f, h * 0.28f)
            )

            // Sun disc
            drawCircle(
                color = Color(0xFFFDE047).copy(alpha = 0.9f),
                radius = 7.dp.toPx(),
                center = Offset(w * 0.82f, h * 0.28f)
            )

            // Far Mountain Ridge (Lavender / Sky)
            val farPath = Path().apply {
                moveTo(w * 0.25f, h)
                lineTo(w * 0.41f, h * 0.35f)
                lineTo(w * 0.52f, h * 0.62f)
                lineTo(w * 0.65f, h * 0.23f)
                lineTo(w * 0.80f, h * 0.71f)
                lineTo(w * 0.91f, h * 0.32f)
                lineTo(w * 1.05f, h)
                close()
            }
            drawPath(
                path = farPath,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF818CF8).copy(alpha = 0.45f),
                        Color(0xFFC084FC).copy(alpha = 0.40f),
                        Color(0xFF38BDF8).copy(alpha = 0.35f)
                    )
                )
            )

            // Mid Mountain Ridge (Cyan / Emerald / Indigo)
            val midPath = Path().apply {
                moveTo(w * 0.35f, h)
                lineTo(w * 0.54f, h * 0.45f)
                lineTo(w * 0.64f, h * 0.73f)
                lineTo(w * 0.76f, h * 0.40f)
                lineTo(w * 0.89f, h * 0.77f)
                lineTo(w * 1.02f, h * 0.52f)
                lineTo(w * 1.05f, h)
                close()
            }
            drawPath(
                path = midPath,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF06B6D4).copy(alpha = 0.65f),
                        Color(0xFF10B981).copy(alpha = 0.60f),
                        Color(0xFF6366F1).copy(alpha = 0.55f)
                    )
                )
            )

            // Near Mountain Foreground Ridge (Emerald / Golden Crest / Coral)
            val nearPath = Path().apply {
                moveTo(w * 0.45f, h)
                lineTo(w * 0.61f, h * 0.57f)
                lineTo(w * 0.71f, h * 0.80f)
                lineTo(w * 0.84f, h * 0.48f)
                lineTo(w * 0.95f, h * 0.78f)
                lineTo(w * 1.05f, h * 0.58f)
                lineTo(w * 1.05f, h)
                close()
            }
            drawPath(
                path = nearPath,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF34D399).copy(alpha = 0.80f),
                        Color(0xFF059669).copy(alpha = 0.75f),
                        Color(0xFF0284C7).copy(alpha = 0.70f),
                        Color(0xFFF43F5E).copy(alpha = 0.55f)
                    )
                )
            )

            // Golden Ridge Crest Contour Line
            val crestPath = Path().apply {
                moveTo(w * 0.45f, h)
                lineTo(w * 0.61f, h * 0.57f)
                lineTo(w * 0.71f, h * 0.80f)
                lineTo(w * 0.84f, h * 0.48f)
                lineTo(w * 0.95f, h * 0.78f)
                lineTo(w * 1.05f, h * 0.58f)
            }
            drawPath(
                path = crestPath,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color.White.copy(alpha = 0.4f))
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Summit Sparkling Stars
            drawCircle(Color(0xFFFDE047), radius = 2.dp.toPx(), center = Offset(w * 0.65f, h * 0.20f))
            drawCircle(Color(0xFF38BDF8), radius = 1.8.dp.toPx(), center = Offset(w * 0.84f, h * 0.44f))
            drawCircle(Color(0xFF34D399), radius = 1.5.dp.toPx(), center = Offset(w * 0.97f, h * 0.28f))
        }

        // Left Text Content Overlay
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(0.72f)
        ) {
            // Mini Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        if (glass.isDark) Color(0xFF0F172A).copy(alpha = 0.7f)
                        else com.example.ui.theme.LightPearl.copy(alpha = 0.8f)
                    )
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(9999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = if (hasConducted) "${percentage}% Peak Form" else "Start Your Journey",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF059669)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Small steps every day lead to big dreams!",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = glass.textPrimary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Keep up the good work! ✨",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = if (glass.isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
            )
        }
    }
}

// =============================================================================
// SECTION 4: HERO MONTHLY ATTENDANCE CARD
// =============================================================================
@Composable
fun SectionThree_HeroAttendanceCard(
    analytics: MonthlyAnalytics,
    profileExamTarget: Int,
    profileStipendTarget: Int,
    sessionStats: com.example.ui.components.AttendanceSessionStats? = null,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val currentPercentage = analytics.percentage.toFloat()
    val conductedCount = analytics.totalConductedCredits
    val isDarkMode = glass.isDark
    val examTarget = profileExamTarget
    val stipendTarget = profileStipendTarget

    UpgradedHeroCardContainer(
        percentage = currentPercentage,
        targetPercentage = stipendTarget.toFloat(),
        isDarkMode = isDarkMode,
        modifier = modifier.testTag("hero_monthly_attendance_card")
    ) {
        // 1. UPGRADED TOP: Radial Gauge + Exam & Stipend Eligibility Cards
        UpgradedHeroTopSection(
            percentage = currentPercentage,
            conductedCount = conductedCount,
            examTarget = examTarget,
            stipendTarget = stipendTarget,
            periodLabel = "(This Month)",
            isDarkMode = isDarkMode
        )

        // 2. UNTOUCHED MIDDLE: Keep your exact existing 4 stat cards (Attended, Missed, Conducted, Cancelled)
        val effectiveStats = sessionStats ?: com.example.ui.components.AttendanceSessionStats(
            attended = analytics.totalAttendedCredits,
            missed = (analytics.totalConductedCredits - analytics.totalAttendedCredits).coerceAtLeast(0),
            conducted = analytics.totalConductedCredits,
            cancelled = 0
        )
        com.example.ui.components.AttendanceStatsRow(stats = effectiveStats)

        // 3. UPGRADED BOTTOM: Target vs. Current Animated Progress Bar
        UpgradedHeroBottomTargetBar(
            targetPercent = stipendTarget,
            currentPercent = currentPercentage,
            isDarkMode = isDarkMode
        )
    }
}


// =============================================================================
// SECTION 5: FLOATING RECOVERY STATUS CARD
// =============================================================================
@Composable
fun SectionFour_RecoveryStatusCard(
    analytics: MonthlyAnalytics,
    stipendTarget: Int,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val hasConducted = analytics.totalConductedCredits > 0
    val isSafe = analytics.percentage >= stipendTarget

    val bodyText = when {
        !hasConducted -> "Mark today's classes below to calculate your buffer & safe skips"
        isSafe -> "You have a +${(analytics.percentage - stipendTarget).toInt()}% buffer • ${analytics.safeSkipHoursAvailable} safe skips available"
        analytics.recoveryHoursNeeded80 == -1 -> "Impossible (100% target) to achieve target after missing a class"
        else -> "Attend next ${analytics.recoveryHoursNeeded80} credit hours consecutively to achieve $stipendTarget% target"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0x1810B981), Color(0x1806B6D4))
                )
            )
            .border(1.dp, Color(0x3510B981), RoundedCornerShape(16.dp))
            .clickable { onOpenReports() }
            .padding(12.dp)
            .testTag("recovery_status_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2510B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Recovery Status",
                        fontFamily = OutfitFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.textPrimary
                    )
                    Text(
                        text = bodyText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.sp,
                        color = glass.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View Details",
                tint = glass.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// =============================================================================
// SECTION 6: QUICK ACTIVITIES (3 VIVID CARDS)
// =============================================================================
@Composable
fun SectionFive_QuickActivities(
    onWhatIfClicked: () -> Unit,
    onPdfClicked: () -> Unit,
    onBunkPlannerClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Quick Activities",
            fontFamily = OutfitFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = glass.textPrimary
        )
        Text(
            text = "Tools to help you stay ahead",
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 11.sp,
            color = glass.textMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Top Row: 3 Vivid Vertical Gradient Cards (h-32)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: What-If Predictor
            VividActivityCard(
                title = "What-If",
                subtitle = "Predictor",
                desc = "Simulate leaves",
                gradient = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
                icon = Icons.Default.Lightbulb,
                tag = "what_if_button",
                onClick = onWhatIfClicked,
                modifier = Modifier.weight(1f)
            )

            // Card 2: PDF Report
            VividActivityCard(
                title = "PDF",
                subtitle = "Report",
                desc = "Weekly/Monthly",
                gradient = listOf(Color(0xFF06B6D4), Color(0xFF14B8A6)),
                icon = Icons.Default.PictureAsPdf,
                tag = "pdf_report_button",
                onClick = onPdfClicked,
                modifier = Modifier.weight(1f)
            )

            // Card 3: Bunk Planner
            VividActivityCard(
                title = "Bunk",
                subtitle = "Planner",
                desc = "Top 3 breaks",
                gradient = listOf(Color(0xFFF97316), Color(0xFFF43F5E)),
                icon = Icons.Default.CalendarToday,
                tag = "bunk_planner_button",
                onClick = onBunkPlannerClicked,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VividActivityCard(
    title: String,
    subtitle: String,
    desc: String,
    gradient: List<Color>,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(118.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(gradient))
            .clickable { onClick() }
            .padding(10.dp)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column {
                Text(
                    text = "$title\n$subtitle",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 14.sp
                )
                Text(
                    text = desc,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.End)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}


// =============================================================================
// SECTION 7: TODAY'S CLASSES SNAPSHOT (2 SIDE-BY-SIDE CARDS)
// =============================================================================
@Composable
fun SectionSix_TodaysClassesSnapshot(
    activeSlot: PeriodSlot?,
    todayPresent: Int,
    todayTotal: Int,
    todayAuto: Int,
    modifier: Modifier = Modifier,
    phaseLabel: String = "NEXT CLASS",
    statusText: String? = null,
    classProgress: Float = 0f
) {
    TodaysClassesSnapshotSection(
        activeSlot = activeSlot,
        todayPresent = todayPresent,
        todayTotal = todayTotal,
        todayAuto = todayAuto,
        modifier = modifier,
        phaseLabel = phaseLabel,
        statusText = statusText,
        classProgress = classProgress
    )
}

private val SlotClockFormat = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)

/** Parses "09:30 AM" / "02:50 PM" style slot times; null if the text is not in that format. */
private fun parseSlotClock(text: String): LocalTime? =
    runCatching { LocalTime.parse(text.trim().uppercase(java.util.Locale.ENGLISH), SlotClockFormat) }.getOrNull()

// =============================================================================
// SECTION 8: TODAY'S CLASSES (ALL) INTERACTIVE LIST + BULK BAR
// =============================================================================
@Composable
fun SectionSeven_TodaysClassesListAndInteractiveControls(
    slots: List<PeriodSlot>,
    statuses: Map<Int, PeriodStatus>,
    isHoliday: Boolean,
    holidayName: String,
    isWeekend: Boolean,
    onMarkPeriod: (Int, PeriodStatus) -> Unit,
    onBulkPresent: () -> Unit,
    onBulkAbsent: () -> Unit,
    onBulkCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Top Bar: Left Title + Right Bulk Action Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Classes (All)",
                fontFamily = OutfitFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary
            )

            // Glass pill container with Bulk buttons (Soft Glass before marking -> Solid Active with Undo)
            val isAllPresent = slots.isNotEmpty() && slots.all { slot ->
                val s = statuses[slot.periodNumber]
                s == PeriodStatus.PRESENT || slot.subject.isAutoPresent
            }
            val isAllAbsent = slots.isNotEmpty() && slots.all { slot ->
                val s = statuses[slot.periodNumber]
                s == PeriodStatus.ABSENT || slot.subject.isAutoPresent
            }

            val isDark = glass.isDark
            val chipBg = if (isDark) Color(0xFF1E2538).copy(alpha = 0.65f) else com.example.ui.theme.LightSurfaceAlt
            val chipBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(chipBg)
                    .border(1.dp, chipBorder, RoundedCornerShape(9999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Bulk:",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )

                    if (isAllPresent) {
                        // Solid Green Active Pill with Undo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF059669))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "✓ All Present",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            TactileBulkUndoIcon(
                                tintColor = Color(0xFF10B981),
                                onClick = onBulkCancelled,
                                tag = "bulk_undo_present_button"
                            )
                        }
                    } else if (isAllAbsent) {
                        // Solid Red Active Pill with Undo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFFE11D48))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "✕ All Absent",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            TactileBulkUndoIcon(
                                tintColor = Color(0xFFF43F5E),
                                onClick = onBulkCancelled,
                                tag = "bulk_undo_absent_button"
                            )
                        }
                    } else {
                        // Soft Translucent Glass Buttons Before Marking
                        // Green [ ✓ All ]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF10B981).copy(alpha = if (isDark) 0.15f else 0.12f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                .clickable { onBulkPresent() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("bulk_present_button")
                        ) {
                            Text(
                                text = "✓ All",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                            )
                        }

                        // Rose [ ✕ All ]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF43F5E).copy(alpha = if (isDark) 0.14f else 0.10f))
                                .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                .clickable { onBulkAbsent() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("bulk_absent_button")
                        ) {
                            Text(
                                text = "✕ All",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isWeekend) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🗓️ Weekend • Previewing Monday's Academic Timetable",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF818CF8)
                )
            }
        }

        if (isHoliday) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF06B6D4).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "🏖️ Official Holiday: $holidayName. Zero conducted credit hours counted today.",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF06B6D4)
                )
            }
        } else {
            // List of Periods (Strictly Periods 1 to 7 with Expanding Marked Pill + Undo)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.filter { it.periodNumber <= 7 }.forEach { slot ->
                    val status = statuses[slot.periodNumber] ?: if (slot.subject.isAutoPresent) PeriodStatus.AUTO_PRESENT else PeriodStatus.PENDING

                    val isDark = glass.isDark

                    val surfaceBrush = if (isDark) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF161B2E).copy(alpha = 0.75f), // Nocturne Smoked Glass
                                Color(0xFF0D1120).copy(alpha = 0.88f)  // Deep Obsidian Core
                            )
                        )
                    } else {
                        // Pearl lavender glass, faintly tinted by the card's status (green / red / cyan / indigo)
                        val lightTint = when {
                            slot.subject.isAutoPresent -> Color(0xFF06B6D4)
                            status == PeriodStatus.PRESENT -> Color(0xFF10B981)
                            status == PeriodStatus.ABSENT -> Color(0xFFF43F5E)
                            else -> Color(0xFF6366F1)
                        }
                        Brush.linearGradient(
                            colors = listOf(
                                com.example.ui.theme.LightCardSurface,
                                androidx.compose.ui.graphics.lerp(com.example.ui.theme.LightPearl, lightTint, 0.10f)
                            )
                        )
                    }

                    val cardBorderColor by animateColorAsState(
                        targetValue = when {
                            slot.subject.isAutoPresent -> Color(0xFF06B6D4).copy(alpha = 0.40f)
                            status == PeriodStatus.PRESENT -> Color(0xFF10B981).copy(alpha = 0.50f)
                            status == PeriodStatus.ABSENT -> Color(0xFFF43F5E).copy(alpha = 0.50f)
                            status == PeriodStatus.PENDING -> Color(0xFF94A3B8).copy(alpha = 0.35f)
                            status == PeriodStatus.NO_CLASS -> Color(0xFF64748B).copy(alpha = 0.30f)
                            else -> if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF6366F1).copy(alpha = 0.15f)
                        },
                        animationSpec = tween(300),
                        label = "homeCardBorder"
                    )

                    val cardAccentStripe by animateColorAsState(
                        targetValue = when {
                            slot.subject.isAutoPresent -> Color(0xFF06B6D4)
                            status == PeriodStatus.PRESENT -> Color(0xFF10B981)
                            status == PeriodStatus.ABSENT -> Color(0xFFF43F5E)
                            status == PeriodStatus.PENDING -> Color(0xFF94A3B8)
                            status == PeriodStatus.NO_CLASS -> Color(0xFF64748B)
                            else -> if (isDark) Color(0xFF4F46E5) else Color(0xFF818CF8)
                        },
                        animationSpec = tween(300),
                        label = "homeCardAccent"
                    )

                    val periodBadgeBrush = if (isDark) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                        )
                    }

                    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                    val mutedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    val codeColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (status == PeriodStatus.PRESENT || status == PeriodStatus.ABSENT) 8.dp else 3.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = cardBorderColor,
                                spotColor = cardBorderColor
                            )
                            .border(1.2.dp, cardBorderColor, RoundedCornerShape(16.dp))
                            .attendanceShine(status),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(surfaceBrush)
                        ) {
                            // Left Accent Stripe
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                    .background(cardAccentStripe)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left: Period Badge + Full Name + Details
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (slot.subject.isAutoPresent) {
                                                    Brush.linearGradient(listOf(Color(0xFF06B6D4).copy(alpha = 0.25f), Color(0xFF0891B2).copy(alpha = 0.25f)))
                                                } else {
                                                    periodBadgeBrush
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (slot.subject.isAutoPresent) Color(0xFF06B6D4).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (slot.isTwoHourBlock) "6-7" else "${slot.periodNumber}",
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = if (slot.subject.isAutoPresent) Color(0xFF06B6D4) else Color.White
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = slot.subject.code,
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = codeColor
                                            )
                                        }
                                        Text(
                                            text = slot.subject.fullName,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = textColor,
                                            lineHeight = 17.sp
                                        )
                                        Text(
                                            text = "${slot.timeRange} • ${slot.subject.creditHours} Cr${if (slot.isTwoHourBlock) " • Lab" else ""}",
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontSize = 10.5.sp,
                                            color = mutedColor
                                        )
                                    }
                                }

                                // Right: Auto Badge OR Expanding Marked/Undo Pill
                                if (slot.subject.isAutoPresent) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9999.dp))
                                            .background(Color(0xFF06B6D4).copy(alpha = 0.16f))
                                            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.40f), RoundedCornerShape(9999.dp))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFF06B6D4),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "🔒 Auto",
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF06B6D4)
                                            )
                                        }
                                    }
                                } else {
                                    AnimatedContent(
                                        targetState = status,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(220)) + expandHorizontally(
                                                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
                                            )) togetherWith (fadeOut(animationSpec = tween(160)) + shrinkHorizontally(animationSpec = tween(180)))
                                        },
                                        label = "homePeriodMarkingMorph"
                                    ) { s ->
                                        when (s) {
                                            PeriodStatus.PRESENT -> {
                                                // Expanded Solid Green Pill + Undo
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .shadow(6.dp, RoundedCornerShape(999.dp), spotColor = Color(0xFF10B981).copy(alpha = 0.4f))
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(Color(0xFF059669), Color(0xFF10B981))
                                                                )
                                                            )
                                                            .padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = "Marked",
                                                                fontFamily = SpaceGroteskFontFamily,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }

                                                    HomePeriodUndoIcon(
                                                        tintColor = Color(0xFF10B981),
                                                        isDarkMode = isDark,
                                                        onClick = { onMarkPeriod(slot.periodNumber, PeriodStatus.PENDING) },
                                                        tag = "undo_p${slot.periodNumber}"
                                                    )
                                                }
                                            }

                                            PeriodStatus.ABSENT -> {
                                                // Expanded Solid Red Pill + Undo
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .shadow(6.dp, RoundedCornerShape(999.dp), spotColor = Color(0xFFF43F5E).copy(alpha = 0.4f))
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(Color(0xFFE11D48), Color(0xFFF43F5E))
                                                                )
                                                            )
                                                            .padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = "Marked",
                                                                fontFamily = SpaceGroteskFontFamily,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }

                                                    HomePeriodUndoIcon(
                                                        tintColor = Color(0xFFF43F5E),
                                                        isDarkMode = isDark,
                                                        onClick = { onMarkPeriod(slot.periodNumber, PeriodStatus.PENDING) },
                                                        tag = "undo_p${slot.periodNumber}"
                                                    )
                                                }
                                            }

                                            PeriodStatus.NO_CLASS -> {
                                                // No Class / Cancelled Badge + Undo
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(if (isDark) Color(0x3364748B) else Color(0x2294A3B8))
                                                            .border(1.dp, Color(0xFF64748B).copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                                            .padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "No Class",
                                                            fontFamily = SpaceGroteskFontFamily,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF94A3B8)
                                                        )
                                                    }

                                                    HomePeriodUndoIcon(
                                                        tintColor = Color(0xFF94A3B8),
                                                        isDarkMode = isDark,
                                                        onClick = { onMarkPeriod(slot.periodNumber, PeriodStatus.PENDING) },
                                                        tag = "undo_p${slot.periodNumber}"
                                                    )
                                                }
                                            }

                                            else -> {
                                                // Unmarked 2-button Circular Glass Area
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Soft Emerald [ ✓ ]
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF10B981).copy(alpha = if (isDark) 0.15f else 0.12f))
                                                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), CircleShape)
                                                            .clickable { onMarkPeriod(slot.periodNumber, PeriodStatus.PRESENT) }
                                                            .testTag("present_p${slot.periodNumber}"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Mark Present",
                                                            tint = Color(0xFF10B981),
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }

                                                    // Soft Rose [ ✕ ]
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFF43F5E).copy(alpha = if (isDark) 0.14f else 0.10f))
                                                            .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.35f), CircleShape)
                                                            .clickable { onMarkPeriod(slot.periodNumber, PeriodStatus.ABSENT) }
                                                            .testTag("absent_p${slot.periodNumber}"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Mark Absent",
                                                            tint = Color(0xFFF43F5E),
                                                            modifier = Modifier.size(17.dp)
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
        }
    }
}

@Composable
private fun TactileBulkUndoIcon(
    tintColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    val isDark = LocalGlassTheme.current.isDark
    val ghostBg = if (isDark) Color.White.copy(alpha = 0.08f) else com.example.ui.theme.LightSurfaceAlt
    val ghostBorder = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE2E8F0)
    val ghostFg = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(ghostBg)
            .border(1.dp, ghostBorder, CircleShape)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(13.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.width * 0.38f
            val arcPath = Path().apply {
                addArc(
                    oval = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                    startAngleDegrees = 50f,
                    sweepAngleDegrees = 270f
                )
            }
            drawPath(path = arcPath, color = ghostFg, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
            val arrowHead = Path().apply {
                val ax = cx + (r * kotlin.math.cos(Math.toRadians(50.0))).toFloat()
                val ay = cy + (r * kotlin.math.sin(Math.toRadians(50.0))).toFloat()
                moveTo(ax - 2.dp.toPx(), ay - 4.dp.toPx())
                lineTo(ax + 2.5.dp.toPx(), ay + 1.dp.toPx())
                lineTo(ax - 3.dp.toPx(), ay + 2.dp.toPx())
            }
            drawPath(path = arrowHead, color = ghostFg)
        }
    }
}

@Composable
private fun HomePeriodUndoIcon(
    tintColor: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val ghostBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else com.example.ui.theme.LightSurfaceAlt
    val ghostBorder = if (isDarkMode) Color.White.copy(alpha = 0.14f) else Color(0xFFE2E8F0)
    val ghostFg = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(ghostBg)
            .border(1.dp, ghostBorder, CircleShape)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.width * 0.38f
            val arcPath = Path().apply {
                addArc(
                    oval = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                    startAngleDegrees = 50f,
                    sweepAngleDegrees = 270f
                )
            }
            drawPath(path = arcPath, color = ghostFg, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            val arrowHead = Path().apply {
                val ax = cx + (r * kotlin.math.cos(Math.toRadians(50.0))).toFloat()
                val ay = cy + (r * kotlin.math.sin(Math.toRadians(50.0))).toFloat()
                moveTo(ax - 2.dp.toPx(), ay - 5.dp.toPx())
                lineTo(ax + 3.dp.toPx(), ay + 1.dp.toPx())
                lineTo(ax - 4.dp.toPx(), ay + 3.dp.toPx())
            }
            drawPath(path = arrowHead, color = ghostFg)
        }
    }
}


// =============================================================================
// SECTION 9: QUICK STATS, NOTIFICATIONS & MOTIVATIONAL FOOTER
// =============================================================================
@Composable
fun QuickStatsAndNotifications(
    todayPresent: Int,
    todayAbsent: Int,
    todayLibrary: Int,
    todayCancelled: Int,
    onViewAllNotifications: () -> Unit,
    onOpenPdfReport: () -> Unit = {},
    dateOfBirth: String = "",
    onOpenProfile: () -> Unit = {},
    onMilestoneMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 9A. 🎯 Quick Stats (4-Column Grid)
        Column {
            Text(
                text = "🎯 Quick Stats",
                fontFamily = OutfitFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickStatBox(
                    label = "Present",
                    count = todayPresent,
                    icon = "✓",
                    iconColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )

                QuickStatBox(
                    label = "Absent",
                    count = todayAbsent,
                    icon = "✕",
                    iconColor = Color(0xFFF43F5E),
                    modifier = Modifier.weight(1f)
                )

                QuickStatBox(
                    label = "Library",
                    count = todayLibrary,
                    icon = "📚",
                    iconColor = Color(0xFF6366F1),
                    modifier = Modifier.weight(1f)
                )

                QuickStatBox(
                    label = "Cancelled",
                    count = todayCancelled,
                    icon = "⊘",
                    iconColor = Color(0xFF64748B),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 9B. Recent Notifications Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(glass.cardSurface)
                .border(1.dp, glass.cardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = glass.royalIndigo,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Recent Notifications",
                            fontFamily = OutfitFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = glass.textPrimary
                        )
                    }

                    Text(
                        text = "View All",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.royalIndigo,
                        modifier = Modifier.clickable { onViewAllNotifications() }
                    )
                }

                NotificationRow(
                    dotColor = Color(0xFF6366F1),
                    title = "Weekly Attendance Report",
                    desc = "Your weekly summary is now available",
                    time = "2h ago",
                    onClick = onOpenPdfReport
                )

                NotificationRow(
                    dotColor = Color(0xFFF59E0B),
                    title = "Reminder",
                    desc = "Mark your afternoon lab attendance",
                    time = "4h ago"
                )

                NotificationRow(
                    dotColor = Color(0xFFF43F5E),
                    title = "Holiday Notice",
                    desc = "AKTU Academic Calendar updated",
                    time = "1d ago"
                )
            }
        }

        // =========================================================================
        // ACADEMIC MILESTONES & COUNTDOWN
        // =========================================================================
        CountdownSection(
            dateOfBirth = dateOfBirth,
            onOpenProfile = onOpenProfile,
            onMessage = onMilestoneMessage
        )

        // 9C. Motivational Trophy & Paper Airplane Footer Banner
        MotivationalFooterBanner()
    }
}

@Composable
private fun QuickStatBox(
    label: String,
    count: Int,
    icon: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    QuickStatCard(label = label, count = count, iconColor = iconColor, modifier = modifier)
}

@Composable
private fun NotificationRow(
    dotColor: Color,
    title: String,
    desc: String,
    time: String,
    onClick: (() -> Unit)? = null
) {
    val glass = LocalGlassTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column {
                Text(
                    text = title,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = glass.textPrimary
                )
                Text(
                    text = desc,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 9.sp,
                    color = glass.textMuted
                )
            }
        }

        Text(
            text = time,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 9.sp,
            color = glass.textMuted
        )
    }
}

// 9C: Motivational Footer Banner with Golden Trophy & Floating Paper Airplane
@Composable
private fun MotivationalFooterBanner() {
    val glass = LocalGlassTheme.current

    val infiniteTransition = rememberInfiniteTransition(label = "airplaneFloat")
    val planeFloatY by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "planeFloatY"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (glass.isDark) Brush.linearGradient(listOf(Color(0xFF151D2E).copy(alpha = 0.9f), Color(0xFF151D2E).copy(alpha = 0.9f)))
                else Brush.linearGradient(listOf(Color(0xFFEEE6FF), Color(0xFFDCEBFF)))
            )
            .border(
                1.dp,
                if (glass.isDark) Color(0xFF312E81).copy(alpha = 0.7f) else Color(0xFFC7D2FE).copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Golden Trophy SVG / Canvas
            Canvas(modifier = Modifier.size(52.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Trophy Cup (Gold)
                val cupPath = Path().apply {
                    moveTo(cx - 10.dp.toPx(), cy - 8.dp.toPx())
                    lineTo(cx + 10.dp.toPx(), cy - 8.dp.toPx())
                    cubicTo(
                        cx + 10.dp.toPx(), cy + 6.dp.toPx(),
                        cx + 4.dp.toPx(), cy + 8.dp.toPx(),
                        cx, cy + 9.dp.toPx()
                    )
                    cubicTo(
                        cx - 4.dp.toPx(), cy + 8.dp.toPx(),
                        cx - 10.dp.toPx(), cy + 6.dp.toPx(),
                        cx - 10.dp.toPx(), cy - 8.dp.toPx()
                    )
                    close()
                }
                drawPath(
                    path = cupPath,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706))
                    )
                )

                // Handles
                drawArc(
                    brush = Brush.linearGradient(listOf(Color(0xFFFDE047), Color(0xFFD97706))),
                    startAngle = 120f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - 16.dp.toPx(), cy - 8.dp.toPx()),
                    size = Size(8.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.linearGradient(listOf(Color(0xFFFDE047), Color(0xFFD97706))),
                    startAngle = -60f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx + 8.dp.toPx(), cy - 8.dp.toPx()),
                    size = Size(8.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Stem & Base
                drawRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(cx - 3.dp.toPx(), cy + 9.dp.toPx()),
                    size = Size(6.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(cx - 8.dp.toPx(), cy + 13.dp.toPx()),
                    size = Size(16.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f)
                )

                // Sparkle on cup
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy - 2.dp.toPx()))
            }

            // Center: Motivational Cursive Quote
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "“Dream Big, Study Hard,\nAchieve More!”",
                    fontFamily = CaveatFontFamily,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (glass.isDark) Color(0xFFC7D2FE) else Color(0xFF1E1B4B),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            // Right: Origami Paper Airplane SVG / Canvas with Looping Dashed Flight Trail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(0, planeFloatY.dp.roundToPx()) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Dashed loop flight trail
                    val trail = Path().apply {
                        moveTo(w * 0.15f, h * 0.8f)
                        cubicTo(w * 0.3f, h * 0.85f, w * 0.45f, h * 0.7f, w * 0.45f, h * 0.55f)
                        cubicTo(w * 0.45f, h * 0.4f, w * 0.35f, h * 0.45f, w * 0.35f, h * 0.6f)
                        cubicTo(w * 0.35f, h * 0.75f, w * 0.6f, h * 0.75f, w * 0.8f, h * 0.45f)
                    }
                    drawPath(
                        path = trail,
                        color = Color(0xFF818CF8).copy(alpha = 0.85f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                            cap = StrokeCap.Round
                        )
                    )

                    // Origami Paper Airplane
                    val plane = Path().apply {
                        moveTo(w * 0.95f, h * 0.35f)
                        lineTo(w * 0.55f, h * 0.22f)
                        lineTo(w * 0.65f, h * 0.38f)
                        close()
                    }
                    drawPath(path = plane, color = Color.White)

                    val planeFold = Path().apply {
                        moveTo(w * 0.95f, h * 0.35f)
                        lineTo(w * 0.65f, h * 0.38f)
                        lineTo(w * 0.55f, h * 0.44f)
                        close()
                    }
                    drawPath(path = planeFold, color = Color(0xFFE0E7FF))

                    // Orange accent keel
                    val keel = Path().apply {
                        moveTo(w * 0.65f, h * 0.38f)
                        lineTo(w * 0.75f, h * 0.40f)
                        lineTo(w * 0.66f, h * 0.46f)
                        close()
                    }
                    drawPath(path = keel, color = Color(0xFFF97316))
                }
            }
        }
    }
}

// =============================================================================
// INTERACTIVE MODALS (What-If Predictor & Bunk Planner)
// =============================================================================
@Composable
fun WhatIfPredictorModal(
    analytics: MonthlyAnalytics,
    onDismiss: () -> Unit
) {
    val glass = LocalGlassTheme.current
    var attendDays by remember { mutableStateOf(3) }
    var missDays by remember { mutableStateOf(0) }

    val simulatedAttended = analytics.totalAttendedCredits + (attendDays * 7)
    val simulatedConducted = analytics.totalConductedCredits + ((attendDays + missDays) * 7)
    val simulatedPct = com.example.data.math.AttendanceMath.calculatePercentage(simulatedAttended, simulatedConducted)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (glass.isDark) Color(0xFF151D2E) else com.example.ui.theme.LightPearl),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "What-If Simulator",
                    fontFamily = OutfitFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = glass.textPrimary
                )
                Text(
                    text = "Predict your attendance by simulating upcoming days",
                    fontSize = 12.sp,
                    color = glass.textSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Days to Attend (+7 Cr/day):", fontSize = 13.sp, color = glass.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "-",
                            modifier = Modifier
                                .clickable { if (attendDays > 0) attendDays-- }
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = glass.textPrimary
                        )
                        Text("$attendDays", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        Text(
                            text = "+",
                            modifier = Modifier
                                .clickable { attendDays++ }
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = glass.textPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Days to Miss (+0 Attended):", fontSize = 13.sp, color = glass.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "-",
                            modifier = Modifier
                                .clickable { if (missDays > 0) missDays-- }
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = glass.textPrimary
                        )
                        Text("$missDays", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        Text(
                            text = "+",
                            modifier = Modifier
                                .clickable { missDays++ }
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = glass.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glass.royalIndigo.copy(alpha = 0.15f))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Projected Result:", fontSize = 11.sp, color = glass.textMuted)
                        Text(
                            text = "$simulatedPct% ($simulatedAttended / $simulatedConducted Hrs)",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (simulatedPct >= 80.0) glass.emeraldNeon else glass.crimsonRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.royalIndigo)
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun BunkPlannerModal(
    analytics: MonthlyAnalytics,
    onDismiss: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val safeHours = analytics.safeSkipHoursAvailable
    val safeDays = safeHours / 7

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (glass.isDark) Color(0xFF151D2E) else com.example.ui.theme.LightPearl),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Smart Bunk Planner",
                    fontFamily = OutfitFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = glass.textPrimary
                )
                Text(
                    text = "Safe credit skip calculation above 80% target",
                    fontSize = 12.sp,
                    color = glass.textSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (safeHours > 0) glass.emeraldNeon.copy(alpha = 0.15f) else glass.crimsonRose.copy(alpha = 0.15f)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = if (safeHours > 0) "You can safely skip $safeHours credit-hours (~$safeDays full days)." else "No bunks available right now!",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (safeHours > 0) glass.emeraldNeon else glass.crimsonRose
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (safeHours > 0)
                                "Your attendance will remain at or above 80% after skipping these credits."
                            else
                                "You are below your 80% Stipend threshold. Attend upcoming classes to build your buffer.",
                            fontSize = 12.sp,
                            color = glass.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.royalIndigo)
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Got It", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
