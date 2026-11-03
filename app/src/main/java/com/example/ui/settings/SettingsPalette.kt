package com.example.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import kotlin.random.Random
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colours for the Settings screen.
 *
 * UI-upgrade notes (presentation only):
 *  - Dark  : neutral glass surfaces + white hairlines (same tokens as Home / Reports - no blue wash).
 *  - Light : alabaster glass surfaces, slate text, soft indigo hairlines.
 * Every value animates when the theme flips so the page cross-fades instead of snapping.
 * The constructor stays source-compatible for existing callers (they only use [rememberSettingsPalette]).
 */
@Immutable
class SettingsPalette(
    val dark: Boolean,
    val cardBg: Color,
    val cardBorder: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val inset: Color,
    val insetBorder: Color,
    val segTrack: Color,
    val segTrackBorder: Color,
    val divider: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val toggleOff: Color,
    // ── presentation-only additions ──
    val cardBgTop: Color = cardBg,
    val cardBorderHi: Color = cardBorder
) {
    val indigo = Color(0xFF4F46E5)
    val indigoLight = Color(0xFF6366F1)
    val blue = Color(0xFF3B82F6)
    val cyan = Color(0xFF06B6D4)
    val cyanSoft = Color(0xFF22D3EE)
    val violet = Color(0xFFA855F7)
    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val rose = Color(0xFFF43F5E)
    val accentText get() = if (dark) Color(0xFF67E8F9) else Color(0xFF2563EB)
    val sky get() = Color(0xFF0284C7)

    /** Primary accent gradient (selected segments, primary buttons, active tracks). */
    val accentBrush: Brush get() = Brush.horizontalGradient(listOf(Color(0xFF3B5BFF), Color(0xFF5B52EE), Color(0xFF7C3AED)))

    /** Body copy on cards (slightly stronger than [textSecondary]). */
    val body get() = if (dark) Color(0xFFCBD5E1) else Color(0xFF334155)

    /** Soft glow colour used behind selected / active elements. */
    val glow get() = if (dark) Color(0x666366F1) else Color(0x4D6366F1)
}

@Composable
fun rememberSettingsPalette(dark: Boolean): SettingsPalette {
    val spec = tween<Color>(350)
    val cardBg by animateColorAsState(if (dark) Color(0xE00D1120) else Color(0xD6F0F2FF), spec, label = "cardBg")
    val cardBgTop by animateColorAsState(if (dark) Color(0xBF161B2E) else Color(0xE8F5F6FF), spec, label = "cardBgTop")
    val cardBorder by animateColorAsState(if (dark) Color(0x1AFFFFFF) else Color(0x266366F1), spec, label = "cardBorder")
    val cardBorderHi by animateColorAsState(if (dark) Color(0x38FFFFFF) else Color(0xFFF5F6FF), spec, label = "cardBorderHi")
    val text by animateColorAsState(if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A), spec, label = "text")
    val sec by animateColorAsState(if (dark) Color(0xFF94A3B8) else Color(0xFF475569), spec, label = "sec")
    val muted by animateColorAsState(if (dark) Color(0xFF64748B) else Color(0xFF64748B), spec, label = "muted")
    val inset by animateColorAsState(if (dark) Color(0x6620294A) else Color(0xB3E7EBFF), spec, label = "inset")
    val insetBorder by animateColorAsState(if (dark) Color(0x30818CF8) else Color(0x1F4F46E5), spec, label = "insetBorder")
    val seg by animateColorAsState(if (dark) Color(0x8C161E3A) else Color(0xB3E0E5FF), spec, label = "seg")
    val segBorder by animateColorAsState(if (dark) Color(0x38818CF8) else Color(0x244F46E5), spec, label = "segBorder")
    val divider by animateColorAsState(if (dark) Color(0x26818CF8) else Color(0x1F4F46E5), spec, label = "divider")
    val chipBg by animateColorAsState(if (dark) Color(0x2E4F5BB8) else Color(0xFFF5F6FF), spec, label = "chipBg")
    val chipBorder by animateColorAsState(if (dark) Color(0x4D818CF8) else Color(0x244F46E5), spec, label = "chipBorder")
    val off by animateColorAsState(if (dark) Color(0xFF2B3548) else Color(0xFFCBD5E1), spec, label = "off")
    return SettingsPalette(
        dark, cardBg, cardBorder, text, sec, muted, inset, insetBorder, seg, segBorder, divider, chipBg, chipBorder, off,
        cardBgTop = cardBgTop, cardBorderHi = cardBorderHi
    )
}

/**
 * Frosted card: 24dp radius, vertical surface gradient (lit from the top), hairline border that is
 * brighter at the top edge, soft blue-violet shadow in light mode (no shadow in dark → cheaper to draw).
 */
fun Modifier.glassCard(p: SettingsPalette, radius: Dp = 24.dp): Modifier {
    // Exactly the app's standard card (Attendance / Home): diagonal smoked-glass gradient, 1.5dp hairline, no shadow.
    val shape: Shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Brush.linearGradient(listOf(p.cardBgTop, p.cardBg)))
        .border(1.5.dp, p.cardBorder, shape)
}

fun Modifier.insetBox(p: SettingsPalette, radius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(p.inset).border(1.dp, p.insetBorder, shape)
}

/**
 * Inset row surface that visibly "arms" when [active]: border + wash turn accent-coloured.
 * (Active state is additionally conveyed by the switch thumb position/tick, so colour is never the only cue.)
 */
@Composable
fun Modifier.activeInset(p: SettingsPalette, active: Boolean, radius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val bg by animateColorAsState(
        if (active) (if (p.dark) Color(0x2E6366F1) else Color(0x1A6366F1)) else p.inset,
        tween(300), label = "rowBg"
    )
    val bd by animateColorAsState(
        if (active) (if (p.dark) Color(0x808B93FF) else Color(0x666366F1)) else p.insetBorder,
        tween(300), label = "rowBd"
    )
    return this.clip(shape).background(bg).border(1.dp, bd, shape)
}
