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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PeriodSlot
import com.example.data.model.PeriodStatus
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun ClassSessionCard(
    slot: PeriodSlot,
    currentStatus: PeriodStatus,
    onStatusSelected: (PeriodStatus) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCascading: Boolean = false,
    isImmunityPulse: Boolean = false,
    onLockedClick: () -> Unit = {}
) {
    val glass = LocalGlassTheme.current

    // Roman numeral conversion
    val romanNumeral = when (slot.periodNumber) {
        1 -> "I"
        2 -> "II"
        3 -> if (slot.isTwoHourBlock) "III - IV" else "III"
        4 -> "IV"
        5 -> "V"
        6 -> if (slot.isTwoHourBlock) "VI - VII" else "VI"
        7 -> "VII"
        else -> "${slot.periodNumber}"
    }

    // Dynamic accent & glow colors matching college-attendance-redesign.html
    val accentStripeColor by animateColorAsState(
        targetValue = when {
            slot.subject.isAutoPresent -> Color(0xFF06B6D4)
            currentStatus == PeriodStatus.PRESENT -> Color(0xFF10B981)
            currentStatus == PeriodStatus.ABSENT -> Color(0xFFF43F5E)
            currentStatus == PeriodStatus.PENDING -> Color(0xFF94A3B8)
            currentStatus == PeriodStatus.NO_CLASS -> Color(0xFF64748B)
            else -> if (glass.isDark) Color(0xFF4F46E5) else Color(0xFF818CF8)
        },
        animationSpec = tween(350),
        label = "accentStripeColor"
    )

    val isDark = glass.isDark
    val surfaceBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF161B2E).copy(alpha = 0.75f),
                Color(0xFF0D1120).copy(alpha = 0.88f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFFF8FAFD).copy(alpha = 0.82f)
            )
        )
    }

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            slot.subject.isAutoPresent -> Color(0xFF06B6D4).copy(alpha = 0.45f)
            currentStatus == PeriodStatus.PRESENT -> Color(0xFF10B981).copy(alpha = 0.50f)
            currentStatus == PeriodStatus.ABSENT -> Color(0xFFF43F5E).copy(alpha = 0.50f)
            currentStatus == PeriodStatus.PENDING -> Color(0xFF94A3B8).copy(alpha = 0.35f)
            currentStatus == PeriodStatus.NO_CLASS -> Color(0xFF64748B).copy(alpha = 0.30f)
            else -> if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF6366F1).copy(alpha = 0.15f)
        },
        animationSpec = tween(350),
        label = "cardBorderColor"
    )

    val statusCardBgTint by animateColorAsState(
        targetValue = when {
            slot.subject.isAutoPresent -> Color(0xFF06B6D4).copy(alpha = if (glass.isDark) 0.08f else 0.04f)
            currentStatus == PeriodStatus.PRESENT -> Color(0xFF10B981).copy(alpha = if (glass.isDark) 0.12f else 0.06f)
            currentStatus == PeriodStatus.ABSENT -> Color(0xFFF43F5E).copy(alpha = if (glass.isDark) 0.12f else 0.06f)
            currentStatus == PeriodStatus.PENDING -> Color(0xFF94A3B8).copy(alpha = if (glass.isDark) 0.06f else 0.03f)
            currentStatus == PeriodStatus.NO_CLASS -> Color(0xFF64748B).copy(alpha = if (glass.isDark) 0.06f else 0.03f)
            else -> Color.Transparent
        },
        animationSpec = tween(350),
        label = "statusCardBgTint"
    )

    // Cascade animation reaction
    val cascadeScale by animateFloatAsState(
        targetValue = if (isCascading) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "cascadeScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cascadeScale)
            .shadow(
                elevation = if (currentStatus == PeriodStatus.PRESENT || currentStatus == PeriodStatus.ABSENT) 10.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = cardBorderColor,
                spotColor = cardBorderColor
            )
            .border(1.2.dp, cardBorderColor, RoundedCornerShape(20.dp))
            .testTag("period_card_${slot.periodNumber}"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceBrush)
                .background(statusCardBgTint)
        ) {
            // Left Accent Glow Stripe (4dp width)
            Box(
                modifier = Modifier
                    .width(4.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(accentStripeColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
            ) {
                // Top Row: Roman Numeral Badge + Full Subject Info + Tag Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Period Roman Badge Container
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        slot.subject.isAutoPresent -> Color(0xFF06B6D4).copy(alpha = 0.16f)
                                        slot.isTwoHourBlock -> Color(0xFF6366F1).copy(alpha = 0.22f)
                                        else -> glass.royalIndigo.copy(alpha = 0.16f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        slot.subject.isAutoPresent -> Color(0xFF06B6D4).copy(alpha = 0.45f)
                                        slot.isTwoHourBlock -> Color(0xFF818CF8).copy(alpha = 0.50f)
                                        else -> glass.royalIndigo.copy(alpha = 0.35f)
                                    },
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = romanNumeral,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = if (romanNumeral.length > 3) 11.sp else 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    slot.subject.isAutoPresent -> Color(0xFF06B6D4)
                                    slot.isTwoHourBlock -> if (glass.isDark) Color(0xFFC7D2FE) else Color(0xFF4F46E5)
                                    else -> if (glass.isDark) Color(0xFFA5B4FC) else glass.royalIndigo
                                }
                            )
                        }

                        // Full Official Subject Name & Metadata
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = slot.timeRange,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = glass.textMuted
                                )
                                if (slot.subject.isAutoPresent) {
                                    Text(
                                        text = "• Auto-Rule",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 10.sp,
                                        color = Color(0xFF06B6D4),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            // FULL OFFICIAL SUBJECT NAME (never truncated)
                            Text(
                                text = slot.subject.fullName,
                                fontFamily = OutfitFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = glass.textPrimary,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${slot.subject.code} • ${slot.subject.creditHours} Hr${if (slot.subject.creditHours > 1) "s" else ""} • ${slot.subject.category.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 11.sp,
                                color = glass.textSecondary
                            )
                        }
                    }

                    // Lab / Tutorial Badges
                    if (slot.isTwoHourBlock) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.20f))
                                .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "2 HRS • 2X CR",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (glass.isDark) Color(0xFFC7D2FE) else Color(0xFF4F46E5)
                            )
                        }
                    } else if (slot.isTutorial) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(glass.amberGold.copy(alpha = 0.18f))
                                .border(1.dp, glass.amberGold.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "TUTORIAL",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = glass.amberGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subtle Card Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(glass.cardBorder.copy(alpha = 0.5f))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Area: Auto-Present Immunity OR Expanding Marked/Undo Pill
                if (slot.subject.isAutoPresent) {
                    // Auto-Present Immunity Shield (Library / Seminar / Digital Library)
                    val immunityAlpha by animateFloatAsState(
                        targetValue = if (isImmunityPulse) 0.35f else 0.16f,
                        animationSpec = tween(250),
                        label = "immunityPulse"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF06B6D4).copy(alpha = immunityAlpha))
                            .border(
                                1.2.dp,
                                if (isImmunityPulse) Color(0xFF06B6D4) else Color(0xFF06B6D4).copy(alpha = 0.40f),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("auto_present_badge_${slot.periodNumber}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Auto-Present Protected",
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Auto-Present (+${slot.subject.creditHours} Hr${if (slot.subject.creditHours > 1) "s" else ""}) • Immunity Protected",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (glass.isDark) Color(0xFF67E8F9) else Color(0xFF0891B2)
                            )
                        }
                    }
                } else {
                    // Interactive Action Area: Exact Unmarked 2-button OR Expanding Marked Solid Pill + Undo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = currentStatus,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(250)) + expandHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = 0.65f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )) togetherWith (fadeOut(animationSpec = tween(180)) + shrinkHorizontally(
                                    animationSpec = tween(200)
                                ))
                            },
                            label = "actionAreaContent"
                        ) { status ->
                            when (status) {
                                PeriodStatus.PRESENT -> {
                                    // 1.3 MARKED PRESENT: Rich Solid Green Pill + ↺ Undo Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Expanded Solid Emerald Green Pill
                                        Box(
                                            modifier = Modifier
                                                .height(38.dp)
                                                .shadow(
                                                    elevation = 10.dp,
                                                    shape = RoundedCornerShape(999.dp),
                                                    spotColor = Color(0xFF10B981).copy(alpha = 0.45f)
                                                )
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF059669), Color(0xFF10B981))
                                                    )
                                                )
                                                .padding(horizontal = 14.dp)
                                                .testTag("present_marked_pill_${slot.periodNumber}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Text(
                                                    text = "Marked",
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        // Tactile Undo / Reset Button (↺)
                                        UndoCircularButton(
                                            tintColor = Color(0xFF10B981),
                                            isDarkMode = glass.isDark,
                                            onClick = {
                                                if (!enabled) onLockedClick() else onStatusSelected(PeriodStatus.PENDING)
                                            },
                                            testTag = "undo_button_${slot.periodNumber}"
                                        )
                                    }
                                }

                                PeriodStatus.ABSENT -> {
                                    // 1.3 MARKED ABSENT: Rich Solid Red Pill + ↺ Undo Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Expanded Solid Crimson Red Pill
                                        Box(
                                            modifier = Modifier
                                                .height(38.dp)
                                                .shadow(
                                                    elevation = 10.dp,
                                                    shape = RoundedCornerShape(999.dp),
                                                    spotColor = Color(0xFFF43F5E).copy(alpha = 0.45f)
                                                )
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFE11D48), Color(0xFFF43F5E))
                                                    )
                                                )
                                                .padding(horizontal = 14.dp)
                                                .testTag("absent_marked_pill_${slot.periodNumber}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Text(
                                                    text = "Marked",
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        // Tactile Undo / Reset Button (↺)
                                        UndoCircularButton(
                                            tintColor = Color(0xFFF43F5E),
                                            isDarkMode = glass.isDark,
                                            onClick = {
                                                if (!enabled) onLockedClick() else onStatusSelected(PeriodStatus.PENDING)
                                            },
                                            testTag = "undo_button_${slot.periodNumber}"
                                        )
                                    }
                                }

                                PeriodStatus.NO_CLASS -> {
                                    // 1.4 CANCELLED / NO CLASS: Neutral Slate Pill + ↺ Undo Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (glass.isDark) Color(0x3364748B) else Color(0x2294A3B8))
                                                .border(1.dp, Color(0xFF64748B).copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No Class",
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }

                                        UndoCircularButton(
                                            tintColor = Color(0xFF94A3B8),
                                            isDarkMode = glass.isDark,
                                            onClick = {
                                                if (!enabled) onLockedClick() else onStatusSelected(PeriodStatus.PENDING)
                                            },
                                            testTag = "undo_button_${slot.periodNumber}"
                                        )
                                    }
                                }

                                else -> {
                                    // 1.2 UNMARKED / PENDING STATE: Two 36dp x 36dp circular glass buttons side-by-side
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Soft Emerald Present Button [ ✓ ]
                                        UnmarkedCircularButton(
                                            icon = Icons.Default.Check,
                                            iconColor = Color(0xFF10B981),
                                            bgColor = if (glass.isDark) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFECFDF5),
                                            borderColor = Color(0xFF10B981).copy(alpha = 0.35f),
                                            contentDescription = "Mark Present",
                                            onClick = {
                                                if (!enabled) onLockedClick() else onStatusSelected(PeriodStatus.PRESENT)
                                            },
                                            testTag = "present_p${slot.periodNumber}"
                                        )

                                        // Soft Rose Absent Button [ ✕ ]
                                        UnmarkedCircularButton(
                                            icon = Icons.Default.Close,
                                            iconColor = Color(0xFFF43F5E),
                                            bgColor = if (glass.isDark) Color(0xFFF43F5E).copy(alpha = 0.14f) else Color(0xFFFFF1F2),
                                            borderColor = Color(0xFFF43F5E).copy(alpha = 0.35f),
                                            contentDescription = "Mark Absent",
                                            onClick = {
                                                if (!enabled) onLockedClick() else onStatusSelected(PeriodStatus.ABSENT)
                                            },
                                            testTag = "absent_p${slot.periodNumber}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 36dp x 36dp compact circular glass button used in the Unmarked state.
 */
@Composable
private fun UnmarkedCircularButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    bgColor: Color,
    borderColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btnScale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * Tactile Undo/Reset icon button (↺ / RotateCcw) with subtle rotation micro-interaction.
 */
@Composable
private fun UndoCircularButton(
    tintColor: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    var rotDegrees by remember { mutableFloatStateOf(0f) }
    val animatedRot by animateFloatAsState(
        targetValue = rotDegrees,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "undoRot"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tintColor.copy(alpha = if (isDarkMode) 0.22f else 0.15f))
            .border(1.dp, tintColor.copy(alpha = 0.40f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                rotDegrees -= 360f
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // RotateCcw SVG Icon drawn on Canvas for crisp 1-to-1 fidelity
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = w * 0.38f

            rotate(degrees = animatedRot, pivot = Offset(cx, cy)) {
                // Arc with opening at top-right
                val arcPath = Path().apply {
                    addArc(
                        oval = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                        startAngleDegrees = 50f,
                        sweepAngleDegrees = 270f
                    )
                }
                drawPath(
                    path = arcPath,
                    color = tintColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                // Arrowhead on the arc start
                val arrowHead = Path().apply {
                    val ax = cx + (r * kotlin.math.cos(Math.toRadians(50.0))).toFloat()
                    val ay = cy + (r * kotlin.math.sin(Math.toRadians(50.0))).toFloat()
                    moveTo(ax - 2.dp.toPx(), ay - 5.dp.toPx())
                    lineTo(ax + 3.dp.toPx(), ay + 1.dp.toPx())
                    lineTo(ax - 4.dp.toPx(), ay + 3.dp.toPx())
                }
                drawPath(path = arrowHead, color = tintColor)
            }
        }
    }
}
