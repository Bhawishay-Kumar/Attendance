package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class MarkState {
    NONE, PRESENT, ABSENT, AUTO_PRESENT
}

/**
 * Exact 1-to-1 replica of `.surf.bulk` from `attendance-app.html`:
 * - Header with Zap icon, "Bulk Attendance Actions", and bumping `marked/total` pill.
 * - Animated 3-stop gradient progress bar (`#3B6BFF -> #8B5CF6 -> #14D4A0`).
 * - 3-Column action row (`1fr 1fr 48dp`): `[ ✓ All Present ]`, `[ ✕ All Absent ]`, and `[ ↺ Reset ]`.
 */
@Composable
fun RedesignBulkAttendanceCard(
    markedCount: Int,
    totalCount: Int,
    isDark: Boolean,
    isLockedFutureDate: Boolean = false,
    onMarkAllPresent: () -> Unit,
    onMarkAllAbsent: () -> Unit,
    onResetDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalCount > 0) (markedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 700, easing = CubicBezierEasing(0.3f, 0.9f, 0.3f, 1f)),
        label = "bulkProgressBar"
    )

    // Bump animation on counter change (`@keyframes bump`)
    val bumpScale = remember { Animatable(1f) }
    LaunchedEffect(markedCount, totalCount) {
        bumpScale.snapTo(1f)
        bumpScale.animateTo(1.25f, tween(150, easing = FastOutSlowInEasing))
        bumpScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
    }

    val surfaceBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF161B2E).copy(alpha = 0.75f), // Nocturne Smoked Glass
                Color(0xFF0D1120).copy(alpha = 0.88f)  // Deep Obsidian Core
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                com.example.ui.theme.LightCardSurface,       // Pearl lavender glass
                com.example.ui.theme.LightPearl.copy(alpha = 0.82f)
            )
        )
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF6366F1).copy(alpha = 0.15f)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val mutedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val chipBg = if (isDark) Color(0xFF1E2538).copy(alpha = 0.65f) else com.example.ui.theme.LightSurfaceAlt
    val chipBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceBrush)
            .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        // Header Row: Zap icon + Title + Bumping Counter Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color(0xFF8B6DFF),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Bulk Attendance Actions",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val s = bumpScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(chipBg)
                    .border(1.dp, chipBorder, CircleShape)
                    .padding(horizontal = 11.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "$markedCount/$totalCount",
                    color = mutedColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Progress Bar (`.prog`)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(chipBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isDark) listOf(
                                Color(0xFF4F46E5), // Indigo
                                Color(0xFF7C3AED), // Amethyst Violet
                                Color(0xFF10B981)  // Emerald
                            ) else listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6),
                                Color(0xFF10B981)
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3-Column Buttons Row (`.brow`: `1fr 1fr 48dp`)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. All Present Button (`.btn.b-present`)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isDark) listOf(Color(0xFF10B981), Color(0xFF059669))
                            else listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    )
                    .clickable(enabled = !isLockedFutureDate) { onMarkAllPresent() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "All Present",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. All Absent Button (`.btn.b-absent`: Light soft rose in Light mode / Vivid rose in Dark mode)
            val absentBgBrush = if (isDark) {
                Brush.linearGradient(colors = listOf(Color(0xFFF43F5E), Color(0xFFE11D48)))
            } else {
                Brush.linearGradient(colors = listOf(Color(0xFFFFECF2), Color(0xFFFFE0EA)))
            }
            val absentFg = if (isDark) Color.White else Color(0xFFE11D48)
            val absentBorder = if (isDark) Color.Transparent else Color(0xFFF43F5E).copy(alpha = 0.40f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(absentBgBrush)
                    .border(1.5.dp, absentBorder, RoundedCornerShape(14.dp))
                    .clickable(enabled = !isLockedFutureDate) { onMarkAllAbsent() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = absentFg,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "All Absent",
                        color = absentFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 3. Reset All Button (`.btn.b-ghost`, 48dp square - Neutral Translucent Glass)
            val ghostBg = if (isDark) Color.White.copy(alpha = 0.08f) else com.example.ui.theme.LightSurfaceAlt
            val ghostBorder = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE2E8F0)
            val ghostFg = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ghostBg)
                    .border(1.5.dp, ghostBorder, RoundedCornerShape(14.dp))
                    .clickable(enabled = !isLockedFutureDate) { onResetDay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Reset all attendance for this day",
                    tint = ghostFg,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Exact 1-to-1 replica of `.surf.sc` Individual Subject Card from `attendance-app.html`
 * with your specific requirement:
 * - NO individual "No Class" button (only Present & Absent before marking).
 * - Unmarked buttons are soft/light-tinted; once marked, `.slot2` smoothly crossfades/expands into
 *   the full-width `.res` row (`1fr 46dp`) containing the `.strip` ("Marked present" in green or
 *   "Marked absent" in red with animated stroke draw + diagonal sheen sweep) + `46dp` Undo (`↺`) button!
 * - Includes card `.flash` halo pulse, `.icoPop` icon bounce, `.pill.pop` status badge bounce, and 9-particle burst.
 */
@Composable
fun RedesignSubjectAttendanceCard(
    periodLabel: String,          // e.g. "I", "II", "III–IV (2x)"
    timeRange: String,            // e.g. "09:30 AM – 10:20 AM"
    subjectTitle: String,         // Full Subject Name: e.g. "Quality Evaluation of Fibers and Yarns"
    subjectCode: String,          // e.g. "BHT503"
    subjectType: String,          // e.g. "LECTURE" or "2 HRS • 2X CREDIT LAB"
    accentColor: Color,           // Subject `--ac` color
    markState: MarkState,
    isDark: Boolean,
    isLockedFutureDate: Boolean = false,
    subjectIcon: @Composable (Color) -> Unit,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onUndoMark: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Flash & Particle Burst animations triggered whenever markState transitions to PRESENT or ABSENT
    val flashProgress = remember { Animatable(1f) }
    val iconPopScale = remember { Animatable(1f) }
    val pillPopScale = remember { Animatable(1f) }
    val sheenProgress = remember { Animatable(-0.5f) }
    val particleProgress = remember { Animatable(1f) }
    var previousState by remember { mutableStateOf(markState) }

    LaunchedEffect(markState) {
        if (markState != previousState && (markState == MarkState.PRESENT || markState == MarkState.ABSENT)) {
            launch {
                flashProgress.snapTo(0f)
                flashProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
            }
            launch {
                iconPopScale.snapTo(1f)
                iconPopScale.animateTo(1.2f, tween(220, easing = FastOutSlowInEasing))
                iconPopScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
            }
            launch {
                pillPopScale.snapTo(0.4f)
                pillPopScale.animateTo(1.2f, tween(240, easing = FastOutSlowInEasing))
                pillPopScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
            }
            launch {
                sheenProgress.snapTo(-0.5f)
                delay(120)
                sheenProgress.animateTo(1.3f, tween(850, easing = FastOutSlowInEasing))
            }
            launch {
                particleProgress.snapTo(0f)
                particleProgress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            }
        }
        previousState = markState
    }

    val flashColor = when (markState) {
        MarkState.PRESENT, MarkState.AUTO_PRESENT -> Color(0xFF1EDCA0)
        MarkState.ABSENT -> Color(0xFFFF5082)
        MarkState.NONE -> Color.Transparent
    }

    val cardBorderColor by animateColorAsState(
        targetValue = when (markState) {
            MarkState.PRESENT, MarkState.AUTO_PRESENT -> Color(0xFF10B981).copy(alpha = 0.65f)
            MarkState.ABSENT -> Color(0xFFF43F5E).copy(alpha = 0.65f)
            MarkState.NONE -> if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF6366F1).copy(alpha = 0.15f)
        },
        animationSpec = tween(400),
        label = "cardBorderColor"
    )

    val surfaceBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF161B2E).copy(alpha = 0.75f), // Nocturne Smoked Glass
                Color(0xFF0D1120).copy(alpha = 0.88f)  // Deep Obsidian Core
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                com.example.ui.theme.LightCardSurface,       // Pearl lavender glass
                com.example.ui.theme.LightPearl.copy(alpha = 0.82f)
            )
        )
    }

    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val mutedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val codeColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5) // Clean Indigo

    val periodBadgeBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
        )
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(surfaceBrush)
                .border(1.5.dp, cardBorderColor, RoundedCornerShape(24.dp))
                .drawWithContent {
                    // Radial subject `--ac` ambient glow (`sc::before`)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(36.dp.toPx(), 80.dp.toPx()),
                            radius = 120.dp.toPx()
                        ),
                        center = Offset(36.dp.toPx(), 80.dp.toPx()),
                        radius = 120.dp.toPx()
                    )
                    drawContent()
                    // Flash inner/outer ring pulse (`sc.flash::after`)
                    val fp = flashProgress.value
                    if (fp < 1f && flashColor != Color.Transparent) {
                        drawRoundRect(
                            color = flashColor.copy(alpha = (1f - fp) * 0.55f),
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            style = Stroke(width = (1f + fp * 14f).dp.toPx())
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ROW 1 (`.r1`): Period Badge + Time Range + Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // `.period` badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(periodBadgeBrush)
                        .padding(horizontal = 11.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Period $periodLabel",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // `.time` label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = mutedColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = timeRange,
                        color = mutedColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // `.pill` Status Badge (`Not Marked`, `Present`, `Absent`, `Auto-Present`)
                val (pillBg, pillBorder, pillFg, pillText, pillIcon) = when (markState) {
                    MarkState.PRESENT -> PillStyle(
                        bg = if (isDark) Color(0xFF0A5A5A).copy(alpha = 0.50f) else Color(0xFFD2F7E6).copy(alpha = 0.90f),
                        border = if (isDark) Color(0xFF2DE6B4).copy(alpha = 0.70f) else Color(0xFF28BE8C).copy(alpha = 0.55f),
                        fg = if (isDark) Color(0xFF3DF0B8) else Color(0xFF0A7A55),
                        text = "Present",
                        icon = Icons.Rounded.CheckCircleOutline
                    )
                    MarkState.AUTO_PRESENT -> PillStyle(
                        bg = if (isDark) Color(0xFF0A5A5A).copy(alpha = 0.55f) else Color(0xFFD2F7E6),
                        border = Color(0xFF27D3F0).copy(alpha = 0.75f),
                        fg = if (isDark) Color(0xFF3DF0B8) else Color(0xFF0A7A55),
                        text = "Auto-Present",
                        icon = Icons.Rounded.Verified
                    )
                    MarkState.ABSENT -> PillStyle(
                        bg = if (isDark) Color(0xFF6E143C).copy(alpha = 0.50f) else Color(0xFFFFE1E9).copy(alpha = 0.90f),
                        border = if (isDark) Color(0xFFFF5A8C).copy(alpha = 0.70f) else Color(0xFFF05A82).copy(alpha = 0.50f),
                        fg = if (isDark) Color(0xFFFF86A8) else Color(0xFFC2173E),
                        text = "Absent",
                        icon = Icons.Rounded.Cancel
                    )
                    MarkState.NONE -> PillStyle(
                        bg = if (isDark) Color(0xFF1E2538).copy(alpha = 0.60f) else com.example.ui.theme.LightSurfaceAlt,
                        border = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0),
                        fg = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        text = "Not Marked",
                        icon = Icons.Rounded.RadioButtonUnchecked
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .graphicsLayer {
                            val s = pillPopScale.value
                            scaleX = s
                            scaleY = s
                        }
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.5.dp, pillBorder, CircleShape)
                        .padding(start = 8.dp, end = 11.dp)
                ) {
                    Icon(
                        imageVector = pillIcon,
                        contentDescription = null,
                        tint = pillFg,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = pillText,
                        color = pillFg,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ROW 2 (`.r2`): 48dp Glowing Subject Icon Box + Full Subject Name + Code & Type
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val s = iconPopScale.value
                            scaleX = s
                            scaleY = s
                        }
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.28f),
                                    accentColor.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(1.5.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    subjectIcon(accentColor)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subjectTitle, // Full subject name displayed clearly!
                        color = textColor,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = subjectCode,
                            color = codeColor,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " • $subjectType",
                            color = mutedColor,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ROW 3 (`.slot2`): Smoothly transitions between Unmarked `.acts` (Present | Absent)
            // and Marked `.res` (Full-Width `.strip` "Marked present" / "Marked absent" + 46dp Undo `↺` button)
            AnimatedContent(
                targetState = markState,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 3 })
                        .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(240)) { -it / 3 })
                },
                label = "slot2Transition"
            ) { state ->
                when (state) {
                    MarkState.NONE -> {
                        // UNMARKED STATE: Soft/Light buttons before marking (NO "No Class" button)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Soft Emerald mist before marking
                            val unmarkedPresentBg = if (isDark) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.12f)
                            val unmarkedPresentBorder = Color(0xFF10B981).copy(alpha = 0.45f)
                            val unmarkedPresentFg = if (isDark) Color(0xFF34D399) else Color(0xFF059669)

                            Box(
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(unmarkedPresentBg)
                                    .border(1.5.dp, unmarkedPresentBorder, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isLockedFutureDate) { onMarkPresent() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = unmarkedPresentFg,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        text = "Present",
                                        color = unmarkedPresentFg,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Soft Rose mist before marking
                            val unmarkedAbsentBg = if (isDark) Color(0xFFF43F5E).copy(alpha = 0.14f) else Color(0xFFF43F5E).copy(alpha = 0.10f)
                            val unmarkedAbsentBorder = Color(0xFFF43F5E).copy(alpha = 0.45f)
                            val unmarkedAbsentFg = if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(unmarkedAbsentBg)
                                    .border(1.5.dp, unmarkedAbsentBorder, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isLockedFutureDate) { onMarkAbsent() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = unmarkedAbsentFg,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        text = "Absent",
                                        color = unmarkedAbsentFg,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    MarkState.PRESENT, MarkState.ABSENT -> {
                        // MARKED STATE (`.res`): Expanding Solid Emerald or Rose `.strip` + `46dp` Undo Button (`↺`)
                        val isPres = state == MarkState.PRESENT
                        val stripBrush = if (isPres) {
                            Brush.linearGradient(colors = listOf(Color(0xFF059669), Color(0xFF10B981)))
                        } else {
                            Brush.linearGradient(colors = listOf(Color(0xFFE11D48), Color(0xFFF43F5E)))
                        }

                        // Neutral Translucent Glass Undo Button
                        val ghostBg = if (isDark) Color.White.copy(alpha = 0.08f) else com.example.ui.theme.LightSurfaceAlt
                        val ghostBorder = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE2E8F0)
                        val ghostFg = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Expanded `.strip` with diagonal sheen sweep
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(stripBrush)
                                    .drawWithContent {
                                        drawContent()
                                        val sp = sheenProgress.value
                                        if (sp > -0.45f && sp < 1.25f) {
                                            val sw = size.width * 0.4f
                                            val sx = size.width * sp
                                            withTransform({
                                                rotate(degrees = -18f, pivot = Offset(sx + sw / 2f, size.height / 2f))
                                            }) {
                                                drawRect(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            Color.White.copy(alpha = 0.45f),
                                                            Color.Transparent
                                                        ),
                                                        startX = sx,
                                                        endX = sx + sw
                                                    ),
                                                    topLeft = Offset(sx, -size.height * 0.2f),
                                                    size = Size(sw, size.height * 1.4f)
                                                )
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPres) Icons.Rounded.Check else Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (isPres) "Marked present" else "Marked absent",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 46dp Square Undo Button (`.btn.b-ghost[data-a="undo"]`)
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ghostBg)
                                    .border(1.5.dp, ghostBorder, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isLockedFutureDate) { onUndoMark() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Undo attendance",
                                    tint = ghostFg,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }

                    MarkState.AUTO_PRESENT -> {
                        // Locked Auto-Present Strip for Library / Seminar / Digital Library
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0D9488), Color(0xFF0891B2))
                                    )
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Auto-Present (Protected)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 9-Particle Sparkle Burst Overlay (`function burst(btn, st)`)
        val pp = particleProgress.value
        if (pp < 1f && (markState == MarkState.PRESENT || markState == MarkState.ABSENT)) {
            val colors = if (markState == MarkState.PRESENT) {
                listOf(Color(0xFF19E0A6), Color(0xFF5FF5CF), Color(0xFFB6FFE9))
            } else {
                listOf(Color(0xFFFF4F81), Color(0xFFFF8FB0), Color(0xFFFFC2D4))
            }
            Canvas(
                modifier = Modifier
                    .matchParentSize()
            ) {
                val cx = size.width * 0.45f
                val cy = size.height * 0.82f
                for (n in 0 until 9) {
                    val angle = (n / 9f) * (Math.PI * 2f).toFloat() + 0.3f
                    val dist = (38.dp.toPx() + (n % 3) * 14.dp.toPx()) * pp
                    val px = cx + cos(angle) * dist
                    val py = cy + sin(angle) * dist + (12.dp.toPx() * pp * pp)
                    val radius = (4.dp.toPx() * (1f - pp)).coerceAtLeast(0f)
                    drawCircle(
                        color = colors[n % 3].copy(alpha = (1f - pp).coerceIn(0f, 1f)),
                        radius = radius,
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}

private data class PillStyle(
    val bg: Color,
    val border: Color,
    val fg: Color,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SubjectIcon(
    iconKey: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val vector = when (iconKey) {
        "flask" -> Icons.Rounded.Science
        "microscope" -> Icons.Rounded.Biotech
        "grid" -> Icons.Rounded.GridOn
        "scissors" -> Icons.Rounded.ContentCut
        "workflow" -> Icons.Rounded.AccountTree
        "book", "library" -> Icons.AutoMirrored.Rounded.MenuBook
        "monitor", "laptop" -> Icons.Rounded.Computer
        "presentation", "seminar" -> Icons.Rounded.CoPresent
        else -> Icons.Rounded.School
    }
    Icon(
        imageVector = vector,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(24.dp)
    )
}