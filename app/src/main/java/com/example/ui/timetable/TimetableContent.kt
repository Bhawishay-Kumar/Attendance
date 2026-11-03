package com.example.ui.timetable

import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import com.example.ui.components.AppScreenHeader
import com.example.ui.theme.PlusJakartaSansFontFamily

/* ═══════════════════════════════════════════════════════════════════════════
 *  REQUIRED GRADLE DEPENDENCY:  implementation("androidx.compose.material:material-icons-extended")
 * ═══════════════════════════════════════════════════════════════════════════ */

// ───────────────────────────── FONTS ─────────────────────────────
// App font (same Plus Jakarta Sans the HTML uses). The HTML's `font-mono` is the system monospace.
private val AppFont: FontFamily = PlusJakartaSansFontFamily
private val MonoFont: FontFamily = FontFamily.Monospace

// ───────────────────────────── UI MODELS (feed these from your real data) ─────────────────────────────

enum class SessionType(val label: String) { Theory("Theory"), Lab("Lab"), Seminar("Seminar") }

enum class SubjectIcon(val vector: ImageVector) {
    Math(Icons.Rounded.Calculate), Physics(Icons.Rounded.Science), Computer(Icons.Rounded.Memory),
    Graphics(Icons.Rounded.Straighten), Logic(Icons.Rounded.Bolt), Code(Icons.Rounded.Code),
    Brain(Icons.Rounded.Psychology), Talk(Icons.Rounded.Chat), Signal(Icons.Rounded.GraphicEq),
    Network(Icons.Rounded.Hub), Robot(Icons.Rounded.SmartToy), Cloud(Icons.Rounded.Cloud),
    Seminar(Icons.Rounded.Groups), Database(Icons.Rounded.Storage), Structure(Icons.Rounded.AccountTree),
    Web(Icons.Rounded.Public), Os(Icons.Rounded.Settings), Terminal(Icons.Rounded.Terminal),
    Desktop(Icons.Rounded.Computer), Generic(Icons.Rounded.AutoStories)
}

enum class BreakKind { Water, Lunch }

sealed interface TimelineItemUi {
    val key: String

    data class PeriodItem(
        override val key: String,
        val number: Int,
        val timeLabel: String,            // "09:00 – 10:00 AM"
        val subjectName: String,
        val subjectCode: String,
        val type: SessionType,
        val icon: SubjectIcon,
        val highlightLabel: String? = null, // e.g. "2 HRS · 2X CREDIT" -> renders the hero card
        val fullName: String? = null,
        val sessionLabel: String? = null,   // e.g. "Practical Lab (2 Hours)"
        val faculty: String? = null,
        val supportingStaff: String? = null,
        val venue: String? = null,          // Non-null ONLY for WFM, CPT-II, and Textile Testing
        val attendancePercent: Int? = null,
        val attendanceSafeAt: Int = 75
    ) : TimelineItemUi {
        val isHighlight get() = highlightLabel != null
        val hasDetails get() = faculty != null || venue != null || attendancePercent != null || fullName != null || supportingStaff != null
    }

    data class BreakItem(
        override val key: String,
        val kind: BreakKind,
        val timeLabel: String
    ) : TimelineItemUi
}

data class DayScheduleUi(
    val shortLabel: String,     // "MON"
    val fullTitle: String,      // "Monday"
    val periodsLabel: String,   // "7 Periods"
    val practicalLabel: String, // "1 Practical"
    val freeLabel: String,      // "0 Free"
    val items: List<TimelineItemUi>
)

data class DistributionSlice(val label: String, val count: Int)

data class WorkloadUi(
    val totalPeriods: Int,
    val practicalSessions: Int,
    val practicalHours: Int,
    val freePeriods: Int,
    val distribution: List<DistributionSlice>,   // first 4 map to blue, purple, emerald, amber
    val distributionCaption: String = "100% Completed"
)

data class HolidayUi(val name: String, val dateLabel: String, val countdownLabel: String? = null)

@Immutable
data class TimetableUiState(
    val title: String = "Weekly Timetable",
    val subtitle: String,               // "BHTT 5th Sem • Batch A • Honours Track"
    val profileLine: String,            // "BHTT • 5th Sem • Batch A"
    val profileSub: String,             // "Honours Track"
    val semesterBadge: String = "Active Sem",
    val waterBreakTimes: List<String>,  // ["11:10 – 11:20 AM", "03:40 – 03:50 PM"]
    val lunchTime: String,              // "01:00 – 02:00 PM"
    val lunchDuration: String,          // "Duration 60 min"
    val days: List<DayScheduleUi>,
    val selectedDayIndex: Int,
    val workload: WorkloadUi,
    val featuredHoliday: HolidayUi?,
    val moreHolidays: List<HolidayUi>
)

// ───────────────────────────── COLOR TOKENS ─────────────────────────────

@Immutable
data class TtColors(
    val isDark: Boolean,
    val bgTop: Color, val bgMid: Color, val bgBot: Color,
    val glass: Color, val glassBorder: Color, val glassSheen: Color,
    val hiFill: Color, val hiBorder: Color,
    val title: Color, val sub: Color, val muted: Color, val heading: Color,
    val dockFill: Color, val dockBorder: Color,
    val chipFill: Color, val chipBorder: Color, val chipText: Color,
    val iconBoxFill: Color, val iconBoxBorder: Color, val iconTint: Color,
    val waterFill: Color, val waterBorder: Color, val waterText: Color,
    val lunchFill: Color, val lunchBorder: Color, val lunchText: Color,
    val breakNodeFill: Color,
    val pillFill: Color, val pillBorder: Color, val pillText: Color,
    val pillAccentFill: Color, val pillAccentBorder: Color, val pillAccentText: Color,
    val inactiveTab: Color, val trackFill: Color, val shadow: Color
)

