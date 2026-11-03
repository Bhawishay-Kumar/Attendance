package com.example.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Outer Hero Card wrapper with animated aurora mesh glow & shimmering glass border.
 * Wraps the Top Gauge/Eligibility Row, your existing untouched 4 QuickStat cards,
 * and the Bottom Target Progress Bar.
 */
@Composable
fun UpgradedHeroCardContainer(
    percentage: Float,
    targetPercentage: Float = 80f,
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_bg_transition")

    val auroraDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_drift"
    )

    val borderGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow"
    )

    val dynamicAccent by animateColorAsState(
        targetValue = when {
            percentage <= 0f -> Color(0xFF6366F1) // Royal Indigo when 0% / unmarked
            percentage >= targetPercentage + 4f -> Color(0xFF10B981) // Emerald Safe
            percentage >= targetPercentage -> Color(0xFFF59E0B) // Amber Zone
            else -> Color(0xFFF43F5E) // Rose Danger
        },
        animationSpec = tween(durationMillis = 600),
        label = "dynamic_accent"
    )

    val cardShape = RoundedCornerShape(28.dp)
    val baseBg = if (isDarkMode) Color(0xFF0F1626).copy(alpha = 0.84f) else com.example.ui.theme.LightCardSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(baseBg)
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderGlowAlpha),
                        dynamicAccent.copy(alpha = borderGlowAlpha * 0.95f),
                        Color(0xFF8B5CF6).copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.08f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 900f)
                ),
                shape = cardShape
            )
    ) {
        // Animated ambient cosmic aurora background
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // Left ambient orb behind the radial gauge
            val leftCenter = Offset(
                x = w * (0.18f + 0.05f * auroraDrift),
                y = h * (0.25f + 0.04f * (1f - auroraDrift))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dynamicAccent.copy(alpha = if (isDarkMode) 0.18f else 0.10f),
                        Color.Transparent
                    ),
                    center = leftCenter,
                    radius = w * 0.46f
                ),
                center = leftCenter,
                radius = w * 0.46f
            )

            // Right ambient orb behind eligibility cards
            val rightCenter = Offset(
                x = w * (0.84f - 0.06f * auroraDrift),
                y = h * (0.20f + 0.06f * auroraDrift)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6).copy(alpha = if (isDarkMode) 0.15f else 0.08f),
                        Color.Transparent
                    ),
                    center = rightCenter,
                    radius = w * 0.44f
                ),
                center = rightCenter,
                radius = w * 0.44f
            )

            // Subtle bottom glow near the target progress bar
            val bottomCenter = Offset(w * 0.5f, h * 0.95f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = if (isDarkMode) 0.09f else 0.05f),
                        Color.Transparent
                    ),
                    center = bottomCenter,
                    radius = w * 0.50f
                ),
                center = bottomCenter,
                radius = w * 0.50f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

/**
 * Top Section of Hero Card:
 * Left: Animated Multi-Layer Radial Attendance Gauge
 * Right: Exam Eligibility & Stipend Eligibility Glass Cards
 */
@Composable
fun UpgradedHeroTopSection(
    percentage: Float,
    conductedCount: Int,
    examTarget: Int,
    stipendTarget: Int,
    periodLabel: String = "(This Month)",
    isDarkMode: Boolean = true,
    onExamClick: (() -> Unit)? = null,
    onStipendClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroAnimatedRadialGauge(
            percentage = percentage,
            conductedCount = conductedCount,
            targetPercentage = stipendTarget.toFloat(),
            periodLabel = periodLabel,
            isDarkMode = isDarkMode,
            modifier = Modifier.size(140.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroEligibilityGlassCard(
                title = "Exam Eligibility",
                targetPercent = examTarget,
                currentPercent = percentage,
                conductedCount = conductedCount,
                icon = Icons.Rounded.School,
                accentColor = Color(0xFF818CF8), // Indigo glow
                isDarkMode = isDarkMode,
                onClick = onExamClick
            )

            HeroEligibilityGlassCard(
                title = "Stipend Eligibility",
                targetPercent = stipendTarget,
                currentPercent = percentage,
                conductedCount = conductedCount,
                icon = Icons.Rounded.BarChart,
                accentColor = Color(0xFF2DD4BF), // Teal glow
                isDarkMode = isDarkMode,
                onClick = onStipendClick
            )
        }
    }
}

