package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun NoClassesTodayCard(
    date: LocalDate,
    holidayName: String?, // Non-null if official AKTU Holiday (e.g., "Diwali / Deepawali")
    isDark: Boolean,
    onJumpToToday: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHoliday = !holidayName.isNullOrBlank()
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    // Theme-harmonized accent: Warm Amber-Gold for Festival/Holiday, Serene Cyan-Indigo for Weekend
    val primaryAccent = if (isHoliday) {
        if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    } else {
        if (isDark) Color(0xFF38BDF8) else Color(0xFF4F46E5)
    }

    val secondaryAccent = if (isHoliday) {
        Color(0xFFF59E0B)
    } else {
        Color(0xFF818CF8)
    }

    // Infinite floating & breathing animations
    val infinite = rememberInfiniteTransition(label = "noClassAnim")
    val floatOffsetY by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val haloScale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )
    val sheenSweep by infinite.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheenSweep"
    )

    // Nocturne Academic (Dark) & Pastel Alabaster (Light) Glass Tokens
    val surfaceBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF161B2E).copy(alpha = 0.82f),
                Color(0xFF0D1120).copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                com.example.ui.theme.LightCardSurface,
                com.example.ui.theme.LightPearl.copy(alpha = 0.86f)
            )
        )
    }

    val borderColor = if (isDark) {
        primaryAccent.copy(alpha = 0.32f)
    } else {
        primaryAccent.copy(alpha = 0.28f)
    }

    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val pillBg = if (isDark) Color(0xFF1E2538).copy(alpha = 0.70f) else com.example.ui.theme.LightSurfaceAlt
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(surfaceBrush)
            .border(1.5.dp, borderColor, RoundedCornerShape(26.dp))
            .drawWithContent {
                // Soft ambient radial glow behind the center icon
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = if (isDark) 0.20f else 0.14f),
                            secondaryAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.28f),
                        radius = 135.dp.toPx() * haloScale
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = 135.dp.toPx() * haloScale
                )

                drawContent()

                // Subtle diagonal glass sheen sweep
                val sx = size.width * sheenSweep
                val sw = size.width * 0.38f
                withTransform({
                    rotate(degrees = -18f, pivot = Offset(sx + sw / 2f, size.height / 2f))
                }) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.07f else 0.28f),
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
            .padding(horizontal = 22.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Category Top Pill ("OFFICIAL AKTU HOLIDAY" or "WEEKEND BREAK")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(primaryAccent.copy(alpha = 0.14f))
                    .border(1.dp, primaryAccent.copy(alpha = 0.40f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = if (isHoliday) Icons.Rounded.AutoAwesome else Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isHoliday) "OFFICIAL INSTITUTE HOLIDAY" else "${date.dayOfWeek.name} • WEEKEND BREAK",
                    color = primaryAccent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 2. Floating Glass Emblem Orb
            Box(
                modifier = Modifier
                    .offset(y = floatOffsetY.dp)
                    .size(78.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.26f),
                                secondaryAccent.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(1.5.dp, primaryAccent.copy(alpha = 0.50f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isHoliday) Icons.Rounded.Celebration else Icons.Rounded.SelfImprovement,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 3. Main Headline ("No Classes Today") & Specific Holiday / Day Subheader
            Text(
                text = "No Classes Today",
                color = titleColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center
            )

            if (isHoliday) {
                Text(
                    text = holidayName,
                    color = primaryAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = if (isHoliday) {
                    "Institute is closed for $holidayName on ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}. Enjoy your holiday!"
                } else {
                    "No BHTT 5th Sem lectures or practical labs are scheduled for ${date.format(DateTimeFormatter.ofPattern("EEEE, dd MMM"))}. Recharge and relax!"
                },
                color = subtitleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Reassurance Info Strip (75% Criteria Protected)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(pillBg)
                    .border(1.dp, pillBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Non-instructional day • Zero impact on 75% criteria",
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
