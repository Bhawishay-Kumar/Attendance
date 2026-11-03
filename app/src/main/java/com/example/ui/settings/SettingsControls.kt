package com.example.ui.settings

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlin.math.roundToInt

// ───────────────────────── motion helpers ─────────────────────────

/** False when the user disabled animations system-wide (Developer options / accessibility "remove animations"). */
@Composable
fun rememberMotionEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
            .getOrDefault(true)
    }
}

/** Smooth ping-pong value between [from] and [to]; constant [to] when motion is disabled. */
@Composable
fun rememberPulse(periodMs: Int, from: Float, to: Float, label: String = "pulse"): State<Float> {
    if (!rememberMotionEnabled()) return remember { mutableStateOf(to) }
    val t = rememberInfiniteTransition(label = label)
    return t.animateFloat(from, to, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = label)
}

/** Linear 0→1 loop (restart); constant 0 when motion is disabled. */
@Composable
fun rememberLoop(periodMs: Int, label: String = "loop"): State<Float> {
    if (!rememberMotionEnabled()) return remember { mutableStateOf(0f) }
    val t = rememberInfiniteTransition(label = label)
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = label)
}

/** Press-scale click (Stitch "tap-scale"). */
fun Modifier.tapClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tap"
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** Soft breathing colour blob in a card corner (Stitch "animate-glow-aura"). */
@Composable
fun Modifier.glowAura(color: Color, atTop: Boolean, atStart: Boolean, radiusDp: Float = 90f): Modifier {
    val a by rememberPulse(4200, 0.55f, 1f, "aura")
    return this.drawBehind {
        val cx = if (atStart) size.width * 0.05f else size.width * 0.95f
        val cy = if (atTop) size.height * 0.05f else size.height * 0.95f
        val r = radiusDp.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = color.alpha * a), Color.Transparent), Offset(cx, cy), r),
            radius = r,
            center = Offset(cx, cy)
        )
    }
}

// ───────────────────────── text ─────────────────────────

@Composable
fun SText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium,
    family: FontFamily = PlusJakartaSansFontFamily,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = text, color = color, fontSize = size, fontWeight = weight, fontFamily = family,
        letterSpacing = letterSpacing, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        textAlign = align, lineHeight = lineHeight, modifier = modifier
    )
}

// ───────────────────────── toggle ─────────────────────────

/**
 * 52×30 pill switch with spring thumb, colour cross-fade, soft glow and an in-thumb tick when on.
 * (The tick means "on" is never communicated by colour alone.)
 */
@Composable
fun LuxuryToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    p: SettingsPalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val prog by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "toggle"
    )
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "togglePress")
    val shape = RoundedCornerShape(50)
    val on = prog.coerceIn(0f, 1f)
    val tickColor = p.indigo
    Box(
        modifier
            .size(52.dp, 30.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .graphicsLayer { scaleX = press; scaleY = press }
            .shadow((8f * on).dp, shape, ambientColor = p.indigoLight, spotColor = p.indigoLight)
            .clip(shape)
            .background(p.toggleOff)
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B5BFF), Color(0xFF7C3AED))), shape, alpha = on)
            .border(1.dp, Color.White.copy(alpha = 0.10f + 0.12f * on), shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, interactionSource = src, indication = null, onValueChange = onCheckedChange)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset((22.dp * prog).roundToPx(), 0) }
                .size(24.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .drawBehind {
                    // tick fades in with the switch
                    if (on > 0.05f) {
                        val w = size.width; val h = size.height
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.28f, h * 0.52f); lineTo(w * 0.44f, h * 0.67f); lineTo(w * 0.73f, h * 0.35f)
                        }
                        drawPath(
                            path, tickColor.copy(alpha = on),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.2.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
        )
    }
}

// ───────────────────────── segmented selector ─────────────────────────

