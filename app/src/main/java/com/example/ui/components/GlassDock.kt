package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import com.example.ui.settings.rememberAppHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* ───────────────────────── Public API ───────────────────────── */

enum class DockIcon { Home, Attendance, Timetable, Reports, Settings }

data class DockItem(val icon: DockIcon, val label: String)

object DockDefaults {
    val items = listOf(
        DockItem(DockIcon.Home, "Home"),
        DockItem(DockIcon.Attendance, "Attendance"),
        DockItem(DockIcon.Timetable, "Timetable"),
        DockItem(DockIcon.Reports, "Reports"),
        DockItem(DockIcon.Settings, "Settings"),
    )

    /** Bottom space every scrolling screen should leave so its last item clears the dock. */
    val ContentBottomPadding = 110.dp
}

/**
 * Bottom padding for the floating dock: about 10dp lower than the old
 * `navigationBarsPadding() + 10.dp`, and still clears a 3-button nav bar.
 */
@Composable
fun dockBottomPadding(): Dp {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (nav <= 32.dp) nav.coerceAtLeast(8.dp) else nav + 4.dp
}

/**
 * Liquid-glass bottom dock.
 *
 * @param hazeState  pass the same HazeState you attach to your screen content with
 *                   `Modifier.hazeSource(hazeState)`. That gives real backdrop blur, so the
 *                   dock is clear glass instead of a milky white fill. If null, a very faint
 *                   neutral tint is used as a fallback.
 */