private val Cyan400 = Color(0xFF22D3EE)
private val Sky400 = Color(0xFF38BDF8)
private val Sky500 = Color(0xFF0EA5E9)
private val Cyan500 = Color(0xFF06B6D4)
private val Blue600 = Color(0xFF2563EB)
private val Indigo600 = Color(0xFF4F46E5)
private val Amber500 = Color(0xFFF59E0B)
private val Emerald500 = Color(0xFF10B981)
private val Rose500 = Color(0xFFF43F5E)
private val Purple500 = Color(0xFFA855F7)
private val Blue500 = Color(0xFF3B82F6)

private val NightColors = TtColors(
    isDark = true,
    bgTop = Color(0xFF0E172E), bgMid = Color(0xFF080D1E), bgBot = Color(0xFF040713),
    glass = Color(0xA60E172E), glassBorder = Color(0x1AFFFFFF), glassSheen = Color(0x0DFFFFFF),
    hiFill = Color(0xD9121D3D), hiBorder = Color(0x5938BDF8),
    title = Color(0xFFFFFFFF), sub = Color(0xFF38BDF8), muted = Color(0xFF94A3B8), heading = Color(0xFFF8FAFC),
    dockFill = Color(0xD10A1023), dockBorder = Color(0x1AFFFFFF),
    chipFill = Color(0x99083344), chipBorder = Color(0x66155E75), chipText = Color(0xFF22D3EE),
    iconBoxFill = Color(0xCC1E293B), iconBoxBorder = Color(0x4D06B6D4), iconTint = Color(0xFF67E8F9),
    waterFill = Color(0x73083344), waterBorder = Color(0x4D22D3EE), waterText = Color(0xFF67E8F9),
    lunchFill = Color(0x59451A03), lunchBorder = Color(0x4DF59E0B), lunchText = Color(0xFFFCD34D),
    breakNodeFill = Color(0xFF0F172A),
    pillFill = Color(0xCC0F172A), pillBorder = Color(0x99334155), pillText = Color(0xFFCBD5E1),
    pillAccentFill = Color(0xCC083344), pillAccentBorder = Color(0x6606B6D4), pillAccentText = Color(0xFF67E8F9),
    inactiveTab = Color(0xFF94A3B8), trackFill = Color(0xCC0F172A), shadow = Color(0x73000000)
)

private val DayColors = TtColors(
    isDark = false,
    bgTop = Color(0xFFE8EDFF), bgMid = Color(0xFFEFF0FF), bgBot = Color(0xFFF3ECFF),
    glass = Color(0xCCE9ECFF), glassBorder = Color(0x664F46E5), glassSheen = Color(0x33F5F6FF),
    hiFill = Color(0xE6DFE6FF), hiBorder = Color(0x590EA5E9),
    title = Color(0xFF0F172A), sub = Color(0xFF0284C7), muted = Color(0xFF64748B), heading = Color(0xFF1E293B),
    dockFill = Color(0xD6E6EAFF), dockBorder = Color(0x664F46E5),
    chipFill = Color(0xB3CFFAFE), chipBorder = Color(0xFF67E8F9), chipText = Color(0xFF0891B2),
    iconBoxFill = Color(0xFFCFFAFE), iconBoxBorder = Color(0xFF67E8F9), iconTint = Color(0xFF0891B2),
    waterFill = Color(0xD9DDF4FF), waterBorder = Color(0xCC67E8F9), waterText = Color(0xFF0891B2),
    lunchFill = Color(0xE6FFF3D6), lunchBorder = Color(0xCCFCD34D), lunchText = Color(0xFFB45309),
    breakNodeFill = Color(0xFFF5F6FF),
    pillFill = Color(0xFFF5F6FF), pillBorder = Color(0xFFD9DEFA), pillText = Color(0xFF334155),
    pillAccentFill = Color(0xFFE0F2FE), pillAccentBorder = Color(0xFF7DD3FC), pillAccentText = Color(0xFF075985),
    inactiveTab = Color(0xFF94A3B8), trackFill = Color(0x99DCE2FF), shadow = Color(0x264F46E5)
)

@Composable
private fun animatedColors(dark: Boolean): TtColors {
    val t = if (dark) NightColors else DayColors
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, tween(300), label = "themeColor").value
    return TtColors(
        isDark = dark,
        bgTop = a(t.bgTop), bgMid = a(t.bgMid), bgBot = a(t.bgBot),
        glass = a(t.glass), glassBorder = a(t.glassBorder), glassSheen = a(t.glassSheen),
        hiFill = a(t.hiFill), hiBorder = a(t.hiBorder),
        title = a(t.title), sub = a(t.sub), muted = a(t.muted), heading = a(t.heading),
        dockFill = a(t.dockFill), dockBorder = a(t.dockBorder),
        chipFill = a(t.chipFill), chipBorder = a(t.chipBorder), chipText = a(t.chipText),
        iconBoxFill = a(t.iconBoxFill), iconBoxBorder = a(t.iconBoxBorder), iconTint = a(t.iconTint),
        waterFill = a(t.waterFill), waterBorder = a(t.waterBorder), waterText = a(t.waterText),
        lunchFill = a(t.lunchFill), lunchBorder = a(t.lunchBorder), lunchText = a(t.lunchText),
        breakNodeFill = a(t.breakNodeFill),
        pillFill = a(t.pillFill), pillBorder = a(t.pillBorder), pillText = a(t.pillText),
        pillAccentFill = a(t.pillAccentFill), pillAccentBorder = a(t.pillAccentBorder), pillAccentText = a(t.pillAccentText),
        inactiveTab = a(t.inactiveTab), trackFill = a(t.trackFill), shadow = a(t.shadow)
    )
}