/** Sliding-indicator segmented control (Batch, Theme, Haptic intensity). */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    p: SettingsPalette,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 38.dp,
    textSize: TextUnit = 12.sp,
    gap: Dp = 8.dp,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    val itemShape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(p.segTrack)
            .border(1.dp, p.segTrackBorder, shape)
            .padding(4.dp)
    ) {
        val n = options.size.coerceAtLeast(1)
        val itemW = (maxWidth - gap * (n - 1)) / n
        val x by animateDpAsState((itemW + gap) * selectedIndex, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "segX")
        Box(
            Modifier
                .offset(x = x)
                .size(itemW, itemHeight)
                .shadow(if (p.dark) 6.dp else 4.dp, itemShape, ambientColor = p.indigoLight, spotColor = p.indigoLight)
                .clip(itemShape)
                .background(p.accentBrush)
                .border(1.dp, Color.White.copy(alpha = 0.22f), itemShape)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                val c by animateColorAsState(if (selected) Color.White else p.textSecondary, label = "segText")
                Box(
                    Modifier
                        .width(itemW)
                        .height(itemHeight)
                        .clip(itemShape)
                        .semantics { this.selected = (i == selectedIndex) }
                        .tapClick(enabled = enabled, role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    SText(label, textSize, c, weight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────────────── slider ─────────────────────────

/**
 * Stitch attendance slider: flowing multi-colour fill with a travelling shine, thumb that grows while dragging.
 * Integer steps between [min] and [max]; [onStep] fires on every value change (used for haptic ticks).
 *
 * Fixes vs. the previous version:
 *  - the gesture handlers always call the LATEST [onValueChange] / [onStep] (they used to capture the first one),
 *  - the thumb follows the finger instantly while dragging (no 150 ms animation lag),
 *  - [onDragStateChange] tells the caller when a drag is in progress so it can avoid overwriting the
 *    value from outside mid-drag (that made the thumb jump back),
 *  - taller touch area (36dp) so it is easier to grab inside a scrolling list.
 */
@Composable
fun FlowSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    p: SettingsPalette,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 10.dp,
    flowing: Boolean = true,
    onStep: (() -> Unit)? = null,
    onDragStateChange: ((Boolean) -> Unit)? = null
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnStep by rememberUpdatedState(onStep)
    val latestOnDragState by rememberUpdatedState(onDragStateChange)
    val frac by animateFloatAsState(
        targetValue = ((value - min) / (max - min).toFloat()).coerceIn(0f, 1f),
        animationSpec = if (dragging) snap() else tween(150),
        label = "sliderFrac"
    )
    val thumbScale by animateFloatAsState(if (dragging) 1.3f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "thumb")
    val flow by rememberLoop(3500, "flow")
    val shine by rememberLoop(2000, "shine")

    fun emit(x: Float) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        val v = (min + f * (max - min)).roundToInt().coerceIn(min, max)
        if (v != current) { latestOnValueChange(v); latestOnStep?.invoke() }
    }

    Box(
        modifier
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                stateDescription = "$value"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat())
                setProgress { t -> latestOnValueChange(t.roundToInt().coerceIn(min, max)); true }
            }
            .pointerInput(min, max) { detectTapGestures { emit(it.x) } }
            .pointerInput(min, max) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; latestOnDragState?.invoke(true); emit(it.x) },
                    onDragEnd = { dragging = false; latestOnDragState?.invoke(false) },
                    onDragCancel = { dragging = false; latestOnDragState?.invoke(false) }
                ) { change, _ -> change.consume(); emit(change.position.x) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val th = trackHeight.toPx()
            val top = (size.height - th) / 2f
            val r = CornerRadius(th / 2f, th / 2f)
            // recessed track
            drawRoundRect(p.toggleOff, Offset(0f, top), Size(size.width, th), r)
            drawRoundRect(
                Color.Black.copy(alpha = if (p.dark) 0.28f else 0.06f), Offset(0f, top), Size(size.width, th * 0.5f), r
            )
            val fillW = (size.width * frac).coerceAtLeast(0f)
            if (fillW > 0f) {
                val brush = if (flowing) {
                    val gw = (fillW * 2.5f).coerceAtLeast(1f)
                    val s = flow * gw
                    Brush.horizontalGradient(
                        listOf(Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFF06B6D4), Color(0xFF3B82F6)),
                        startX = s, endX = s + gw, tileMode = TileMode.Repeated
                    )
                } else Brush.horizontalGradient(listOf(p.indigo, p.indigoLight))
                drawRoundRect(brush, Offset(0f, top), Size(fillW, th), r)
                if (flowing) {
                    val s = -fillW + (1f - shine) * 2f * fillW
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent), startX = s, endX = s + fillW),
                        Offset(0f, top), Size(fillW, th), r
                    )
                }
                val tr = (th * 0.9f) * thumbScale
                val cx = fillW.coerceIn(tr, size.width - tr)
                val cy = size.height / 2f
                // halo → white thumb → indigo ring → inner dot
                drawCircle(p.indigoLight.copy(alpha = if (dragging) 0.38f else 0.26f), tr * 2.0f, Offset(cx, cy))
                drawCircle(Color.Black.copy(alpha = 0.18f), tr + 1.5.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))
                drawCircle(Color.White, tr, Offset(cx, cy))
                drawCircle(p.indigo, tr, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                drawCircle(p.indigo.copy(alpha = 0.9f), tr * 0.32f, Offset(cx, cy))
            }
        }
    }
}

// ───────────────────────── small pieces ─────────────────────────

@Composable
fun PulseDot(color: Color, size: Dp = 6.dp) {
    val a by rememberPulse(1200, 0.35f, 1f, "dot")
    Box(Modifier.size(size).graphicsLayer { alpha = a }.background(color, CircleShape))
}

@Composable
fun IconBadge(icon: ImageVector, bg: Color, tint: Color, size: Dp = 28.dp, iconSize: Dp = 16.dp, round: Boolean = false, corner: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(if (round) CircleShape else RoundedCornerShape(corner))
            .background(bg)
            .border(1.dp, tint.copy(alpha = 0.22f), if (round) CircleShape else RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize)) }
}

@Composable
fun TagPill(text: String, bg: Color, fg: Color, border: Color, size: TextUnit = 10.sp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) { SText(text, size, fg, weight = FontWeight.SemiBold, maxLines = 1) }
}