@Composable
fun GlassDock(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    items: List<DockItem> = DockDefaults.items,
    hazeState: HazeState? = null,
    darkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    val c = if (darkTheme) DockColors.Dark else DockColors.Light
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = rememberAppHapticFeedback()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(.90f).widthIn(max = 420.dp),
    ) {
        // original size, unchanged
        val dockH = (maxWidth * .15f).coerceIn(58.dp, 64.dp)
        val iconSize = (maxWidth * .062f).coerceIn(22.dp, 26.dp)
        val fontSize = (maxWidth.value * .030f).coerceIn(11f, 14f).sp
        val pad = 6.dp
        val trackW = with(density) { (maxWidth - pad * 2).toPx() }
        val n = items.size
        val total = (n - 1) + ACTIVE_WEIGHT
        val lensW = trackW * ACTIVE_WEIGHT / total
        val maxX = trackW - lensW
        val unit = trackW / total
        fun slotX(i: Int) = unit * i
        fun nearest(x: Float) = (x / unit).roundToInt().coerceIn(0, n - 1)

        /* ── lens state ── */
        val lensX = remember { Animatable(slotX(selectedIndex)) }
        var from by remember { mutableFloatStateOf(lensX.value) }
        var to by remember { mutableFloatStateOf(lensX.value) }
        var pressed by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var dragX by remember { mutableFloatStateOf(0f) }   // finger-driven position (no coroutine per frame)
        var hover by remember { mutableIntStateOf(-1) }       // slot currently under the lens while dragging
        val shine = remember { Animatable(1f) }
        var lastIndex by remember { mutableIntStateOf(selectedIndex) }

        suspend fun glideTo(target: Float) = coroutineScope {
            from = lensX.value; to = target
            launch { shine.snapTo(0f); shine.animateTo(1f, tween(600, easing = LinearOutSlowInEasing)) }
            lensX.animateTo(target, LensSpring)
        }

        LaunchedEffect(trackW) { lensX.snapTo(slotX(selectedIndex)); from = lensX.value; to = lensX.value }
        LaunchedEffect(selectedIndex) {
            if (selectedIndex != lastIndex) { lastIndex = selectedIndex; glideTo(slotX(selectedIndex)) }
        }

        val swell = pressed || dragging || lensX.isRunning
        val lensScale by animateFloatAsState(
            if (swell) 1.06f else 1f,
            spring(dampingRatio = .55f, stiffness = Spring.StiffnessMediumLow), label = "swell",
        )
        val tintAlpha by animateFloatAsState(if (swell) .5f else .82f, tween(300), label = "tintAlpha")
        val shift by animateFloatAsState(
            selectedIndex * .75f, tween(500, easing = CubicBezierEasing(.3f, .9f, .3f, 1f)), label = "shift",
        )

        val currentSelected by rememberUpdatedState(selectedIndex)
        val currentOnSelect by rememberUpdatedState(onSelect)

        /* ── glass dock body (Haze 1.1.1 API: hazeChild, paired with haze on the content) ── */
        val glass = if (hazeState != null) {
            Modifier.hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = Color.Transparent,
                    tint = HazeTint(c.glassTint),
                    blurRadius = 24.dp,
                    noiseFactor = 0f,
                    fallbackTint = HazeTint(c.glassFallback),
                ),
            )
        } else Modifier.background(c.glassFallback)

        Box(
            Modifier
                .fillMaxWidth()
                .height(dockH)
                .shadow(16.dp, CircleShape, ambientColor = c.dockGlow, spotColor = c.dockGlow)
                .clip(CircleShape)
                .then(glass)
                .border(1.dp, Brush.linearGradient(c.rim), CircleShape)
                .padding(pad)
                // press-swell + drag the lens like a toggle thumb
                .pointerInput(trackW, lensW) {
                    val slack = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // this node is already inside the padding, so position.x is in the same space as lensX
                        // (the old code subtracted `pad` again, shifting the touch zone by 6dp)
                        val inside = down.position.x in (lensX.value - slack)..(lensX.value + lensW + slack)
                        if (!inside) { waitForUpOrCancellation(PointerEventPass.Initial); return@awaitEachGesture }
                        pressed = true
                        var firstOver = 0f
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume(); firstOver = over
                        }
                        if (slop == null) { pressed = false; return@awaitEachGesture }

                        // ── drag: purely state-driven, no coroutine launched per pointer event ──
                        scope.launch { lensX.stop() }
                        dragX = (lensX.value + firstOver).coerceIn(0f, maxX)
                        hover = nearest(dragX)
                        dragging = true
                        horizontalDrag(slop.id) { change ->
                            dragX = (dragX + change.positionChange().x).coerceIn(0f, maxX)
                            change.consume()
                            val h = nearest(dragX)
                            if (h != hover) { hover = h; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        }

                        // ── release ──
                        val x = dragX
                        val idx = nearest(x)
                        scope.launch {
                            lensX.snapTo(x)                 // hand over from drag to animation BEFORE dragging=false (no flicker)
                            dragging = false; pressed = false; hover = -1
                            if (idx != currentSelected) currentOnSelect(idx) else glideTo(slotX(idx))
                        }
                    }
                },
        ) {
            /* ── the sliding liquid-glass lens ── */
            Box(
                Modifier
                    .width(with(density) { lensW.toDp() })
                    .fillMaxHeight()
                    .graphicsLayer {
                        val x = if (dragging) dragX else lensX.value
                        translationX = x
                        val travel = to - from
                        val f = if (!dragging && abs(travel) > 1f) ((x - from) / travel).coerceIn(0f, 1f) else 0f
                        val wave = sin(PI * f).toFloat()
                        val peak = min(.35f, abs(travel) / trackW)
                        scaleX = (1f + peak * wave) * lensScale
                        scaleY = (1f - .07f * wave) * lensScale
                    }
                    .shadow(10.dp, CircleShape, ambientColor = c.lensGlow, spotColor = c.lensGlow)
                    .clip(CircleShape)
                    .drawBehind {
                        val w = size.width; val h = size.height
                        // moving blue → violet → magenta tint
                        drawRect(
                            Brush.horizontalGradient(
                                LENS_COLORS.map { it.copy(alpha = tintAlpha) },
                                startX = -shift * w, endX = (4f - shift) * w,
                            ),
                        )
                        // top gloss
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(.42f), .48f to Color.Transparent, endY = h,
                            ),
                        )
                        // chromatic edge glow
                        drawRect(Brush.horizontalGradient(listOf(Color(0x5978E6FF), Color.Transparent), endX = w * .2f))
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0x4DFF96FF)), startX = w * .8f, endX = w,
                            ),
                        )
                        // shine sweep
                        val p = shine.value
                        if (p < 1f) {
                            val bw = w * .45f
                            val x = w * (-.6f + 1.9f * p)
                            val skew = h * .325f
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
                    .border(1.dp, Color.White.copy(.5f), CircleShape),
            )

            /* ── tabs ── */
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    DockSlot(
                        item = item,
                        selected = i == selectedIndex,
                        lit = if (dragging) i == hover else i == selectedIndex,
                        colors = c,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        onClick = {
                            if (i != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            }
                        },
                    )
                }
            }
        }
    }
}

