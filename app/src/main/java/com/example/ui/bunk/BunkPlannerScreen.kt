package com.example.ui.bunk

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.bunk.BunkOption
import com.example.data.bunk.BunkOverview
import com.example.data.bunk.BunkPlannerResult
import com.example.data.bunk.NoSafeReason
import com.example.data.bunk.SafetyTier
import com.example.data.bunk.SubjectStatus
import com.example.ui.components.StarfieldCanvas
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import com.example.ui.settings.rememberMotionEnabled
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Stateful host: reads the ViewModel (read-only) and owns the list/details back stack. */
@Composable
fun BunkPlannerScreen(viewModel: AttendanceViewModel) {
    val glass = LocalGlassTheme.current
    val profile by viewModel.profile.collectAsState()
    val result by viewModel.bunkPlannerResult.collectAsState()
    var detailRank by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler {
        if (detailRank != null) detailRank = null else viewModel.closeBunkPlanner()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(glass.canvasStart, glass.canvasEnd)))
    ) {
        ProvideLiveMotion(true) {
            AuroraBackground()
            if (glass.isDark && profile.starfieldAnimationEnabled) {
                StarfieldCanvas(modifier = Modifier.fillMaxSize(), enabled = true)
            }
        }
        BunkPlannerContent(
            result = result,
            detailRank = detailRank,
            onOpenDetails = { detailRank = it },
            onCloseDetails = { detailRank = null },
            onBack = { viewModel.closeBunkPlanner() },
            onRefresh = { detailRank = null; viewModel.recalculateBunkPlanner() }
        )
    }
}

/**
 * Stateless content so it can be previewed and UI-tested with a fixed [BunkPlannerResult].
 * [liveMotion] controls decorative endless animations only (tests pass false).
 */