@Composable
fun HeroAnimatedRadialGauge(
    percentage: Float,
    conductedCount: Int,
    targetPercentage: Float,
    periodLabel: String = "(This Month)",
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clampedPct = percentage.coerceIn(0f, 100f)

    val animatedPct by animateFloatAsState(
        targetValue = clampedPct,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "radial_pct_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gauge_infinite")
    val dialRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dial_rotation"
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    val primaryText = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val ringTrackColor = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.90f) else Color(0xFFE2E8F0)

    val sweepColors = when {
        conductedCount == 0 && clampedPct == 0f -> listOf(Color(0xFF6366F1), Color(0xFF38BDF8))
        clampedPct >= targetPercentage -> listOf(Color(0xFF6366F1), Color(0xFF10B981), Color(0xFF2DD4BF))
        clampedPct >= targetPercentage - 5f -> listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
        else -> listOf(Color(0xFFF43F5E), Color(0xFFFB7185))
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 11.dp.toPx()
            val pad = strokeWidth / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val topLeft = Offset(pad, pad)
            val radius = arcSize.width / 2f

            // Inner ambient core glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        sweepColors.first().copy(alpha = if (isDarkMode) 0.12f * haloPulse else 0.06f),
                        Color.Transparent
                    )
                ),
                radius = radius - strokeWidth * 0.35f,
                center = center
            )

            // Subtle rotating precision ticks around outer perimeter
            rotate(degrees = dialRotation, pivot = center) {
                val ticks = 36
                for (i in 0 until ticks) {
                    val angleRad = Math.toRadians(i * (360.0 / ticks))
                    val rOuter = size.width / 2f - 1.dp.toPx()
                    val rInner = rOuter - 3.dp.toPx()
                    drawLine(
                        color = secondaryText.copy(alpha = if (i % 3 == 0) 0.30f else 0.10f),
                        start = Offset(
                            x = center.x + (rInner * cos(angleRad)).toFloat(),
                            y = center.y + (rInner * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            x = center.x + (rOuter * cos(angleRad)).toFloat(),
                            y = center.y + (rOuter * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Background Track Ring
            drawArc(
                color = ringTrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Subtle idle shimmer ring when 0.0% so it still looks alive and premium
            if (animatedPct <= 0.5f) {
                rotate(degrees = dialRotation * 1.5f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF6366F1).copy(alpha = 0.35f * haloPulse),
                                Color(0xFF2DD4BF).copy(alpha = 0.45f * haloPulse),
                                Color.Transparent
                            ),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth * 0.45f, cap = StrokeCap.Round)
                    )
                }
            }

            // Target Notch Marker on the Ring (e.g., at 80%)
            val targetAngle = -90f + (targetPercentage.coerceIn(0f, 100f) / 100f) * 360f
            val targetRad = Math.toRadians(targetAngle.toDouble())
            val nIn = radius - strokeWidth * 0.62f
            val nOut = radius + strokeWidth * 0.62f
            drawLine(
                color = Color(0xFFFBBF24).copy(alpha = 0.85f),
                start = Offset(
                    x = center.x + (nIn * cos(targetRad)).toFloat(),
                    y = center.y + (nIn * sin(targetRad)).toFloat()
                ),
                end = Offset(
                    x = center.x + (nOut * cos(targetRad)).toFloat(),
                    y = center.y + (nOut * sin(targetRad)).toFloat()
                ),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Active Progress Arc + Glowing End Cap
            val sweepAngle = (animatedPct / 100f) * 360f
            if (sweepAngle > 0.5f) {
                // Soft glow under-arc
                drawArc(
                    brush = Brush.sweepGradient(colors = sweepColors + sweepColors.first(), center = center),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 1.45f, cap = StrokeCap.Round),
                    alpha = 0.24f * haloPulse
                )

                // Main gradient arc
                drawArc(
                    brush = Brush.sweepGradient(colors = sweepColors + sweepColors.first(), center = center),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Glowing tip dot
                val tipRad = Math.toRadians((-90f + sweepAngle).toDouble())
                val tipOffset = Offset(
                    x = center.x + (radius * cos(tipRad)).toFloat(),
                    y = center.y + (radius * sin(tipRad)).toFloat()
                )
                drawCircle(
                    color = Color.White,
                    radius = strokeWidth * 0.34f,
                    center = tipOffset
                )
            }
        }

        // Center Labels (Preserving exact text hierarchy: Percentage, ATTENDANCE, (This Month))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = String.format(Locale.US, "%.1f%%", animatedPct),
                color = primaryText,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.8).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ATTENDANCE",
                color = secondaryText,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )
            Text(
                text = periodLabel,
                color = secondaryText.copy(alpha = 0.78f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HeroEligibilityGlassCard(
    title: String,
    targetPercent: Int,
    currentPercent: Float,
    conductedCount: Int,
    icon: ImageVector,
    accentColor: Color,
    isDarkMode: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val isUnmarked = conductedCount == 0 && currentPercent <= 0f
    val isSafe = !isUnmarked && currentPercent >= targetPercent

    val badgeAccent by animateColorAsState(
        targetValue = when {
            isUnmarked -> Color(0xFF94A3B8)
            isSafe -> Color(0xFF34D399)
            else -> Color(0xFFFB7185)
        },
        animationSpec = tween(400),
        label = "badge_color"
    )

    val badgeText = when {
        isUnmarked -> String.format(Locale.US, "%.1f%% • Mark", currentPercent)
        isSafe -> String.format(Locale.US, "%.1f%% • Safe", currentPercent)
        else -> String.format(Locale.US, "%.1f%% • Low", currentPercent)
    }

    val barProgress by animateFloatAsState(
        targetValue = if (targetPercent > 0) (currentPercent / targetPercent.toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "mini_bar_progress"
    )

    val shape = RoundedCornerShape(18.dp)
    val cardSurface = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.65f) else com.example.ui.theme.LightPearl
    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            accentColor.copy(alpha = if (isDarkMode) 0.35f else 0.25f),
            if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color(0xFFCBD5E1)
        )
    )
    val titleColor = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subtitleColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        cardSurface,
                        accentColor.copy(alpha = if (isDarkMode) 0.11f else 0.06f)
                    )
                )
            )
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Status Pill (e.g. "0.0% • Mark")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeAccent.copy(alpha = 0.15f))
                    .border(0.8.dp, badgeAccent.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$targetPercent% Target",
                color = subtitleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            // Subtle animated progress indicator bar
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barProgress)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(accentColor, badgeAccent)))
                )
            }
        }
    }
}