private val LocalTt = compositionLocalOf { NightColors }

private val CardShape = RoundedCornerShape(16.dp)
private val SmallCardShape = RoundedCornerShape(12.dp)
private val HeroShape = RoundedCornerShape(20.dp)
private val TactileSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 400f)

// ───────────────────────────── MODIFIER HELPERS ─────────────────────────────

private fun Modifier.glass(c: TtColors, shape: Shape, hero: Boolean = false): Modifier =
    this
        .shadow(if (hero) 14.dp else 8.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
        .clip(shape)
        .background(if (hero) c.hiFill else c.glass)
        .border(1.dp, if (hero) c.hiBorder else c.glassBorder, shape)
        .drawBehind {
            drawRect(
                Brush.linearGradient(
                    0f to c.glassSheen, 0.6f to Color.Transparent,
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
            )
        }

/** Spring-press scale (0.985) used on every tactile card. */
@Composable
private fun Modifier.tactile(onClick: (() -> Unit)? = null, pressedScale: Float = 0.985f): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, TactileSpring, label = "tactile")
    val haptics = com.example.ui.settings.rememberAppHaptics()
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (onClick != null) Modifier.clickable(interactionSource = source, indication = null) { haptics.click(); onClick() }
            else Modifier.clickable(interactionSource = source, indication = null) {}
        )
}

private fun txt(size: Float, weight: FontWeight = FontWeight.Normal, mono: Boolean = false, spacing: Float = 0f) =
    TextStyle(
        fontFamily = if (mono) MonoFont else AppFont,
        fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp
    )

// ───────────────────────────── ROOT SCREEN ─────────────────────────────

/**
 * Stateless timetable content. Your existing GlassDock in MainActivity stays untouched:
 * this composable only reserves bottom space for it and never draws a dock.
 *
 * `header` is a slot. The default is the redesigned header; pass your current header composable
 * to keep it as-is.
 */
@Composable
fun TimetableContent(
    state: TimetableUiState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onPeriodClick: (String) -> Unit,
    onViewAttendance: (String) -> Unit,
    onHolidayClick: (HolidayUi) -> Unit,
    onViewMatrix: () -> Unit,
    onOpenAcademicCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    /** false = use the app-level gradient + StarfieldCanvas from MainActivity (recommended). */
    drawOwnBackground: Boolean = false,
    header: @Composable () -> Unit = {
        TimetableHeader(state.title, state.subtitle, darkTheme, onToggleTheme)
    }
) {
    val colors = animatedColors(darkTheme)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalTt provides colors) {
        Box(
            modifier
                .fillMaxSize()
                .then(
                    if (drawOwnBackground) Modifier
                        .background(colors.bgBot)
                        .drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    0f to colors.bgTop, 0.4f to colors.bgMid, 1f to colors.bgBot,
                                    center = Offset(size.width / 2f, 0f), radius = size.height * 0.95f
                                )
                            )
                        }
                    else Modifier
                )
        ) {
            // Only when this screen owns its background (standalone use). Inside the app the shared global
            // background + starfield from MainActivity is used, so every tab looks the same (no per-tab tint).
            if (drawOwnBackground) CelestialBackdrop(darkTheme, drawStars = true)

            val entrance = rememberEntranceTracker()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding() + 104.dp
                )
            ) {
                item(key = "header") { header() }
                item(key = "profile") {
 Column(Modifier.loadIn(1, entrance)) { ProfileAndBreaks(state, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) 
 }
}
                item(key = "tabs") {
 Column(Modifier.loadIn(2, entrance)) {
                    DayTabs(state.days, state.selectedDayIndex, onSelectDay, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
                
 }
}
                item(key = "dayHeader") {
 Column(Modifier.loadIn(3, entrance)) {
                    val day = state.days.getOrNull(state.selectedDayIndex)
                    if (day != null) DayHeader(day, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
                
 }
}
                item(key = "timeline") {
 Column(Modifier.loadIn(4, entrance)) {
                    val day = state.days.getOrNull(state.selectedDayIndex)
                    if (day != null) {
                        AnimatedContent(
                            targetState = day,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                            label = "dayTimeline",
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)
                        ) { d -> Timeline(d.items, onPeriodClick, onViewAttendance) }
                    }
                
 }
}
                item(key = "workload") {
 Column(Modifier.loadIn(5, entrance)) {
                    WorkloadSection(state.workload, onViewMatrix, Modifier.padding(horizontal = 20.dp).padding(top = 24.dp))
                
 }
}
                item(key = "holidays") {
 Column(Modifier.loadIn(6, entrance)) {
                    HolidaysSection(state, onHolidayClick, onOpenAcademicCalendar, Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 16.dp))
                
 }
}
            }

            // Scroll-to-top FAB (floating bob)
            val bob = rememberInfiniteTransition(label = "fab").animateFloat(
                0f, -5f, infiniteRepeatable(tween(1750, easing = EaseInOut), RepeatMode.Reverse), label = "bob"
            )
            val fabSource = remember { MutableInteractionSource() }
            val fabPressed by fabSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(if (fabPressed) 0.9f else 1f, TactileSpring, label = "fabScale")
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 92.dp)
                    .graphicsLayer { translationY = bob.value.dp.toPx(); scaleX = fabScale; scaleY = fabScale }
                    .shadow(14.dp, CircleShape, ambientColor = Cyan400.copy(0.45f), spotColor = Cyan400.copy(0.45f))
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Sky500, Cyan500)))
                    .border(1.dp, Color.White.copy(0.4f), CircleShape)
                    .clickable(fabSource, null) { scope.launch { listState.animateScrollToItem(0) } },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ArrowUpward, "Scroll to top", tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("TOP", style = txt(8f, FontWeight.ExtraBold, spacing = -0.3f), color = Color.White)
                }
            }

        }
    }
}

