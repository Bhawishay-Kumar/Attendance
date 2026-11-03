package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import com.example.ui.settings.rememberAppHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* ───────────────────────── Day Attendance Status Enum ───────────────────────── */

enum class DayAttendanceStatus {
    UNMARKED,
    ALL_PRESENT,
    ABSENT_OR_MIXED,
    HOLIDAY
}

/* ───────────────────────── Tunables ───────────────────────── */

private val ItemW = 56.dp
private val ItemH = 72.dp
private val Gap = 8.dp
private val HPad = 12.dp
private val VPad = 8.dp
private const val WHEEL_TILT = 16f      // degrees of 3D tilt at the edges
private const val WHEEL_SHRINK = .07f   // how much cells shrink toward the edges
private val Ease = CubicBezierEasing(.3f, .85f, .35f, 1f)

/* ───────────────────────── Public API ───────────────────────── */

/**
 * Returns the real current IST date and automatically refreshes whenever the app resumes
 * (e.g., after midnight or returning from background), preventing any stale Oct 30 lock.
 */
@Composable
fun rememberLiveToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Kolkata"))) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                if (now != today) {
                    today = now
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return today
}

/**
 * Generates the full Current Month + Full Next Month date list keyed on `liveToday`'s YearMonth.
 */
@Composable
fun rememberTwoMonthDates(liveToday: LocalDate): List<LocalDate> {
    return remember(liveToday.year, liveToday.monthValue) {
        val currentMonth = YearMonth.from(liveToday)
        val nextMonth = currentMonth.plusMonths(1)
        val start = currentMonth.atDay(1)
        val end = nextMonth.atEndOfMonth()
        generateSequence(start) { date ->
            if (date.isBefore(end)) date.plusDays(1) else null
        }.toList()
    }
}

/**
 * Attendance date strip covering the WHOLE current month + the WHOLE next month.
 * Swipe freely (snaps to centre), tap a day, or jump with the month switch.
 * Dates for which [isLocked] is true shake when tapped and call [onLockedClick].
 */
