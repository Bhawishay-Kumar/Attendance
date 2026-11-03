package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

/** Session counts for the same period the hero percentage represents. */
@Immutable
data class AttendanceSessionStats(
    val attended: Int = 0,
    val missed: Int = 0,
    val conducted: Int = 0,
    val cancelled: Int = 0
)

/**
 * Four compact, non-interactive stat cards for the Home hero card.
 * Fully self-contained: only uses LocalGlassTheme, your two font families and Material icons.
 */
@Composable
fun AttendanceStatsRow(
    stats: AttendanceSessionStats,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_attendance_stats"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("Attended", stats.attended, Icons.Default.Check, Color(0xFF10B981), Color(0xFFD1FAE5), shown, 0, Modifier.weight(1f))
        StatCard("Missed", stats.missed, Icons.Default.Close, Color(0xFFEF4444), Color(0xFFFFE4E6), shown, 70, Modifier.weight(1f))
        StatCard("Conducted", stats.conducted, Icons.Default.CalendarMonth, Color(0xFF3B82F6), Color(0xFFDBEAFE), shown, 140, Modifier.weight(1f))
        StatCard("Cancelled", stats.cancelled, Icons.Default.Block, Color(0xFFF97316), Color(0xFFFFEDD5), shown, 210, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    lightBg: Color,
    appeared: Boolean,
    delayMs: Int,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val shape = RoundedCornerShape(16.dp)

    // Entrance: fade + rise + settle, staggered per card
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(520, delayMs, FastOutSlowInEasing),
        label = "enter"
    )
    // Smooth count-up / count-down when data changes
    val shownCount by animateIntAsState(
        targetValue = count.coerceAtLeast(0),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "count"
    )
    // Small bump on the number whenever the value changes
    val bump = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (bump.value == 1f && !appeared) return@LaunchedEffect
        bump.animateTo(1.18f, tween(110))
        bump.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    // Icon pops in with a springy overshoot after the card settles
    var iconIn by remember { mutableStateOf(false) }
    LaunchedEffect(appeared) {
        if (appeared) {
            delay(delayMs + 220L)
            iconIn = true
        }
    }
    val iconScale by animateFloatAsState(
        targetValue = if (iconIn) 1f else 0.3f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "iconScale"
    )
    // Soft glass sweep: idles, then glides across; each card offset so it ripples left to right
    val sweepT = rememberInfiniteTransition(label = "sweep")
    val sweep by sweepT.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5500
                0f at 0
                0f at 3000
                1f at 5500
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs * 4)
        ),
        label = "sweepX"
    )
    // Gentle breathing of the icon bloom
    val breathe by sweepT.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs * 3)
        ),
        label = "breathe"
    )
    val sheen = if (glass.isDark) 0.10f else 0.55f

    // Theme transitions animate instead of snapping
    val dark = glass.isDark
    val border by animateColorAsState(if (dark) Color(0x5C818CF8) else Color(0x80C7D2FE), tween(400), label = "border")
    val iconBg by animateColorAsState(if (dark) accent.copy(alpha = 0.20f) else lightBg, tween(400), label = "iconBg")
    val glow by animateColorAsState(if (dark) accent.copy(alpha = 0.28f) else accent.copy(alpha = 0.10f), tween(400), label = "glow")

    val cardBrush = if (dark)
        Brush.verticalGradient(listOf(Color(0x40312E81), Color(0x260B1228)))
    else
        Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x80EEF0FF)))

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 14.dp.toPx()
                val s = 0.94f + 0.06f * enter
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(cardBrush)
            .drawBehind {
                // soft accent bloom behind the icon, top-centre of the card
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(size.width / 2f, 18.dp.toPx()),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width / 2f, 18.dp.toPx()),
                    radius = size.width * 0.75f * breathe
                )
                // glass sweep band
                val x = -size.width * 0.6f + sweep * size.width * 2.2f
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = sheen), Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + size.width * 0.5f, size.height * 0.4f)
                    )
                )
            }
            .border(1.dp, border, shape)
            .padding(vertical = 9.dp, horizontal = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $count" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    rotationZ = (1f - iconScale) * -35f
                }
                .clip(CircleShape)
                .background(iconBg)
                .border(0.75.dp, accent.copy(alpha = if (dark) 0.55f else 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = "$shownCount",
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = glass.textPrimary,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            }
        )
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp,
            color = glass.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