// ───────────────────────────── HEADER (title + day/night toggle) ─────────────────────────────

@Composable
fun TimetableHeader(
    title: String,
    subtitle: String,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Shared header (same as Attendance / Reports / Settings).
    AppScreenHeader(
        title = title,
        subtitle = subtitle,
        isDark = darkTheme,
        onToggleTheme = { onToggleTheme() },
        modifier = modifier.padding(horizontal = 20.dp)
    )
}

// ───────────────────────────── PROFILE STRIP + BREAK CARDS ─────────────────────────────

@Composable
private fun ProfileAndBreaks(state: TimetableUiState, modifier: Modifier = Modifier) {
    val c = LocalTt.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().glass(c, CardShape).tactile().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Cyan500, Blue600)))
                        .border(1.dp, Color(0xFF67E8F9).copy(0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.School, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                Column {
                    Text(state.profileLine, style = txt(12f, FontWeight.SemiBold), color = c.heading)
                    Text(state.profileSub, style = txt(11f, FontWeight.Medium), color = c.sub)
                }
            }
            Text(
                state.semesterBadge, style = txt(10f, if (c.isDark) FontWeight.Medium else FontWeight.SemiBold),
                color = if (c.isDark) Color(0xFF67E8F9) else Color(0xFF155E75),
                modifier = Modifier.clip(CircleShape)
                    .background(if (c.isDark) Color(0xB3083344) else Color(0xFFCFFAFE))
                    .border(1.dp, if (c.isDark) Cyan500.copy(0.4f) else Color(0xFF67E8F9), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}


// ───────────────────────────── DAY TABS ─────────────────────────────

@Composable
private fun DayTabs(days: List<DayScheduleUi>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = com.example.ui.settings.rememberAppHaptics()
    val c = LocalTt.current
    Row(modifier.fillMaxWidth().glass(c, CardShape).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { i, d ->
            val active = i == selected
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(
                when { pressed -> 0.94f; active -> 1.05f; else -> 1f }, TactileSpring, label = "tabScale"
            )
            val textColor by animateColorAsState(if (active) Color.White else c.inactiveTab, tween(300), label = "tabText")
            val bg1 by animateColorAsState(if (active) Sky500 else Color.Transparent, tween(300), label = "tb1")
            val bg2 by animateColorAsState(if (active) Cyan500 else Color.Transparent, tween(300), label = "tb2")
            val bd by animateColorAsState(if (active) Color(0xFFA5F3FC).copy(0.6f) else Color.Transparent, tween(300), label = "tbd")
            Box(
                Modifier.weight(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(if (active) Modifier.shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Sky500.copy(0.4f), spotColor = Sky500.copy(0.4f)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(bg1, bg2)))
                    .border(1.dp, bd, RoundedCornerShape(12.dp))
                    .clickable(source, null) { if (i != selected) haptics.tick(); onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) { Text(d.shortLabel, style = txt(12f, FontWeight.Bold), color = textColor) }
        }
    }
}

// ───────────────────────────── DAY HEADER + PILLS ─────────────────────────────

@Composable
private fun DayHeader(day: DayScheduleUi, modifier: Modifier = Modifier) {
    val c = LocalTt.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(day.fullTitle, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }, label = "dayTitle") {
            Text(it, style = txt(18f, FontWeight.Bold, spacing = -0.3f), color = c.title)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(day.periodsLabel, Icons.Rounded.Schedule, accent = false, iconTint = Cyan400)
            Pill(day.practicalLabel, Icons.Rounded.Science, accent = true, iconTint = Cyan400)
            Pill(day.freeLabel, null, accent = false, iconTint = c.muted, dim = true)
        }
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector?, accent: Boolean, iconTint: Color, dim: Boolean = false) {
    val c = LocalTt.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.tactile(pressedScale = 0.96f).clip(shape)
            .background(if (accent) c.pillAccentFill else c.pillFill)
            .border(1.dp, if (accent) c.pillAccentBorder else c.pillBorder, shape)
            .padding(horizontal = if (icon == null) 6.dp else 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, tint = if (accent) c.pillAccentText else iconTint, modifier = Modifier.size(10.dp))
        Text(label, style = txt(10f, FontWeight.SemiBold),
            color = if (accent) c.pillAccentText else if (dim) c.muted else c.pillText)
    }
}

// ───────────────────────────── TIMELINE ─────────────────────────────

@Composable
private fun Timeline(
    items: List<TimelineItemUi>,
    onPeriodClick: (String) -> Unit,
    onViewAttendance: (String) -> Unit
) {
    // expanded ids; every card (including labs / seminars) starts collapsed - the user opens what they want
    var expanded by rememberSaveable(items.map { it.key }) {
        mutableStateOf<List<String>>(emptyList())
    }
    Column(
        Modifier.fillMaxWidth().drawBehind {
            val x = 18.dp.toPx()
            drawLine(
                Brush.verticalGradient(listOf(Color(0xFF0284C7), Sky400, Color(0xFF818CF8))),
                Offset(x, 18.dp.toPx()), Offset(x, size.height - 18.dp.toPx()),
                strokeWidth = 2.dp.toPx(), alpha = 0.6f
            )
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            when (item) {
                is TimelineItemUi.BreakItem -> BreakRow(item)
                is TimelineItemUi.PeriodItem -> PeriodRow(
                    item = item,
                    expanded = item.key in expanded,
                    onToggle = {
                        expanded = if (item.key in expanded) expanded - item.key else expanded + item.key
                        onPeriodClick(item.key)
                    },
                    onViewAttendance = { onViewAttendance(item.key) }
                )
            }
        }
    }
}

@Composable
private fun TimelineNode(label: String, hero: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3000, easing = EaseInOut)), label = "p"
    )
    Box(
        Modifier.size(36.dp)
            .drawBehind {
                val spread = 6.dp.toPx() * pulse.value
                drawCircle(Sky400.copy(0.5f * (1f - pulse.value)), radius = size.minDimension / 2f + spread)
            }
            .clip(CircleShape)
            .background(
                if (hero) Brush.linearGradient(listOf(Sky500, Indigo600))
                else Brush.verticalGradient(listOf(Sky400, Blue600))
            )
            .border(2.dp, Color.White.copy(0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(label, style = txt(12f, FontWeight.Bold), color = Color.White) }
}

@Composable
private fun BreakRow(item: TimelineItemUi.BreakItem) {
    val c = LocalTt.current
    val water = item.kind == BreakKind.Water
    val accent = if (water) Cyan400 else Amber500
    val pulse = rememberInfiniteTransition(label = "bp").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3000, easing = EaseInOut)), label = "bpv"
    )
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val x = 18.dp.toPx()
            drawLine(
                Sky400.copy(0.45f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
        },
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(32.dp)
                    .drawBehind { drawCircle(accent.copy(0.45f * (1f - pulse.value)), size.minDimension / 2f + 6.dp.toPx() * pulse.value) }
                    .clip(CircleShape).background(c.breakNodeFill)
                    .border(1.dp, accent.copy(0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (water) Icons.Rounded.WaterDrop else Icons.Rounded.Restaurant, null,
                    tint = if (water) (if (c.isDark) Color(0xFF67E8F9) else Cyan500) else Amber500, modifier = Modifier.size(12.dp))
            }
        }
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.weight(1f).tactile(pressedScale = 0.99f).clip(shape)
                .background(if (water) c.waterFill else c.lunchFill)
                .border(1.dp, if (water) c.waterBorder else c.lunchBorder, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            val col = if (water) c.waterText else c.lunchText
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (water) Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan500))
                Text(if (water) "Water Break" else "Lunch Break", style = txt(12f, FontWeight.SemiBold), color = col)
            }
            Text(item.timeLabel, style = txt(11f, FontWeight.SemiBold, mono = true), color = col)
        }
    }
}