@Composable
fun AttendanceDateStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = rememberLiveToday(),
    dateStatusResolver: (LocalDate) -> DayAttendanceStatus = { DayAttendanceStatus.UNMARKED },
    isLocked: (LocalDate) -> Boolean = { it.isAfter(today) },
    onLockedClick: (LocalDate) -> Unit = {},
    selectOnSwipe: Boolean = true,   // swiping the strip selects the day that settles in the centre
    darkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    val c = if (darkTheme) StripColors.Dark else StripColors.Light
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = rememberAppHapticFeedback()

    // 1st of this month … last day of next month
    val days = rememberTwoMonthDates(today)
    val thisMonth = remember(today.year, today.monthValue) { YearMonth.from(today) }
    val nextMonth = remember(today.year, today.monthValue) { thisMonth.plusMonths(1) }
    val selectedIndex = days.indexOf(selectedDate).coerceAtLeast(0)

    val itemWPx = with(density) { ItemW.toPx() }
    val pitchPx = with(density) { (ItemW + Gap).toPx() }
    // The list gets side padding of (viewport - item) / 2, so ANY day (even the 1st / last) can sit exactly
    // in the centre, and "scroll item to offset 0" == "centre it". Start in the right place: no first-frame jump.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    suspend fun centerOn(i: Int, animated: Boolean) {
        if (listState.layoutInfo.viewportSize.width <= 0) return
        if (animated) listState.animateScrollToItem(i, 0) else listState.scrollToItem(i, 0)
    }

    /**
     * x of any (fractional) item index, in the strip's own coordinates.
     * BUG FIX: LazyList item offsets do NOT include the start content padding, so it must be added back —
     * the old code left the lens 12dp to the left of the day it highlights (visible on "30" in your screenshot).
     */
    fun itemX(i: Float): Float {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return -9999f
        return info.beforeContentPadding + first.offset + (i - first.index) * pitchPx
    }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentIsLocked by rememberUpdatedState(isLocked)
    val currentOnSelected by rememberUpdatedState(onDateSelected)
    val currentOnLocked by rememberUpdatedState(onLockedClick)

    // ── lens (the sliding glass highlight) ──
    val lensIdx = remember { Animatable(selectedIndex.toFloat()) }
    var from by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var to by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    val shine = remember { Animatable(1f) }
    var lastSel by remember { mutableIntStateOf(selectedIndex) }
    var introDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
        centerOn(currentSelectedIndex, animated = false)
        delay(900); introDone = true
    }

    // Swipe to switch day: when a finger-driven scroll settles, select whichever day is in the centre.
    LaunchedEffect(listState, days, selectOnSwipe) {
        if (!selectOnSwipe) return@LaunchedEffect
        var dragged = false
        launch { listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true } }
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling || !dragged) return@collect
            dragged = false
            val info = listState.layoutInfo
            val mid = info.viewportSize.width / 2f - info.beforeContentPadding
            val centre = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - mid) } ?: return@collect
            val idx = centre.index.coerceIn(days.indices)
            if (idx == currentSelectedIndex) return@collect
            if (currentIsLocked(days[idx])) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLocked(days[idx])
                centerOn(currentSelectedIndex, animated = true)      // bounce back to the selected day
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnSelected(days[idx])
            }
        }
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex == lastSel) return@LaunchedEffect
        lastSel = selectedIndex
        from = lensIdx.value; to = selectedIndex.toFloat()
        coroutineScope {
            launch { shine.snapTo(0f); shine.animateTo(1f, tween(600, easing = LinearOutSlowInEasing)) }
            launch { centerOn(selectedIndex, animated = true) }
            lensIdx.animateTo(to, spring(dampingRatio = .84f, stiffness = 300f))
        }
    }

    val travelling = lensIdx.isRunning
    val lensScale by animateFloatAsState(
        if (travelling) 1.08f else 1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMediumLow), label = "swell",
    )
    val tintAlpha by animateFloatAsState(if (travelling) .72f else .95f, tween(300), label = "tintA")
    val monthT by animateFloatAsState(
        if (YearMonth.from(days[selectedIndex]) == thisMonth) 0f else 1f, tween(500), label = "monthT",
    )

    val visibleMonth by remember(days) {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = info.viewportSize.width / 2f - info.beforeContentPadding
            val centre = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - mid) }
            centre?.let { YearMonth.from(days[it.index.coerceIn(days.indices)]) } ?: thisMonth
        }
    }

    Column(modifier) {
        StripHeader(
            visibleMonth = visibleMonth, thisMonth = thisMonth, nextMonth = nextMonth,
            showToday = selectedDate != today, colors = c,
            onToday = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDateSelected(today)
                scope.launch { centerOn(days.indexOf(today).coerceAtLeast(0), animated = true) }
            },
            onJump = { m ->
                val target = if (m == thisMonth) today else m.atDay(1)
                scope.launch { centerOn(days.indexOf(target).coerceAtLeast(0), animated = true) }
            },
        )
        Spacer(Modifier.height(12.dp))

        val shape = RoundedCornerShape(32.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(ItemH + VPad * 2)
                .shadow(14.dp, shape, ambientColor = c.glow, spotColor = c.glow)
                .clip(shape)
                .background(c.container)
                .border(1.dp, Brush.linearGradient(c.rim), shape),
        ) {
            // wrapper: fades both lens and cells softly at the left/right edges
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent, .11f to Color.Black, .89f to Color.Black, 1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                val sidePad = ((maxWidth - ItemW) / 2).coerceAtLeast(HPad)
                /* ── the sliding glass lens ── */
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(ItemW, ItemH)
                        .graphicsLayer {
                            val x = itemX(lensIdx.value)
                            val vp = listState.layoutInfo.viewportSize.width.toFloat()
                            val d = wheelD(x + itemWPx / 2f, vp)
                            val travel = to - from
                            val f = if (abs(travel) > .01f) ((lensIdx.value - from) / travel).coerceIn(0f, 1f) else 0f
                            val wave = sin(PI * f).toFloat()
                            val peak = (.18f + abs(travel) * .04f).coerceAtMost(.5f)
                            val wheel = 1f - WHEEL_SHRINK * d * d
                            translationX = x
                            rotationY = WHEEL_TILT * d
                            cameraDistance = 14f * density.density
                            scaleX = (1f + peak * wave) * lensScale * wheel
                            scaleY = (1f - .08f * wave) * lensScale * wheel
                        }
                        .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = c.lensGlow, spotColor = c.lensGlow)
                        .clip(RoundedCornerShape(22.dp))
                        .drawBehind {
                            val w = size.width; val h = size.height
                            val top = lerp(LensTopOct, LensTopNov, monthT).copy(alpha = tintAlpha)
                            val bottom = lerp(LensBottomOct, LensBottomNov, monthT).copy(alpha = tintAlpha)
                            drawRect(Brush.verticalGradient(listOf(top, bottom)))
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.White.copy(.30f), .5f to Color.Transparent, endY = h,
                                ),
                            )
                            val p = shine.value
                            if (p < 1f) {
                                val bw = w * .5f
                                val x = w * (-.7f + 2.0f * p)
                                val skew = h * .3f
                                val band = Path().apply {
                                    moveTo(x + skew, 0f); lineTo(x + bw + skew, 0f); lineTo(x + bw, h); lineTo(x, h); close()
                                }
                                drawPath(
                                    band,
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.White.copy(.55f), Color.Transparent),
                                        startX = x, endX = x + bw,
                                    ),
                                )
                            }
                        }
                        .border(1.dp, Color.White.copy(.5f), RoundedCornerShape(22.dp)),
                )

                /* ── the days ── */
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = sidePad),
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    verticalAlignment = Alignment.CenterVertically,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                ) {
                    items(days.size, key = { days[it].toEpochDay() }) { i ->
                        val date = days[i]
                        val locked = isLocked(date)
                        val status = dateStatusResolver(date)
                        DayCell(
                            date = date, index = i, today = today,
                            selected = i == selectedIndex, locked = locked, status = status,
                            colors = c, listState = listState, gapPx = with(density) { Gap.toPx() },
                            introDelay = if (introDone) -1 else (abs(i - selectedIndex) * 40).coerceAtMost(360),
                            onTap = {
                                when {
                                    locked -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLockedClick(date)
                                    }
                                    i != selectedIndex -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDateSelected(date)
                                    }
                                    else -> scope.launch { centerOn(i, animated = true) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Day cell ───────────────────────── */

@Composable
private fun DayCell(
    date: LocalDate, index: Int, today: LocalDate,
    selected: Boolean, locked: Boolean, status: DayAttendanceStatus,
    colors: StripColors, listState: androidx.compose.foundation.lazy.LazyListState,
    gapPx: Float, introDelay: Int, onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) .94f else 1f, spring(dampingRatio = .5f, stiffness = 500f), label = "press",
    )
    val selT by animateFloatAsState(if (selected) 1f else 0f, tween(280), label = "selT")
    val numScale by animateFloatAsState(
        if (selected) 1.12f else 1f, spring(dampingRatio = .45f, stiffness = 380f), label = "num",
    )

    // entrance (only for cells composed during the first second)
    val intro = remember { Animatable(if (introDelay >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (introDelay >= 0) { delay(introDelay.toLong()); intro.animateTo(1f, tween(520, easing = Ease)) }
    }
    // shake when a locked day is tapped
    val shake = remember { Animatable(0f) }

    val ink = lerp(colors.weekday, Color.White, selT)
    val numInk = lerp(colors.number, Color.White, selT)
    // Future date lock enforcement: dates after today dimmed (alpha = 0.38f)
    val fade = if (locked) .38f else 1f
    val shape = RoundedCornerShape(22.dp)

    Box(
        Modifier
            .size(ItemW, ItemH)
            .graphicsLayer {
                val info = listState.layoutInfo
                val vp = info.viewportSize.width.toFloat()
                val me = info.visibleItemsInfo.firstOrNull { it.index == index }
                val d = if (me != null) wheelD(info.beforeContentPadding + me.offset + me.size / 2f, vp) else 0f
                val wheel = 1f - WHEEL_SHRINK * d * d
                rotationY = WHEEL_TILT * d
                cameraDistance = 14f * density.density
                scaleX = pressScale * wheel
                scaleY = pressScale * wheel
                translationX = shake.value * density.density
                translationY = (1f - intro.value) * 28f * density.density
                alpha = intro.value * fade
            }
            .drawBehind {   // thin month divider before the 1st of a month
                if (date.dayOfMonth == 1 && index > 0) {
                    drawLine(
                        Brush.verticalGradient(listOf(Color.Transparent, colors.accent, Color.Transparent)),
                        Offset(-gapPx / 2f, size.height * .1f), Offset(-gapPx / 2f, size.height * .9f),
                        2.dp.toPx(), StrokeCap.Round,
                    )
                }
            }
            .clip(shape)
            .background(colors.cellFill.copy(alpha = colors.cellFill.alpha * (1f - selT)))
            .border(1.5.dp, colors.cellBorder.copy(alpha = colors.cellBorder.alpha * (1f - selT) * (if (locked) .40f else 1f)), shape)
            .testTag("date_cell_${date}")
            .semantics {
                contentDescription = date.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault()) +
                    ", " + date.dayOfMonth + " " + date.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()) +
                    if (locked) ", locked" else ""
                this.selected = selected
            }
            .clickable(source, indication = null) {
                if (locked) scope.launch {
                    shake.snapTo(0f)
                    shake.animateTo(0f, keyframes {
                        durationMillis = 380
                        -9f at 60 using LinearEasing; 8f at 140 using LinearEasing
                        -5f at 220 using LinearEasing; 3f at 300 using LinearEasing
                    })
                }
                onTap()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).uppercase(),
                color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "%02d".format(date.dayOfMonth),
                color = numInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer { scaleX = numScale; scaleY = numScale },
            )
            Spacer(Modifier.height(5.dp))
            val isToday = date == today
            when {
                isToday && !selected -> PulseDot(colors.accent)
                locked -> Box(Modifier.height(7.dp), contentAlignment = Alignment.Center) { LockGlyph(ink.copy(alpha = .9f)) }
                else -> {
                    // Status dot mapping:
                    // Emerald (All Present), Rose (Absent/Mixed), Cyan (AKTU Holiday), Dimmed/Hollow (Unmarked)
                    val dotColor = when (status) {
                        DayAttendanceStatus.ALL_PRESENT -> Color(0xFF10B981) // Emerald
                        DayAttendanceStatus.ABSENT_OR_MIXED -> Color(0xFFF43F5E) // Rose
                        DayAttendanceStatus.HOLIDAY -> Color(0xFF06B6D4) // Cyan
                        DayAttendanceStatus.UNMARKED -> colors.dot.copy(alpha = if (locked) .38f else .65f)
                    }

                    if (selected) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    } else if (status == DayAttendanceStatus.UNMARKED) {
                        // Dimmed / Hollow for unmarked
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .border(1.dp, dotColor, CircleShape),
                        )
                    } else {
                        // Solid indicator dot for marked status
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        .8f, 1.35f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "s",
    )
    Box(
        Modifier
            .size(6.dp)
            .graphicsLayer { scaleX = s; scaleY = s; alpha = 1.35f - (s - .8f) * .6f }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun LockGlyph(color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width
        drawRoundRect(color, Offset(w * .12f, w * .45f), Size(w * .76f, w * .5f), CornerRadius(w * .14f))
        drawArc(
            color, 180f, 180f, false, Offset(w * .27f, w * .08f), Size(w * .46f, w * .7f),
            style = Stroke(w * .14f, cap = StrokeCap.Round),
        )
    }
}

/* ───────────────────────── Header ───────────────────────── */

@Composable
private fun StripHeader(
    visibleMonth: YearMonth, thisMonth: YearMonth, nextMonth: YearMonth,
    showToday: Boolean, colors: StripColors, onToday: () -> Unit, onJump: (YearMonth) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = visibleMonth,
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInVertically(tween(350, easing = Ease)) { if (fwd) it else -it } + fadeIn(tween(250))) togetherWith
                        (slideOutVertically(tween(350, easing = Ease)) { if (fwd) -it else it } + fadeOut(tween(150)))
                },
                label = "monthTitle",
            ) { m ->
                Text(
                    m.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()) + " " + m.year,
                    color = colors.number, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            AnimatedVisibility(
                visible = showToday,
                enter = scaleIn(spring(dampingRatio = .55f, stiffness = 400f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Text(
                    "Today",
                    color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = .14f))
                        .border(1.dp, colors.accent.copy(alpha = .35f), CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToday)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        // Current Month | Next Month switch with sliding thumb
        val chipW = 54.dp
        val thumbX by animateDpAsState(
            if (visibleMonth == nextMonth) chipW else 0.dp, spring(dampingRatio = .7f, stiffness = 380f), label = "thumb",
        )
        Box(
            Modifier
                .width(chipW * 2).height(32.dp)
                .clip(CircleShape)
                .background(colors.container)
                .border(1.dp, colors.cellBorder.copy(alpha = .6f), CircleShape),
        ) {
            Box(
                Modifier
                    .offset(x = thumbX).size(chipW, 32.dp).padding(3.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(LensTopOct, LensBottomOct))),
            )
            Row(Modifier.fillMaxSize()) {
                listOf(thisMonth, nextMonth).forEach { m ->
                    val sel = m == visibleMonth
                    val col by animateColorAsState(if (sel) Color.White else colors.weekday, tween(250), label = "chipCol")
                    Box(
                        Modifier
                            .width(chipW).fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onJump(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                            color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Helpers & tokens ───────────────────────── */

/** -1 (far left) … 0 (centre) … +1 (far right) */
private fun wheelD(centerX: Float, vp: Float): Float =
    if (vp <= 0f) 0f else ((centerX - vp / 2f) / (vp / 2f)).coerceIn(-1f, 1f)

private val LensTopOct = Color(0xFF6D6DF7)
private val LensBottomOct = Color(0xFF4F46E5)
private val LensTopNov = Color(0xFF9A6BFA)      // lens shifts to violet in next month
private val LensBottomNov = Color(0xFF6D3AE8)

@Immutable
private class StripColors(
    val container: Color, val cellFill: Color, val cellBorder: Color,
    val weekday: Color, val number: Color, val dot: Color, val marked: Color, val accent: Color,
    val rim: List<Color>, val glow: Color, val lensGlow: Color,
) {
    companion object {
        val Light = StripColors(
            container = com.example.ui.theme.LightPearl.copy(alpha = .85f),
            cellFill = com.example.ui.theme.LightPearl.copy(alpha = .65f),
            cellBorder = Color(0xFFA9A3F0).copy(alpha = .45f),
            weekday = Color(0xFF64748B), number = Color(0xFF0F172A),
            dot = Color(0xFFB4BCCB), marked = Color(0xFF10B981), accent = Color(0xFF5B52EC),
            rim = listOf(Color.White, Color(0xD982D2FF), Color.White, Color(0xE6D78CFF)),
            glow = Color(0xFF5064FF).copy(alpha = .25f), lensGlow = Color(0xFF6E5AFF),
        )
        val Dark = StripColors(
            container = Color(0xFF161B2E).copy(alpha = .55f),          // neutral frosted obsidian
            cellFill = Color.White.copy(alpha = .05f),
            cellBorder = Color.White.copy(alpha = .14f),
            weekday = Color(0xFF9FB0D8), number = Color(0xFFF1F5FF),
            dot = Color(0xFF55618A), marked = Color(0xFF10B981), accent = Color(0xFF8B87FF),
            rim = listOf(Color.White.copy(.25f), Color.White.copy(.05f), Color.White.copy(.14f)),
            glow = Color.Black.copy(alpha = .45f), lensGlow = Color(0xFF7846FF),
        )
    }
}
