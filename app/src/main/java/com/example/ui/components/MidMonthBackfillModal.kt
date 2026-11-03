package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

private fun prettyBackfillDate(iso: String): String =
    runCatching {
        java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))
    }.getOrDefault(iso)

/** First day of the current month through yesterday (or today on the 1st). Never a hardcoded month. */
internal fun defaultBackfillRange(): Pair<String, String> {
    val today = com.example.ui.viewmodel.AttendanceViewModel.liveToday()
    val first = today.withDayOfMonth(1)
    val yesterday = today.minusDays(1)
    return first.toString() to (if (yesterday.isBefore(first)) today else yesterday).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidMonthBackfillModal(
    onDismiss: () -> Unit,
    onConfirm: (fromDate: String, toDate: String, markAllPresent: Boolean) -> Unit
) {
    val glass = LocalGlassTheme.current
    val defaultRange = remember { defaultBackfillRange() }
    var fromDate by remember { mutableStateOf(defaultRange.first) }
    var toDate by remember { mutableStateOf(defaultRange.second) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (glass.isDark) Color(0xFF0F172A).copy(alpha = 0.96f) else com.example.ui.theme.LightPearl.copy(alpha = 0.97f),
                            if (glass.isDark) Color(0xFF1E1B4B).copy(alpha = 0.98f) else com.example.ui.theme.LightSurfaceAlt.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(1.5.dp, glass.royalIndigo.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "⚡ Mid-Month Backfill",
                            fontFamily = OutfitFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = glass.textPrimary
                        )
                        Text(
                            text = "Bulk mark past working days in one click",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 11.5.sp,
                            color = glass.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = glass.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Range Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glass.royalIndigo.copy(alpha = 0.12f))
                        .border(1.dp, glass.royalIndigo.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Backfill Range: ${prettyBackfillDate(fromDate)} – ${prettyBackfillDate(toDate)}",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (glass.isDark) Color(0xFFC7D2FE) else glass.royalIndigo
                        )
                        Text(
                            text = "Covers all Monday–Friday college working days (AKTU official holidays are automatically skipped & protected).",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 10.5.sp,
                            color = glass.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Mark All Present Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF14B8A6))
                                )
                            )
                            .clickable {
                                onConfirm(fromDate, toDate, true)
                            }
                            .testTag("confirm_backfill_present"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Mark All Present",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Mark All Absent Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFEF4444), Color(0xFFE11D48))
                                )
                            )
                            .clickable {
                                onConfirm(fromDate, toDate, false)
                            }
                            .testTag("confirm_backfill_absent"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕ Mark All Absent",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