@Composable
fun BunkPlannerContent(
    result: BunkPlannerResult?,
    detailRank: Int?,
    onOpenDetails: (Int) -> Unit,
    onCloseDetails: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    liveMotion: Boolean = true
) {
    val motion = rememberMotionEnabled() && liveMotion
    val ready = result as? BunkPlannerResult.Ready
    val detailOption = detailRank?.let { r -> ready?.options?.firstOrNull { it.rank == r } }
    ProvideLiveMotion(liveMotion) {
        AnimatedContent(
            targetState = detailOption?.rank,
            transitionSpec = {
                if (!motion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val dir = if (targetState != null) 1 else -1
                    (fadeIn(tween(300, delayMillis = 70)) +
                        scaleIn(tween(360, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { dir * it / 6 }) togetherWith
                        (fadeOut(tween(160)) +
                            scaleOut(tween(260), targetScale = 0.97f) +
                            slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -dir * it / 8 })
                }
            },
            label = "bunkPlannerNav"
        ) { rank ->
            val option = rank?.let { r -> ready?.options?.firstOrNull { it.rank == r } }
            if (option != null && ready != null) {
                BunkOptionDetails(option = option, overview = ready.overview, onBack = onCloseDetails)
            } else {
                BunkPlannerList(result, onOpenDetails, onBack, onRefresh)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

@Composable
private fun BunkPlannerList(
    result: BunkPlannerResult?,
    onOpenDetails: (Int) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val tracker = rememberEntranceTracker()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("bunk_planner_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PlannerHeader(result?.overview?.monthLabel, onBack, onRefresh) }
        when (result) {
            null -> item { LoadingState() }
            is BunkPlannerResult.Ready -> {
                item { Box(Modifier.loadIn(0, tracker)) { OverviewCard(result.overview, result.options) } }
                result.overview.takeIf { it.isProvisional || it.belowTargetNow }?.let { ov ->
                    item { Box(Modifier.loadIn(1, tracker)) { WarningCard(ov) } }
                }
                item { Box(Modifier.loadIn(2, tracker)) { AssumptionNote(result.overview) } }
                item { Box(Modifier.loadIn(2, tracker, key = 22)) { SectionLabel(result.options.size) } }
                result.options.forEachIndexed { i, option ->
                    item(key = "opt_${option.rank}") {
                        Box(Modifier.loadIn(3 + i, tracker, key = 3 + i)) {
                            RecommendationCard(option) { onOpenDetails(option.rank) }
                        }
                    }
                }
                if (result.options.size < 3) {
                    item {
                        Text(
                            "Only ${result.options.size} safe option${if (result.options.size == 1) "" else "s"} found this month. " +
                                "No other full-day leave keeps projected attendance at or above your targets.",
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textMuted,
                            modifier = Modifier.testTag("bunk_fewer_note")
                        )
                    }
                }
            }
            is BunkPlannerResult.NoSafeOption -> {
                item { Box(Modifier.loadIn(0, tracker)) { OverviewCard(result.overview, emptyList()) } }
                item { Box(Modifier.loadIn(1, tracker)) { NoSafeState(result) } }
            }
            is BunkPlannerResult.InsufficientData -> {
                result.overview?.let { ov -> item { Box(Modifier.loadIn(0, tracker)) { OverviewCard(ov, emptyList()) } } }
                item {
                    Box(Modifier.loadIn(1, tracker)) {
                        MessageState(
                            icon = Icons.Default.Info,
                            title = "Not enough data yet",
                            body = result.message,
                            accent = glass.amberGold,
                            tag = "bunk_insufficient"
                        )
                    }
                }
            }
            is BunkPlannerResult.NoRemainingClasses -> {
                result.overview?.let { ov -> item { Box(Modifier.loadIn(0, tracker)) { OverviewCard(ov, emptyList()) } } }
                item {
                    Box(Modifier.loadIn(1, tracker)) {
                        MessageState(
                            icon = Icons.Rounded.CalendarToday,
                            title = "No classes left this month",
                            body = "There are no remaining scheduled classes before month end, so there is nothing to plan. Check back next month.",
                            accent = glass.royalIndigo,
                            tag = "bunk_no_remaining"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    description: String,
    tag: String,
    rotation: Float = 0f,
    onClick: () -> Unit
) {
    val glass = LocalGlassTheme.current
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(glass.cardSurface)
            .border(1.dp, glass.cardBorder, CircleShape)
            .pressScale(onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = glass.textPrimary, modifier = Modifier.graphicsLayer { rotationZ = rotation })
    }
}

@Composable
private fun PlannerHeader(monthLabel: String?, onBack: () -> Unit, onRefresh: () -> Unit) {
    val glass = LocalGlassTheme.current
    val haptic = LocalHapticFeedback.current
    var spins by remember { mutableStateOf(0) }
    val rotation by animateFloatAsState(spins * 360f, tween(650, easing = FastOutSlowInEasing), label = "refreshSpin")
    val reveal = rememberReveal(0L, 600)
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", "bunk_back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Default.Refresh, "Recalculate", "bunk_refresh", rotation = rotation) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                spins += 1
                onRefresh()
            }
        }
        Column(
            Modifier.graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 14.dp.toPx() },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Bunk Planner",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(glass.royalIndigo, glass.electricViolet, glass.electricTeal)),
                    fontFamily = OutfitFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold
                )
            )
            Text(
                "Your top 3 safest full-day breaks" + (if (monthLabel != null) " for $monthLabel" else ""),
                fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp, color = glass.textSecondary
            )
        }
    }
}

@Composable
private fun SectionLabel(count: Int) {
    val glass = LocalGlassTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = glass.electricViolet, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Recommended breaks", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = glass.textPrimary)
        Spacer(Modifier.weight(1f))
        BunkChip("$count found", glass.electricViolet)
    }
}

// ---------------------------------------------------------------------------------------------
// Loading skeleton
// ---------------------------------------------------------------------------------------------

@Composable
private fun LoadingState() {
    val glass = LocalGlassTheme.current
    val live = rememberLiveMotion()
    val steps = listOf("Reading your attendance…", "Checking timetable and holidays…", "Ranking the safest breaks…")
    var step by remember { mutableStateOf(0) }
    if (live) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1100)
                step = (step + 1) % steps.size
            }
        }
    }
    Column(Modifier.testTag("bunk_loading"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BunkGlassCard(accent = glass.royalIndigo, accent2 = glass.electricViolet) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(104.dp), corner = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(Modifier.width(150.dp).height(14.dp))
                    ShimmerBox(Modifier.width(110.dp).height(14.dp))
                    ShimmerBox(Modifier.width(170.dp).height(12.dp))
                }
            }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 2 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 })
            },
            label = "loadingStep"
        ) { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = glass.royalIndigo)
                Spacer(Modifier.width(10.dp))
                Text(steps[s], fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = glass.textSecondary)
            }
        }
        repeat(2) {
            BunkGlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(Modifier.size(38.dp), corner = 19.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShimmerBox(Modifier.width(90.dp).height(11.dp))
                            ShimmerBox(Modifier.width(160.dp).height(16.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(5) { ShimmerBox(Modifier.width(48.dp).height(66.dp), corner = 15.dp) }
                    }
                    ShimmerBox(Modifier.fillMaxWidth().height(44.dp), corner = 16.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------------------------

@Composable
private fun OverviewCard(o: BunkOverview, options: List<BunkOption>) {
    val glass = LocalGlassTheme.current
    val cur = o.currentPercent
    val ringColor = if (cur != null) pctColor(glass, cur, o.examTarget, o.stipendTarget) else glass.textMuted
    BunkGlassCard(Modifier.testTag("bunk_overview"), accent = glass.royalIndigo, accent2 = glass.electricViolet) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttendanceRing(percent = cur, target = o.requiredTarget, color = ringColor, diameter = 116.dp, caption = "attendance now")
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${o.monthLabel} so far", fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target ", fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp, color = glass.textSecondary)
                        Text("${o.requiredTarget}%", fontFamily = OutfitFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                    }
                    Text(
                        "Exam ${o.examTarget}% · Stipend ${o.stipendTarget}% · buffer ${o.safetyBuffer} pts",
                        fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textMuted
                    )
                    if (cur != null) {
                        val margin = cur - o.requiredTarget
                        BunkChip(
                            "${signed(margin)} pts ${if (margin >= 0) "above" else "below"} target",
                            if (margin >= 0) glass.emeraldNeon else glass.crimsonRose
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Classes left", o.remainingSessions.toString(), glass.royalIndigo, Modifier.weight(1f))
                StatTile("Leave-eligible days", o.bunkEligibleDays.toString(), glass.electricViolet, Modifier.weight(1f))
                StatTile("Working days left", o.remainingWorkingDays.toString(), glass.electricTeal, Modifier.weight(1f))
            }
            if (options.isNotEmpty()) MonthStrip(o, options)
        }
    }
}

/** Compact calendar of the rest of the month showing where the three breaks fall. */
@Composable
private fun MonthStrip(o: BunkOverview, options: List<BunkOption>) {
    val glass = LocalGlassTheme.current
    val f = rememberReveal(350L, 800)
    val days = remember(o.horizonStart, o.horizonEnd) {
        generateSequence(o.horizonStart) { it.plusDays(1) }.takeWhile { !it.isAfter(o.horizonEnd) }.toList()
    }
    if (days.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Where your breaks fall", fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = glass.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val bunkOf = options.firstOrNull { d in it.bunkDates }
                val spanOf = if (bunkOf == null) options.firstOrNull { !d.isBefore(it.start) && !d.isAfter(it.end) } else null
                val color = when {
                    bunkOf != null -> rankAccent(glass, bunkOf.rank)
                    spanOf != null -> rankAccent(glass, spanOf.rank).copy(alpha = 0.35f)
                    d.dayOfWeek.value > 5 -> glass.textMuted.copy(alpha = 0.25f)
                    else -> glass.cardBorder
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height((8f + 18f * f).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${o.horizonStart.dayOfMonth} ${shortMonth(o.horizonStart)}", fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = glass.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { opt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(rankAccent(glass, opt.rank)))
                        Spacer(Modifier.width(3.dp))
                        Text("#${opt.rank}", fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = glass.textMuted)
                    }
                }
            }
            Text("${o.horizonEnd.dayOfMonth} ${shortMonth(o.horizonEnd)}", fontFamily = PlusJakartaSansFontFamily, fontSize = 10.sp, color = glass.textMuted)
        }
    }
}