/**
 * Bottom Section of Hero Card:
 * Animated Target vs. Current Progress Bar with pulsing bullseye icon and threshold marker
 */
@Composable
fun UpgradedHeroBottomTargetBar(
    targetPercent: Int,
    currentPercent: Float,
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clampedCurrent = currentPercent.coerceIn(0f, 100f)

    val animatedFillFraction by animateFloatAsState(
        targetValue = clampedCurrent / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottom_bar_fill"
    )

    val animatedCurrentValue by animateFloatAsState(
        targetValue = clampedCurrent,
        animationSpec = tween(durationMillis = 500),
        label = "bottom_bar_text"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bullseye_pulse")
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    val primaryText = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569)
    val trackColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    val activeBarGradient = when {
        clampedCurrent >= targetPercent -> listOf(Color(0xFF6366F1), Color(0xFF10B981), Color(0xFF34D399))
        clampedCurrent >= targetPercent - 5f -> listOf(Color(0xFF6366F1), Color(0xFFF59E0B))
        else -> listOf(Color(0xFF6366F1), Color(0xFFF43F5E))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { scaleX = iconPulse; scaleY = iconPulse }
                        .clip(CircleShape)
                        .background(Color(0xFFF43F5E).copy(alpha = 0.18f))
                        .border(1.dp, Color(0xFFFB7185).copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TrackChanges,
                        contentDescription = "Target",
                        tint = Color(0xFFFB7185),
                        modifier = Modifier.size(13.dp)
                    )
                }

                Text(
                    text = "Target: $targetPercent%",
                    color = primaryText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = String.format(Locale.US, "%.1f%% Current", animatedCurrentValue),
                color = secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Animated Progress Track with Target Marker Tick
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        ) {
            val w = size.width
            val h = size.height
            val radius = CornerRadius(h / 2f, h / 2f)

            // Base Track
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = radius
            )

            // Active Fill
            val fillW = (w * animatedFillFraction).coerceAtLeast(if (animatedFillFraction > 0f) h else 0f)
            if (fillW > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(colors = activeBarGradient),
                    size = Size(fillW, h),
                    cornerRadius = radius
                )
            }

            // Target Threshold Marker Notch (e.g., at 80% position)
            val targetX = w * (targetPercent.coerceIn(0, 100) / 100f)
            drawLine(
                color = Color.White.copy(alpha = 0.80f),
                start = Offset(targetX, 0f),
                end = Offset(targetX, h),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
