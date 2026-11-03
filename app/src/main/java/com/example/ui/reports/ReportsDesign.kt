package com.example.ui.reports

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.spring
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Individually designed dark ("cosmic") and light ("pastel alabaster") palettes for Reports. */
@Immutable
class RptPalette(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val text: Color,
    val textSec: Color,
    val textMuted: Color,
    val indigo: Color,
    val violet: Color,
    val cyan: Color,
    val emerald: Color,
    val amber: Color,
    val rose: Color,
    val trackBg: Color
)

fun rptPalette(isDark: Boolean): RptPalette = if (isDark) {
    RptPalette(
        isDark = true,
        bg = Color(0xFF070B14),
        card = Color(0xD9121826),
        cardAlt = Color(0xFF1A2234),
        border = Color(0x1AFFFFFF),
        text = Color(0xFFF8FAFC),
        textSec = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        indigo = Color(0xFF6366F1),
        violet = Color(0xFF8B5CF6),
        cyan = Color(0xFF22D3EE),
        emerald = Color(0xFF34D399),
        amber = Color(0xFFFBBF24),
        rose = Color(0xFFF87171),
        trackBg = Color(0x14FFFFFF)
    )
} else {
    RptPalette(
        isDark = false,
        bg = com.example.ui.theme.LightCanvasStart,
        card = com.example.ui.theme.LightCardSurface,
        cardAlt = com.example.ui.theme.LightSurfaceAlt,
        border = Color(0x244F46E5),
        text = Color(0xFF0F172A),
        textSec = Color(0xFF475569),
        textMuted = Color(0xFF64748B),
        indigo = Color(0xFF4F46E5),
        violet = Color(0xFF7C3AED),
        cyan = Color(0xFF0891B2),
        emerald = Color(0xFF059669),
        amber = Color(0xFFD97706),
        rose = Color(0xFFE11D48),
        trackBg = Color(0x1A4F46E5)
    )
}

/** True when the user disabled animations system-wide (Settings > Animator duration scale = 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Press-scale feedback + click, no ripple (matches the reference's active:scale behaviour). */
fun Modifier.pressClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(110), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Text with the report typography hierarchy; [tnum] enables tabular numerals. */
@Composable
fun RText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    display: Boolean = false,
    tnum: Boolean = false,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
    family: FontFamily? = null
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = family ?: if (display) OutfitFontFamily else PlusJakartaSansFontFamily,
            letterSpacing = letterSpacing.sp,
            fontFeatureSettings = if (tnum) "tnum" else null,
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
        )
    )
}

/** Glass card surface used by every Reports component. */
@Composable
fun GlassCard(
    p: RptPalette,
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val fill = if (accent != null) {
        Brush.linearGradient(listOf(accent.copy(alpha = if (p.isDark) 0.16f else 0.10f), p.card))
    } else {
        Brush.linearGradient(listOf(p.card, p.card))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, accent?.copy(alpha = 0.35f) ?: p.border), shape)
    ) { content() }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, size: Float = 11f) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) { RText(text, size, color, weight = FontWeight.Bold, maxLines = 1) }
}


/**
 * Diagonal highlight that sweeps across the element every [periodMs]. Apply AFTER clip/background.
 * The animation value is read in the draw phase only (no recomposition).
 */
fun Modifier.shineSweep(enabled: Boolean, periodMs: Int = 3600, strength: Float = 0.28f): Modifier = composed {
    if (!enabled) {
        this
    } else {
        val x = rememberInfiniteTransition(label = "shine").animateFloat(
            initialValue = 0f, targetValue = 1.8f,
            animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
            label = "shineX"
        )
        this.drawWithContent {
            drawContent()
            val band = size.width * 0.45f
            val start = (size.width + band) * x.value - band
            if (start < size.width) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(start + band, size.height * 0.6f)
                    )
                )
            }
        }
    }
}

/** Springy pop-in (overshoot) with a per-item delay; used for calendar cells and badges. */
@Composable
fun Modifier.popIn(index: Int, reduced: Boolean, key: Any? = Unit, stepMs: Long = 18L): Modifier {
    var shown by remember(key) { mutableStateOf(reduced) }
    LaunchedEffect(key) {
        if (!reduced) {
            delay(index.coerceAtMost(42) * stepMs)
            shown = true
        }
    }
    val sc by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "popIn"
    )
    return this.graphicsLayer {
        scaleX = sc
        scaleY = sc
        alpha = sc.coerceIn(0f, 1f)
    }
}

/** Light tap feedback, only when the user's haptic setting is on. Same effect type as the dock. */
@Composable
fun rememberReportHaptics(enabled: Boolean): () -> Unit {
    val h = com.example.ui.settings.rememberAppHaptics()
    return remember(h, enabled) {
        { if (enabled) h.click() }
    }
}
