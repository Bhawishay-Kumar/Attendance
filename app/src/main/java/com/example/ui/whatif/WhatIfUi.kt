package com.example.ui.whatif

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.whatif.TargetStatus
import com.example.ui.settings.rememberMotionEnabled
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import java.util.Locale

// ───────────────────────── formatting ─────────────────────────

fun fmtPct(v: Double): String = String.format(Locale.US, "%.1f%%", v)

fun fmtDelta(v: Double?): String = when {
    v == null -> "—"
    v > 0.0 -> String.format(Locale.US, "+%.1f pts", v)
    v < 0.0 -> String.format(Locale.US, "%.1f pts", v)
    else -> "no change"
}

fun hoursLabel(h: Int): String = if (h == 1) "1 hour" else "$h hours"

// ───────────────────────── motion helpers (honour system animation settings) ─────────────────────────

@Composable
fun wiFloat(target: Float, ms: Int = 450, label: String = "wi"): Float {
    val motion = rememberMotionEnabled()
    val v by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motion) tween(ms, easing = FastOutSlowInEasing) else snap(),
        label = label
    )
    return v
}

@Composable
fun wiColor(target: Color, ms: Int = 250): Color {
    val motion = rememberMotionEnabled()
    val v by animateColorAsState(
        targetValue = target,
        animationSpec = if (motion) tween(ms) else snap(),
        label = "wiColor"
    )
    return v
}

// ───────────────────────── semantic colours ─────────────────────────

@Composable
fun statusColor(s: TargetStatus): Color {
    val t = LocalGlassTheme.current
    return when (s) {
        TargetStatus.MET -> t.emeraldNeon
        TargetStatus.NOT_MET -> t.crimsonRose
        TargetStatus.NO_DATA -> t.textMuted
    }
}

fun statusLabel(s: TargetStatus): String = when (s) {
    TargetStatus.MET -> "Met"
    TargetStatus.NOT_MET -> "Not met"
    TargetStatus.NO_DATA -> "No data"
}

// ───────────────────────── building blocks ─────────────────────────

@Composable
fun WiText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalGlassTheme.current.textPrimary,
    display: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = if (display) OutfitFontFamily else PlusJakartaSansFontFamily,
        textAlign = align, maxLines = maxLines,
        lineHeight = size * 1.35f
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalGlassTheme.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.cardSurface)
            .border(1.dp, accent?.copy(alpha = 0.55f) ?: t.cardBorder, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun WiPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { WiText(text, size = 11.sp, weight = FontWeight.SemiBold, color = color) }
}

@Composable
fun WiPrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalGlassTheme.current
    Box(
        modifier = modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(t.royalIndigo, t.electricViolet)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { WiText(text, size = 15.sp, weight = FontWeight.SemiBold, color = Color.White, align = TextAlign.Center) }
}

@Composable
fun WiGhostButton(text: String, modifier: Modifier = Modifier, color: Color = LocalGlassTheme.current.textPrimary, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalGlassTheme.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(1.dp, t.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { WiText(text, size = 13.sp, weight = FontWeight.SemiBold, color = color, align = TextAlign.Center) }
}

@Composable
fun WiSegment(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val t = LocalGlassTheme.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, t.cardBorder, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val bg = wiColor(if (i == selected) t.royalIndigo else Color.Transparent)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                WiText(
                    label, size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.Center,
                    color = if (i == selected) Color.White else t.textSecondary
                )
            }
        }
    }
}

@Composable
fun WiProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val t = LocalGlassTheme.current
    val f = wiFloat(fraction.coerceIn(0f, 1f), 700, "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(t.cardBorder)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun AnimatedPct(value: Double, modifier: Modifier = Modifier, size: TextUnit = 44.sp, color: Color = LocalGlassTheme.current.textPrimary) {
    val v = wiFloat(value.toFloat(), 700, "pct")
    WiText(fmtPct(v.toDouble()), modifier, size = size, weight = FontWeight.Bold, color = color, display = true)
}

@Composable
fun WiHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)?, trailing: @Composable (RowScope.() -> Unit) = {}) {
    val t = LocalGlassTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, t.cardBorder, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { WiText("‹", size = 22.sp, weight = FontWeight.Bold, color = t.textPrimary) }
        }
        Column(Modifier.weight(1f)) {
            WiText(title, size = 20.sp, weight = FontWeight.Bold, display = true, maxLines = 1)
            if (subtitle != null) WiText(subtitle, size = 12.sp, color = t.textSecondary, maxLines = 1)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    WiText(text.uppercase(Locale.ROOT), modifier.padding(top = 6.dp, bottom = 2.dp), size = 11.sp, weight = FontWeight.Bold, color = LocalGlassTheme.current.textMuted)
}