@Composable
private fun WarningCard(o: BunkOverview) {
    val glass = LocalGlassTheme.current
    BunkGlassCard(Modifier.testTag("bunk_warning"), accent = glass.amberGold, accent2 = glass.crimsonRose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (o.isProvisional) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroIconSmall(Icons.Default.Info, glass.amberGold)
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Provisional recommendations", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = glass.amberGold)
                        Text(
                            "${o.unmarkedPastSessions} past ${if (o.unmarkedPastSessions == 1) "class is" else "classes are"} still unmarked and " +
                                "not counted. Mark them in Attendance for a definitive result. Until then these options are not guaranteed safe.",
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textSecondary
                        )
                    }
                }
            }
            if (o.belowTargetNow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroIconSmall(Icons.Default.Block, glass.crimsonRose)
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("You are currently below your target", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = glass.crimsonRose)
                        Text(
                            "Only options whose full month-end projection reaches the target are shown.",
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroIconSmall(icon: ImageVector, color: Color) {
    val pulse = rememberLoop(1700, fallback = 0f)
    Box(
        Modifier
            .size(34.dp)
            .graphicsLayer { val s = 1f + 0.08f * pulse.value; scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun AssumptionNote(o: BunkOverview) {
    val glass = LocalGlassTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glass.royalIndigo.copy(alpha = 0.08f))
            .border(1.dp, glass.royalIndigo.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("bunk_assumption"),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = glass.royalIndigo, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Month-end projection for ${o.monthLabel}, not an all-time forecast. Projected attendance if you attend all other " +
                "scheduled classes and take this leave. Future attendance is not guaranteed.",
            fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, color = glass.textSecondary
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Recommendation card
// ---------------------------------------------------------------------------------------------

@Composable
private fun RecommendationCard(option: BunkOption, onDetails: () -> Unit) {
    val glass = LocalGlassTheme.current
    val haptic = LocalHapticFeedback.current
    val accent = rankAccent(glass, option.rank)
    val gradient = rankGradient(glass, option.rank)
    val open = {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Throwable) {}
        onDetails()
    }
    val projColor = if (option.tier == SafetyTier.COMFORTABLE) glass.emeraldNeon else glass.amberGold

    BunkGlassCard(
        Modifier.testTag("bunk_card_${option.rank}"),
        accent = accent,
        accent2 = gradient[1],
        shine = option.rank == 1
    ) {
        Box {
            if (option.rank == 1) Twinkles(accent, Modifier.matchParentSize())
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankMedal(option.rank, gradient, accent)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            rankTitle(option.rank).uppercase(),
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, color = accent, fontWeight = FontWeight.Bold
                        )
                        Text(
                            rangeLabel(option.start, option.end),
                            fontFamily = OutfitFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TierChip(option.tier)
                        if (option.isProvisional) BunkChip("Provisional", glass.amberGold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LeaveBadge(option.calendarDays, gradient, delayMs = 120L + option.rank * 90L)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${option.workingDaysMissed} working ${if (option.workingDaysMissed == 1) "day" else "days"} missed",
                            fontFamily = OutfitFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary
                        )
                        Text(
                            "${option.classesMissed} classes · ${option.weekendDays} weekend · ${option.holidayDays} holiday",
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textSecondary
                        )
                    }
                }

                DateStrip(option)
                StripLegend()
                FreeVsMissedBar(option)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(projColor.copy(alpha = 0.09f))
                            .border(1.dp, projColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        CountUpPercent(option.projectedPercent, projColor, 20.sp, 200L + option.rank * 90L)
                        Text("Projected month-end", fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, color = glass.textSecondary)
                    }
                    StatTile("Target", "${option.requiredTarget}%", glass.textPrimary, Modifier.weight(1f))
                    StatTile("Margin", signed(option.marginPoints), projColor, Modifier.weight(1f), sub = "points")
                }
                AttendanceBar(option.projectedPercent, option.requiredTarget, projColor)

                SubjectRiskLine(option)

                GradientButton(
                    text = "View Details",
                    gradient = gradient,
                    modifier = Modifier.testTag("bunk_details_${option.rank}"),
                    trailing = {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    },
                    onClick = open
                )
            }
        }
    }
}

@Composable
private fun SubjectRiskLine(option: BunkOption) {
    val glass = LocalGlassTheme.current
    val weakest = option.weakestSubject
    val text: String
    val color: Color
    val icon: ImageVector
    if (weakest == null) {
        text = "No subject affected"; color = glass.textMuted; icon = Icons.Default.Info
    } else if (option.subjectWarningCount == 0) {
        text = "All affected subjects stay on track · weakest ${weakest.subject.shortName} ${pct(weakest.projectedPercent)}%"
        color = glass.emeraldNeon; icon = Icons.Rounded.CheckCircle
    } else {
        text = "Subject warning: " + option.subjects.filter { it.status != SubjectStatus.ON_TRACK }
            .joinToString(", ") { "${it.subject.shortName} ${pct(it.projectedPercent)}%" } + " below target"
        color = if (option.subjects.any { it.status == SubjectStatus.BELOW_EXAM }) glass.crimsonRose else glass.amberGold
        icon = Icons.Default.Info
    }
    Box(Modifier.testTag("bunk_risk_${option.rank}")) { InfoPill(icon, text, color) }
}

// ---------------------------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------------------------

@Composable
private fun NoSafeState(r: BunkPlannerResult.NoSafeOption) {
    val glass = LocalGlassTheme.current
    val o = r.overview
    val reasonText = when (r.reason) {
        NoSafeReason.BELOW_TARGET_EVEN_IF_ATTEND_ALL ->
            "Even if you attend every remaining class, month-end attendance would be ${o.attendAllProjectedPercent?.let { pct(it) } ?: "—"}%, " +
                "below your required ${o.requiredTarget}%. Any leave would lower it further."
        NoSafeReason.EVERY_LEAVE_TOO_COSTLY ->
            "Every possible full-day leave would drop month-end attendance below ${o.requiredTarget}%." +
                (r.closestProjectedPercent?.let { " The gentlest option (${r.closestDates.joinToString { d -> "${d.dayOfMonth} ${shortMonth(d)}" }}) would end at ${pct(it)}%." } ?: "")
        NoSafeReason.NO_ELIGIBLE_DAYS ->
            "There are no remaining upcoming days with a manually attended class, so there is no full-day leave to evaluate."
    }
    BunkGlassCard(Modifier.testTag("bunk_no_safe"), accent = glass.crimsonRose, accent2 = glass.amberGold) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroIcon(Icons.Default.Shield, glass.crimsonRose)
            Text("No safe option available", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = glass.crimsonRose)
            Text(reasonText, fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp, color = glass.textSecondary)
            if (r.weakSubjects.isNotEmpty()) {
                InfoPill(Icons.Default.Info, "Subjects already short of target this month: ${r.weakSubjects.joinToString()}.", glass.amberGold)
            }
            if (o.attendAllProjectedPercent != null) {
                InfoPill(
                    Icons.Default.Lightbulb,
                    "Best case: attending all ${o.remainingSessions} remaining classes projects to ${pct(o.attendAllProjectedPercent)}% at month end.",
                    glass.royalIndigo
                )
            }
            Text(
                "${o.remainingSessions} classes remain before month end. No unsafe dates are shown as recommendations.",
                fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textMuted
            )
            if (o.isProvisional) {
                InfoPill(Icons.Default.Info, "${o.unmarkedPastSessions} past classes are unmarked; marking them may change this result.", glass.amberGold)
            }
        }
    }
}

@Composable
private fun MessageState(icon: ImageVector, title: String, body: String, accent: Color, tag: String) {
    val glass = LocalGlassTheme.current
    BunkGlassCard(Modifier.testTag(tag), accent = accent, accent2 = glass.electricViolet) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroIcon(icon, accent)
            Text(title, fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = accent)
            Text(body, fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp, color = glass.textSecondary)
        }
    }
}