/* ───────────────────────── Slot ───────────────────────── */

@Composable
private fun RowScope.DockSlot(
    item: DockItem,
    selected: Boolean,
    lit: Boolean,
    colors: DockColors,
    iconSize: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    // same spring as the lens, so the lens and the slots always move as one piece
    val weight by animateFloatAsState(if (selected) ACTIVE_WEIGHT else 1f, LensSpring, label = "w")
    val ink by animateColorAsState(if (lit) Color.White else colors.ink, tween(220), label = "ink")
    val cut by animateColorAsState(if (lit) LENS_COLORS[1] else colors.cut, tween(220), label = "cut")
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed) .94f else 1f, spring(dampingRatio = .5f, stiffness = 500f), label = "press",
    )

    val tag = when (item.icon) {
        DockIcon.Home -> "tab_home"
        DockIcon.Attendance -> "tab_attendance"
        DockIcon.Timetable -> "tab_timetable"
        DockIcon.Reports -> "tab_reports"
        DockIcon.Settings -> "tab_settings"
    }

    // per-icon micro animation, plays once on select
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (selected && !first) { pop.snapTo(0f); pop.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        first = false
    }

    Row(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .testTag(tag)
            .semantics { contentDescription = item.label; this.selected = selected }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Glyph(
            kind = item.icon, tint = ink, cut = cut, size = iconSize,
            pop = { pop.value }, modifier = Modifier.graphicsLayer {
                val s = if (selected) 1.04f else 1f; scaleX = s; scaleY = s
            },
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(380, easing = DockEase)) +
                fadeIn(tween(220, delayMillis = 90)) +
                slideInHorizontally(tween(380, delayMillis = 60, easing = DockEase)) { -it / 4 },
            exit = shrinkHorizontally(tween(260, easing = DockEase)) + fadeOut(tween(100)),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.label, color = ink, fontSize = fontSize, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/* ───────────────────────── Icons (Canvas, so they can animate) ───────────────────────── */

@Composable
private fun Glyph(
    kind: DockIcon, tint: Color, cut: Color, size: Dp, pop: () -> Float, modifier: Modifier = Modifier,
) {
    // rounded house (24x24 grid) + arched door, built once
    val house = remember {
        Path().apply {
            moveTo(10.6f, 3.3f); quadraticBezierTo(12f, 2.2f, 13.4f, 3.3f)
            lineTo(20.9f, 9.3f); quadraticBezierTo(21.7f, 10f, 21.7f, 11f)
            lineTo(21.7f, 18.2f); quadraticBezierTo(21.7f, 21f, 18.9f, 21f)
            lineTo(5.1f, 21f); quadraticBezierTo(2.3f, 21f, 2.3f, 18.2f)
            lineTo(2.3f, 11f); quadraticBezierTo(2.3f, 10f, 3.1f, 9.3f)
            close()
        }
    }
    val door = remember {
        Path().apply {
            moveTo(9.4f, 21f); lineTo(9.4f, 15.4f); quadraticBezierTo(9.4f, 13.2f, 11.6f, 13.2f)
            lineTo(12.4f, 13.2f); quadraticBezierTo(14.6f, 13.2f, 14.6f, 15.4f)
            lineTo(14.6f, 21f); close()
        }
    }
    Canvas(modifier.size(size)) {
        val p = pop()
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            val c = Offset(12f, 12f)
            when (kind) {
                DockIcon.Home -> {
                    val k = sin(PI * p).toFloat()
                    translate(top = -3f * k) {
                        scale(1f + .08f * k, pivot = Offset(12f, 21f)) {
                            drawPath(house, tint)
                            drawPath(door, cut)
                        }
                    }
                }
                DockIcon.Attendance -> {
                    drawCircle(tint, 10f, c)
                    val tick = Path().apply { moveTo(7.5f, 12.4f); lineTo(10.5f, 15.4f); lineTo(16.5f, 9f) }
                    val seg = Path()
                    val pm = PathMeasure().apply { setPath(tick, false) }
                    pm.getSegment(0f, pm.length * p, seg, true)
                    drawPath(seg, cut, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                DockIcon.Timetable -> {
                    drawCircle(tint, 10f, c)
                    drawLine(cut, Offset(12f, 6.8f), Offset(12f, 12f), 2.4f, StrokeCap.Round)
                    rotate(360f * p, pivot = c) {
                        drawLine(cut, c, Offset(15.4f, 14.1f), 2.4f, StrokeCap.Round)
                    }
                }
                DockIcon.Reports -> {
                    val bars = listOf(Triple(3.5f, 12f, 9f), Triple(9.7f, 3f, 18f), Triple(15.9f, 9f, 12f))
                    bars.forEachIndexed { k, (x, y, h) ->
                        val bp = ((p - k * .1f) / .7f).coerceIn(0f, 1f)
                        val sy = .25f + .75f * easeOutBack(bp)
                        scale(1f, sy, pivot = Offset(x + 2.3f, 21f)) {
                            drawRoundRect(tint, Offset(x, y), Size(4.6f, h), CornerRadius(1.4f))
                        }
                    }
                }
                DockIcon.Settings -> rotate(180f * p, pivot = c) {
                    drawCircle(tint, 5.2f, c, style = Stroke(3.4f))
                    for (r in 0 until 8) {
                        val a = r * PI / 4
                        val (ca, sa) = cos(a).toFloat() to sin(a).toFloat()
                        drawLine(
                            tint,
                            Offset(12f + 7.2f * ca, 12f + 7.2f * sa),
                            Offset(12f + 9.4f * ca, 12f + 9.4f * sa),
                            3.2f, StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    return 1f + c3 * (t - 1f).let { it * it * it } + c1 * (t - 1f).let { it * it }
}

/* ───────────────────────── Theme tokens ───────────────────────── */

private const val ACTIVE_WEIGHT = 2.6f
private val DockEase = CubicBezierEasing(.3f, .85f, .35f, 1f)
/** one spring drives lens + slot widths: interruptible, keeps velocity, no hard stop */
private val LensSpring = spring<Float>(dampingRatio = .86f, stiffness = 330f)
private val LENS_COLORS = listOf(
    Color(0xFF0865FF), Color(0xFF4A44F5), Color(0xFF7628F5), Color(0xFFB13CF0), Color(0xFF0865FF),
)

@Immutable
class DockColors(
    val ink: Color,          // inactive icon colour
    val cut: Color,          // "punched-out" detail inside icons
    val glassTint: Color,    // very faint tint laid over the real blur (NOT white)
    val glassFallback: Color,// used when blur isn't available (0.88f scrim)
    val rim: List<Color>,    // gradient edge
    val dockGlow: Color,
    val lensGlow: Color,
) {
    companion object {
        val Dark = DockColors(
            ink = Color(0xFF91A9D0),
            cut = Color(0xFF14224F),
            glassTint = Color(0xFF05070D).copy(alpha = .30f),   // neutral obsidian, not blue
            glassFallback = Color(0xFF0D1120).copy(alpha = .88f),
            rim = listOf(Color.White.copy(.32f), Color.White.copy(.06f), Color.White.copy(.16f), Color.White.copy(.05f)),
            dockGlow = Color.Black.copy(alpha = .55f),
            lensGlow = Color(0xFF7846FF),
        )
        val Light = DockColors(
            ink = Color(0xFF5B70A6),
            cut = Color(0xFFEAF1FF),
            glassTint = Color(0xFF9DB4F0).copy(alpha = .12f),
            glassFallback = Color(0xFFDCE6FF).copy(alpha = .75f),
            rim = listOf(Color.White, Color(0xD982D2FF), Color.White, Color(0xE6D78CFF)),
            dockGlow = Color(0xFF5064FF).copy(alpha = .35f),
            lensGlow = Color(0xFF6E5AFF),
        )
    }
}