@Composable
private fun PeriodRow(
    item: TimelineItemUi.PeriodItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onViewAttendance: () -> Unit
) {
    val c = LocalTt.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        TimelineNode(item.number.toString(), item.isHighlight)
        if (item.isHighlight) HeroPeriodCard(item, expanded, onToggle, onViewAttendance, Modifier.weight(1f))
        else StandardPeriodCard(item, expanded, onToggle, onViewAttendance, Modifier.weight(1f))
    }
}

@Composable
private fun PeriodChip(text: String, hero: Boolean) {
    val c = LocalTt.current
    val shape = if (hero) CircleShape else RoundedCornerShape(6.dp)
    Text(
        text, style = txt(10f, if (hero) FontWeight.Bold else FontWeight.SemiBold, mono = true, spacing = 0.3f),
        color = c.chipText,
        modifier = Modifier.clip(shape).background(c.chipFill).border(1.dp, c.chipBorder, shape)
            .padding(horizontal = if (hero) 10.dp else 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun SubjectIconBox(icon: SubjectIcon, size: Dp, iconSize: Dp) {
    val c = LocalTt.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.iconBoxFill)
            .border(1.dp, c.iconBoxBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon.vector, null, tint = c.iconTint, modifier = Modifier.size(iconSize)) }
}

@Composable
private fun StandardPeriodCard(
    item: TimelineItemUi.PeriodItem, expanded: Boolean, onToggle: () -> Unit,
    onViewAttendance: () -> Unit, modifier: Modifier
) {
    val c = LocalTt.current
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "chev")
    val teacherAbbr = remember(item.faculty) {
        item.faculty?.let { f ->
            val match = Regex("\\(([^)]+)\\)").find(f)
            match?.groupValues?.get(1) ?: f
        }
    }
    Column(modifier.glass(c, CardShape).tactile(onToggle).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PeriodChip("Period ${item.number}", hero = false)
            Text(item.timeLabel, style = txt(11f, FontWeight.Medium, mono = true), color = c.muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SubjectIconBox(item.icon, 40.dp, 16.dp)
            Column(Modifier.weight(1f)) {
                Text(item.subjectName, style = txt(14f, FontWeight.Bold, spacing = -0.2f), color = c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.type.label, style = txt(12f), color = c.muted)
                    Text("•", style = txt(12f), color = Cyan500)
                    Text(item.subjectCode, style = txt(12f), color = c.muted)
                    if (!teacherAbbr.isNullOrBlank()) {
                        Text("•", style = txt(12f), color = Cyan500)
                        Text(teacherAbbr, style = txt(12f, FontWeight.SemiBold), color = if (c.isDark) Color(0xFF67E8F9) else Color(0xFF0891B2))
                    }
                }
            }
            Icon(
                if (item.hasDetails) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp).rotate(if (item.hasDetails) chevron else 0f)
            )
        }
        if (item.hasDetails) {
            AnimatedVisibility(
                expanded,
                enter = expandVertically(tween(350)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(350)) + fadeOut(tween(200))
            ) { DetailsDrawer(item, onViewAttendance) }
        }
    }
}

