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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

enum class BulkActiveState {
    NONE,
    ALL_PRESENT,
    ALL_ABSENT
}

@Composable
fun BulkAttendanceCard(
    isHoliday: Boolean,
    holidayName: String,
    totalSlotsCount: Int,
    recordedSlotsCount: Int,
    onMarkAllPresent: () -> Unit,
    onMarkAllAbsent: () -> Unit,
    onMarkAllCancelled: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val glass = LocalGlassTheme.current

    val isAllMarked = totalSlotsCount > 0 && recordedSlotsCount >= totalSlotsCount
    var localBulkState by remember(recordedSlotsCount) {
        mutableStateOf(
            if (isAllMarked) BulkActiveState.ALL_PRESENT else BulkActiveState.NONE
        )
    }

    // Infinite transition for shimmer effect
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerTransition")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val progressFraction = if (totalSlotsCount > 0) {
        (recordedSlotsCount.toFloat() / totalSlotsCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    val progressPercent = (progressFraction * 100f).toInt()

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
    val cardBorderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF6366F1).copy(alpha = 0.15f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = glass.royalIndigo.copy(alpha = 0.15f))
            .border(1.2.dp, cardBorderColor, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceBrush)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BULK ATTENDANCE MARKER",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = glass.textSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isAllMarked) "All Marked" else "Quick Cascade",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAllMarked) Color(0xFF10B981) else glass.textMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Dual Bulk Buttons matching Section 2 of Prompt
            when (localBulkState) {
                BulkActiveState.ALL_PRESENT -> {
                    // Solid Green Active State: "✓ All Marked Present" + "↺ Undo All"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFF10B981).copy(alpha = 0.45f))
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF059669), Color(0xFF10B981))
                                    )
                                )
                                .padding(horizontal = 14.dp)
                                .testTag("all_marked_present_active"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "✓ All Marked Present",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Undo All Button
                        BulkUndoButton(
                            tintColor = Color(0xFF10B981),
                            isDarkMode = glass.isDark,
                            label = "Undo All",
                            onClick = {
                                localBulkState = BulkActiveState.NONE
                                onMarkAllCancelled()
                            },
                            testTag = "undo_all_bulk_button"
                        )
                    }
                }

                BulkActiveState.ALL_ABSENT -> {
                    // Solid Red Active State: "✕ All Marked Absent" + "↺ Undo All"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFFF43F5E).copy(alpha = 0.45f))
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFE11D48), Color(0xFFF43F5E))
                                    )
                                )
                                .padding(horizontal = 14.dp)
                                .testTag("all_marked_absent_active"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "✕ All Marked Absent",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Undo All Button
                        BulkUndoButton(
                            tintColor = Color(0xFFF43F5E),
                            isDarkMode = glass.isDark,
                            label = "Undo All",
                            onClick = {
                                localBulkState = BulkActiveState.NONE
                                onMarkAllCancelled()
                            },
                            testTag = "undo_all_bulk_button"
                        )
                    }
                }

                BulkActiveState.NONE -> {
                    // Soft Translucent Glass Appearance Before Marking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Mark All Present (Soft Emerald Glass)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (glass.isDark) Color(0xFF10B981).copy(alpha = 0.16f) else Color(0xFFECFDF5)
                                )
                                .border(
                                    1.2.dp,
                                    Color(0xFF10B981).copy(alpha = 0.38f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = enabled && !isHoliday) {
                                    localBulkState = BulkActiveState.ALL_PRESENT
                                    onMarkAllPresent()
                                }
                                .testTag("mark_all_present_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Mark All Present",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (glass.isDark) Color(0xFF34D399) else Color(0xFF059669)
                                )
                            }
                        }

                        // Mark All Absent (Soft Rose Glass)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (glass.isDark) Color(0xFFF43F5E).copy(alpha = 0.16f) else Color(0xFFFFF1F2)
                                )
                                .border(
                                    1.2.dp,
                                    Color(0xFFF43F5E).copy(alpha = 0.38f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = enabled && !isHoliday) {
                                    localBulkState = BulkActiveState.ALL_ABSENT
                                    onMarkAllAbsent()
                                }
                                .testTag("mark_all_absent_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFF43F5E),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Mark All Absent",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (glass.isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Progress Bar Container with Luminous Wave
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(glass.cardBorder.copy(alpha = 0.6f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Completion Track",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textPrimary
                )
                Text(
                    text = "$recordedSlotsCount of $totalSlotsCount Marked ($progressPercent%)",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progressPercent == 100) Color(0xFF10B981) else glass.royalIndigo
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Custom Glassmorphic Progress Bar Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(glass.royalIndigo.copy(alpha = 0.12f))
                    .border(0.5.dp, glass.cardBorder, RoundedCornerShape(9999.dp))
            ) {
                // Filled progress portion
                if (animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFF818CF8),
                                        if (progressPercent == 100) Color(0xFF10B981) else Color(0xFF06B6D4)
                                    )
                                )
                            )
                    )
                }
            }

            if (isHoliday) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🏖️ Holiday: $holidayName • No classes required today",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF06B6D4)
                )
            }
        }
    }
}
}

@Composable
private fun BulkUndoButton(
    tintColor: Color,
    isDarkMode: Boolean,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    var rotDegrees by remember { mutableFloatStateOf(0f) }
    val animatedRot by animateFloatAsState(
        targetValue = rotDegrees,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "bulkUndoRot"
    )

    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tintColor.copy(alpha = if (isDarkMode) 0.20f else 0.14f))
            .border(1.2.dp, tintColor.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                rotDegrees -= 360f
                onClick()
            }
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width * 0.38f

                rotate(degrees = animatedRot, pivot = Offset(cx, cy)) {
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

            Text(
                text = label,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tintColor
            )
        }
    }
}