@Composable
private fun HeroPeriodCard(
    item: TimelineItemUi.PeriodItem, expanded: Boolean, onToggle: () -> Unit,
    onViewAttendance: () -> Unit, modifier: Modifier
) {
    val c = LocalTt.current
    val chevron by animateFloatAsState(if (expanded) 0f else 180f, tween(300), label = "heroChev")
    val teacherAbbr = remember(item.faculty) {
        item.faculty?.let { f ->
            val match = Regex("\\(([^)]+)\\)").find(f)
            match?.groupValues?.get(1) ?: f
        }
    }
    Column(modifier.glass(c, HeroShape, hero = true).tactile(onToggle).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PeriodChip("Period ${item.number}", hero = true)
            Text(item.timeLabel, style = txt(11f, FontWeight.Bold, mono = true, spacing = -0.3f), color = if (c.isDark) Color(0xFFA5F3FC) else Color(0xFF155E75))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (c.isDark) Color(0xCC083344) else Cyan500.copy(0.2f))
                    .border(1.dp, Cyan400.copy(0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(item.icon.vector, null, tint = c.iconTint, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(item.subjectName, style = txt(14f, FontWeight.ExtraBold, spacing = -0.2f).copy(lineHeight = 18.sp), color = c.title)
                // Tag and credit badge are stacked: side by side they squeezed the badge to ~0 width, so its text
                // wrapped one character per line and rendered as a tall blue bar (e.g. Department Seminar).
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${item.type.label} • ${item.subjectCode}${if (!teacherAbbr.isNullOrBlank()) " • $teacherAbbr" else ""}",
                        style = txt(10f, FontWeight.Bold),
                        color = if (c.isDark) Color(0xFF67E8F9) else Color(0xFF1E293B),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(if (c.isDark) Color(0xE61E293B) else Color(0xCCE2E8F0))
                            .border(1.dp, if (c.isDark) Cyan500.copy(0.3f) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    if (!item.highlightLabel.isNullOrBlank()) {
                        Row(
                            Modifier.clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Blue600, Cyan500)))
                                .border(1.dp, Color.White.copy(0.3f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Bolt, null, tint = Color(0xFFFCD34D), modifier = Modifier.size(10.dp))
                            Text(
                                item.highlightLabel.orEmpty(),
                                style = txt(10f, FontWeight.ExtraBold, spacing = 0.3f),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            Icon(Icons.Rounded.ExpandMore, "Toggle details", tint = if (c.isDark) Color(0xFF67E8F9) else Color(0xFF0891B2),
                modifier = Modifier.size(20.dp).rotate(chevron))
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(tween(350)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(350)) + fadeOut(tween(200))
        ) { DetailsDrawer(item, onViewAttendance) }
    }
}

@Composable
private fun DetailsDrawer(item: TimelineItemUi.PeriodItem, onViewAttendance: () -> Unit) {
    val c = LocalTt.current
    val safe = (item.attendancePercent ?: 0) >= item.attendanceSafeAt
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .drawBehind { drawLine(Sky400.copy(0.25f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(top = 12.dp)
    ) {
        val rows = buildList<List<Triple<String, String, Color?>>> {
            // Row 1: Full Name & Subject Code
            add(
                listOf(
                    Triple("Full Name", item.fullName ?: item.subjectName, null),
                    Triple("Subject Code", item.subjectCode, null)
                )
            )
            // Row 2: Session Type & Real Faculty Incharge
            add(
                listOf(
                    Triple("Session Type", item.sessionLabel ?: item.type.label, c.iconTint),
                    Triple("Faculty Incharge", item.faculty ?: "—", null)
                )
            )
            // Optional Supporting Staff Row (shown for practical lab blocks)
            if (!item.supportingStaff.isNullOrBlank()) {
                add(
                    listOf(
                        Triple("Supporting Staff", item.supportingStaff, null)
                    )
                )
            }
            // Row 3: Show "Lab Name" ONLY if `item.venue != null` ( strictly WFM, CPT-II, Textile Testing )
            val attendanceStatusTriple = Triple(
                "Attendance Status",
                item.attendancePercent?.let { "$it% (${if (safe) "Safe" else "Low"})" } ?: "—",
                if (item.attendancePercent == null) null else if (safe) Emerald500 else Rose500
            )
            if (item.venue != null) {
                add(
                    listOf(
                        Triple("Lab Name", item.venue, c.iconTint),
                        attendanceStatusTriple
                    )
                )
            } else {
                add(listOf(attendanceStatusTriple))
            }
        }

        rows.forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                pair.forEach { (label, value, tint) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = txt(10f), color = c.muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (label == "Attendance Status" && tint == Emerald500)
                                Icon(Icons.Rounded.CheckCircle, null, tint = Emerald500, modifier = Modifier.size(10.dp))
                            Text(
                                value,
                                style = txt(11f, if (label == "Subject Code") FontWeight.SemiBold else FontWeight.Medium, mono = label == "Subject Code"),
                                color = tint ?: c.heading
                            )
                        }
                    }
                }
            }
        }

        val btnShape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .tactile(onViewAttendance)
                .clip(btnShape)
                .background(if (c.isDark) Color(0x66083344) else Color(0xE6CFFAFE))
                .border(1.dp, if (c.isDark) Cyan400.copy(0.5f) else Color(0xFF67E8F9), btnShape)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val col = if (c.isDark) Color(0xFFA5F3FC) else Color(0xFF155E75)
            Icon(Icons.Rounded.EventAvailable, null, tint = col, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (item.type == SessionType.Lab) "View Lab Attendance" else "View Attendance",
                style = txt(12f, FontWeight.SemiBold),
                color = col
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ArrowForward, null, tint = col.copy(0.7f), modifier = Modifier.size(11.dp))
        }
    }
}

// ───────────────────────────── WORKLOAD SUMMARY ─────────────────────────────

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val c = LocalTt.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.invoke()
            Text(title, style = txt(14f, FontWeight.Bold, spacing = 0.3f), color = c.title)
        }
        Row(Modifier.tactile(onAction, 0.95f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(action, style = txt(11f, FontWeight.SemiBold), color = c.sub)
            Icon(Icons.Rounded.ArrowForward, null, tint = c.sub.copy(0.7f), modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun WorkloadSection(w: WorkloadUi, onViewMatrix: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTt.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("Weekly Workload Summary", "View Matrix", onViewMatrix)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(Modifier.weight(1f), Icons.Rounded.CalendarMonth, Blue500, "Total Periods", w.totalPeriods.toString(), null)
                MetricCard(Modifier.weight(1f), Icons.Rounded.Science, Color(0xFF14B8A6), "Practical Sessions", w.practicalSessions.toString(), null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(Modifier.weight(1f), Icons.Rounded.HourglassBottom, Amber500, "Practical Hours", w.practicalHours.toString(), "hrs")
                MetricCard(Modifier.weight(1f), Icons.Rounded.LocalCafe, Purple500, "Free Periods", w.freePeriods.toString(), null)
            }
        }
        DistributionCard(w)
    }
}

@Composable
private fun MetricCard(modifier: Modifier, icon: ImageVector, tint: Color, label: String, value: String, unit: String?) {
    val c = LocalTt.current
    Row(
        modifier.glass(c, CardShape).tactile().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(0.25f))
                .border(1.dp, tint.copy(0.4f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = if (tint == Blue500) Color(0xFF60A5FA) else tint, modifier = Modifier.size(18.dp)) }
        Column {
            Text(label, style = txt(10f, FontWeight.Medium), color = c.muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = txt(18f, FontWeight.ExtraBold, spacing = -0.3f), color = c.title)
                if (unit != null) Text(" $unit", style = txt(12f), color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun DistributionCard(w: WorkloadUi) {
    val c = LocalTt.current
    val palette = listOf(Blue500, Purple500, Emerald500, Amber500)
    val slices = w.distribution.take(4)
    val total = slices.sumOf { it.count }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().glass(c, CardShape).tactile().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subject Distribution", style = txt(11f, FontWeight.Bold), color = c.title)
            Text(w.distributionCaption, style = txt(10f, mono = true), color = c.muted)
        }
        Row(
            Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
                .background(if (c.isDark) Color(0xCC0F172A) else Color(0x99E2E8F0)).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            slices.forEachIndexed { i, s ->
                val weight by animateFloatAsState(s.count.toFloat() / total, tween(700), label = "dist$i")
                if (weight > 0f) Box(
                    Modifier.weight(weight).fillMaxHeight().clip(
                        RoundedCornerShape(
                            topStart = if (i == 0) 6.dp else 0.dp, bottomStart = if (i == 0) 6.dp else 0.dp,
                            topEnd = if (i == slices.lastIndex) 6.dp else 0.dp, bottomEnd = if (i == slices.lastIndex) 6.dp else 0.dp
                        )
                    ).background(palette[i])
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(palette[i]))
                    Text("${s.label} ${s.count}", style = txt(10f), color = c.muted)
                }
            }
        }
    }
}

// ───────────────────────────── HOLIDAYS ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HolidaysSection(
    state: TimetableUiState, onHolidayClick: (HolidayUi) -> Unit,
    onOpenCalendar: () -> Unit, modifier: Modifier = Modifier
) {
    val c = LocalTt.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Upcoming Holidays", "Academic Calendar", onOpenCalendar) {
            Icon(Icons.Rounded.Cake, null, tint = Amber500, modifier = Modifier.size(13.dp))
        }
        state.featuredHoliday?.let { h ->
            val shape = CardShape
            Row(
                Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
                    .tactile({ onHolidayClick(h) }).clip(shape)
                    .background(if (c.isDark) Color(0xB30F172A) else Color(0xCCF5F6FF))
                    .border(1.dp, if (c.isDark) Amber500.copy(0.3f) else Color(0xCCFCD34D), shape)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Amber500.copy(0.25f))
                            .border(1.dp, Amber500.copy(0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.CalendarToday, null, tint = Amber500, modifier = Modifier.size(16.dp)) }
                    Column {
                        Text(h.name, style = txt(14f, FontWeight.Bold, spacing = -0.2f), color = c.title)
                        Text(h.dateLabel, style = txt(12f), color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (h.countdownLabel != null) {
                    val amberText = if (c.isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                    Row(
                        Modifier.clip(CircleShape).background(Amber500.copy(0.15f))
                            .border(1.dp, Amber500.copy(0.4f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(h.countdownLabel, style = txt(12f, FontWeight.Bold), color = amberText)
                        Icon(Icons.Rounded.ChevronRight, null, tint = amberText, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        if (state.moreHolidays.isNotEmpty()) {
            Text("More Holidays", style = txt(11f, FontWeight.SemiBold), color = c.muted, modifier = Modifier.padding(top = 4.dp))
            state.moreHolidays.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { h ->
                        Column(Modifier.weight(1f).glass(c, SmallCardShape).tactile({ onHolidayClick(h) }).padding(8.dp)) {
                            Text(h.name, style = txt(10f, FontWeight.Bold), color = c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(h.dateLabel, style = txt(10f, mono = true), color = c.muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ───────────────────────────── ATMOSPHERE (stars, meteors, day mist) ─────────────────────────────

private data class Star(val xFrac: Float, val yDp: Float, val rDp: Float, val color: Color, val periodMs: Int)

private val Stars = listOf(
    Star(0.12f, 48f, 2f, Color.White, 3200), Star(0.44f, 80f, 3f, Color(0xFFA5F3FC), 2200),
    Star(0.74f, 36f, 2f, Color.White, 4200), Star(0.86f, 112f, 1f, Color.White, 3200),
    Star(0.26f, 160f, 2f, Color(0xFFBFDBFE), 4200), Star(0.60f, 192f, 2f, Color(0xFFCFFAFE), 2200),
    Star(0.75f, 64f, 1f, Color.White, 3200), Star(0.18f, 256f, 2f, Color(0xFFC7D2FE), 4200),
    Star(0.80f, 288f, 3f, Color(0xFFBAE6FD), 2200), Star(0.34f, 384f, 2f, Color.White, 3200),
    Star(0.70f, 480f, 1f, Color(0xFFCFFAFE), 4200), Star(0.14f, 576f, 2f, Color(0xFFDBEAFE), 2200),
    Star(0.88f, 704f, 2f, Color.White, 3200)
)

@Composable
private fun CelestialBackdrop(dark: Boolean, drawStars: Boolean) {
    val nightAlpha by animateFloatAsState(if (dark) 1f else 0f, tween(500), label = "nightA")
    val dayAlpha by animateFloatAsState(if (dark) 0f else 1f, tween(500), label = "dayA")
    val inf = rememberInfiniteTransition(label = "sky")
    val density = LocalDensity.current

    val twinkles = Stars.mapIndexed { i, s ->
        inf.animateFloat(0.2f, 0.85f, infiniteRepeatable(tween(s.periodMs / 2, easing = EaseInOut), RepeatMode.Reverse, StartOffset(i * 180)), label = "tw$i")
    }
    val meteorSpecs = listOf(Triple(0.86f, 8f, 4800 to 800), Triple(0.90f, 28f, 5200 to 3200), Triple(0.78f, 56f, 4900 to 6400))
    val meteors = meteorSpecs.mapIndexed { i, (_, _, p) ->
        val dur = p.first
        inf.animateFloat(
            0f, 1f,
            InfiniteRepeatableSpec(
                keyframes { durationMillis = dur; 0f at 0; 1f at (dur * 0.26f).toInt() using EaseIn; 1f at dur },
                initialStartOffset = StartOffset(p.second)
            ), label = "m$i"
        )
    }
    val mist = inf.animateFloat(0f, 12f, infiniteRepeatable(tween(6000, easing = EaseInOut), RepeatMode.Reverse), label = "mist")

    Canvas(Modifier.fillMaxSize()) {
        val dp = density.density
        if (drawStars && nightAlpha > 0f) {
            drawCircle(Brush.radialGradient(listOf(Color(0x33083344).copy(0.2f * nightAlpha), Color.Transparent), Offset(size.width / 2, -20 * dp), 190 * dp),
                radius = 190 * dp, center = Offset(size.width / 2, -20 * dp))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1E1B4B).copy(0.2f * nightAlpha), Color.Transparent), Offset(size.width, 560 * dp), 160 * dp),
                radius = 160 * dp, center = Offset(size.width, 560 * dp))
            Stars.forEachIndexed { i, s ->
                val a = twinkles[i].value * nightAlpha
                val center = Offset(s.xFrac * size.width, s.yDp * dp)
                drawCircle(s.color.copy(a), s.rDp * dp * (0.9f + 0.25f * twinkles[i].value), center)
                if (s.rDp >= 2f) drawCircle(Cyan400.copy(a * 0.25f), s.rDp * dp * 2.4f, center)
            }
            val ang = Math.toRadians(35.0)
            val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
            meteorSpecs.forEachIndexed { i, (xf, yd, _) ->
                val p = meteors[i].value
                val fade = when { p < 0.38f -> p / 0.38f; else -> (1f - (p - 0.38f) / 0.62f) }.coerceIn(0f, 1f) * 0.9f * nightAlpha
                if (p > 0f && p < 1f) {
                    val head = Offset(xf * size.width - dx * 380 * dp * p, yd * dp * 4f + dy * 380 * dp * p)
                    val len = (120 + 20 * p) * dp
                    val tail = Offset(head.x + dx * len, head.y - dy * len)
                    drawLine(
                        Brush.linearGradient(listOf(Color.White.copy(fade), Sky400.copy(fade * 0.6f), Color.Transparent), head, tail),
                        head, tail, strokeWidth = 1.5f * dp, cap = StrokeCap.Round
                    )
                }
            }
        }
        if (dayAlpha > 0f) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFDE68A).copy(0.45f * dayAlpha), Color.Transparent), Offset(size.width, 20 * dp), 170 * dp),
                radius = 170 * dp, center = Offset(size.width, 20 * dp))
            val m = mist.value * dp
            drawCircle(Brush.radialGradient(listOf(Color(0xFFE0F2FE).copy(0.7f * dayAlpha), Color.Transparent), Offset(40 * dp + m, 140 * dp), 120 * dp),
                radius = 120 * dp, center = Offset(40 * dp + m, 140 * dp))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.85f * dayAlpha), Color.Transparent), Offset(size.width - 60 * dp - m, 420 * dp), 140 * dp),
                radius = 140 * dp, center = Offset(size.width - 60 * dp - m, 420 * dp))
        }
    }
}

