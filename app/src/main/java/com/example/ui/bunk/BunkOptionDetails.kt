package com.example.ui.bunk

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.bunk.BunkDayEntry
import com.example.data.bunk.BunkDayKind
import com.example.data.bunk.BunkOption
import com.example.data.bunk.BunkOverview
import com.example.data.bunk.SafetyTier
import com.example.data.bunk.SubjectImpact
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily

@Composable
fun BunkOptionDetails(option: BunkOption, overview: BunkOverview, onBack: () -> Unit) {
    val glass = LocalGlassTheme.current
    val tracker = rememberEntranceTracker()
    val accent = rankAccent(glass, option.rank)
    val gradient = rankGradient(glass, option.rank)
    val projColor = if (option.tier == SafetyTier.COMFORTABLE) glass.emeraldNeon else glass.amberGold

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("bunk_details"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- header --------------------------------------------------------------------
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, CircleShape)
                        .pressScale(onBack)
                        .testTag("bunk_details_back"),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = glass.textPrimary) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(rankTitle(option.rank).uppercase(), fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, color = accent, fontWeight = FontWeight.Bold)
                    Text(rangeLabel(option.start, option.end), fontFamily = OutfitFontFamily, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                }
            }
        }

        // ---- hero summary ----------------------------------------------------------------
        item {
            Box(Modifier.loadIn(0, tracker)) {
                BunkGlassCard(accent = accent, accent2 = gradient[1], shine = option.rank == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LeaveBadge(option.calendarDays, gradient, size = 88.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Leave summary", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = glass.textPrimary)
                                Text(
                                    "${fullWeekday(option.start)}, ${option.start.dayOfMonth} ${shortMonth(option.start)} → " +
                                        "${fullWeekday(option.end)}, ${option.end.dayOfMonth} ${shortMonth(option.end)}",
                                    fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TierChip(option.tier)
                                    if (option.isProvisional) BunkChip("Provisional", glass.amberGold)
                                }
                            }
                        }
                        DateStrip(option)
                        StripLegend()
                        FreeVsMissedBar(option)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Days off", option.calendarDays.toString(), accent, Modifier.weight(1f))
                            StatTile("Working days missed", option.workingDaysMissed.toString(), glass.crimsonRose, Modifier.weight(1f))
                            StatTile("Classes missed", option.classesMissed.toString(), glass.crimsonRose, Modifier.weight(1f), sub = "${option.weightedMissed} weighted")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Weekend days", option.weekendDays.toString(), glass.royalIndigo, Modifier.weight(1f))
                            StatTile("Official holidays", option.holidayDays.toString(), glass.amberGold, Modifier.weight(1f))
                            StatTile("No-class days", option.noClassDays.toString(), glass.electricTeal, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ---- overall impact --------------------------------------------------------------
        item {
            Box(Modifier.loadIn(1, tracker)) {
                BunkGlassCard(accent = projColor, accent2 = glass.royalIndigo) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Overall attendance impact")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AttendanceRing(
                                percent = option.projectedPercent, target = option.requiredTarget, color = projColor,
                                diameter = 112.dp, caption = "projected"
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ImpactLine("Current", option.currentPercent?.let { "${pct(it)}%" } ?: "—", glass.textPrimary)
                                ImpactLine("Projected", "${pct(option.projectedPercent)}%", projColor)
                                ImpactLine("Required target", "${option.requiredTarget}%", glass.textPrimary)
                                ImpactLine("Safety margin", "${signed(option.marginPoints)} pts", projColor)
                            }
                        }
                        Text(
                            "Exam target ${overview.examTarget}% · Stipend target ${overview.stipendTarget}% · safety buffer ${overview.safetyBuffer} pts",
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textMuted
                        )
                    }
                }
            }
        }

        // ---- daily breakdown (timeline) --------------------------------------------------
        item {
            Box(Modifier.loadIn(2, tracker)) {
                BunkGlassCard {
                    Column {
                        SectionTitle("Daily breakdown")
                        Spacer(Modifier.size(10.dp))
                        option.days.forEachIndexed { i, e ->
                            TimelineDay(e, isFirst = i == 0, isLast = i == option.days.lastIndex)
                        }
                    }
                }
            }
        }

        // ---- subject impact --------------------------------------------------------------
        item {
            Box(Modifier.loadIn(3, tracker)) {
                BunkGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle("Subject-wise impact")
                        if (option.subjects.isEmpty()) {
                            Text("No subject is affected.", fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textMuted)
                        }
                        option.subjects.forEachIndexed { i, s -> SubjectCard(s, option.requiredTarget, i) }
                    }
                }
            }
        }

        // ---- explanation -----------------------------------------------------------------
        item {
            Box(Modifier.loadIn(4, tracker)) {
                BunkGlassCard(accent = accent, accent2 = gradient[1]) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(gradient)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                            Spacer(Modifier.width(10.dp))
                            SectionTitle("Why this option")
                        }
                        Text(
                            option.explanation,
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp, color = glass.textSecondary,
                            modifier = Modifier.testTag("bunk_explanation")
                        )
                        Text(
                            "Month-end projection for ${overview.monthLabel}, not an all-time forecast. Future attendance is not guaranteed.",
                            fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val glass = LocalGlassTheme.current
    Text(text, fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = glass.textPrimary)
}

@Composable
private fun ImpactLine(label: String, value: String, color: Color) {
    val glass = LocalGlassTheme.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, color = glass.textMuted)
        Text(value, fontFamily = OutfitFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** One day in the vertical timeline. Bunk days start expanded and can be collapsed. */
@Composable
private fun TimelineDay(e: BunkDayEntry, isFirst: Boolean, isLast: Boolean) {
    val glass = LocalGlassTheme.current
    val color = dayKindColor(glass, e.kind)
    val canExpand = e.kind == BunkDayKind.BUNK
    var expanded by remember { mutableStateOf(canExpand) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(260, easing = FastOutSlowInEasing), label = "dayChevron")
    val line = glass.cardBorder
    val reveal = rememberReveal(0L, 500)

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = reveal }
            .drawBehind {
                val x = 14.dp.toPx()
                val y0 = if (isFirst) 20.dp.toPx() else 0f
                val y1 = if (isLast) 20.dp.toPx() else size.height
                drawLine(line, Offset(x, y0), Offset(x, y1), strokeWidth = 2.dp.toPx())
            }
    ) {
        Box(Modifier.width(28.dp).padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.28f))
                    .border(2.dp, color, CircleShape)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.08f))
                .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .clickable(enabled = canExpand) { expanded = !expanded }
                .animateContentSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${shortWeekday(e.date)}, ${e.date.dayOfMonth} ${shortMonth(e.date)}",
                        fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = glass.textPrimary
                    )
                    if (e.kind == BunkDayKind.HOLIDAY && e.label != null) {
                        Text(e.label, fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, color = glass.textSecondary)
                    }
                }
                BunkChip(dayKindLabel(e.kind), color)
                if (canExpand) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = glass.textMuted,
                        modifier = Modifier.padding(start = 4.dp).size(20.dp).graphicsLayer { rotationZ = chevron }
                    )
                }
            }
            if (e.kind == BunkDayKind.BUNK) {
                Text(
                    "${e.classesMissed} classes missed · ${e.subjectsAffected.joinToString()}",
                    fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = glass.crimsonRose
                )
                if (expanded) {
                    e.sessions.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (s.isMissed) glass.crimsonRose else glass.emeraldNeon)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${s.periodLabel} · ${s.subject.shortName}" + when {
                                    s.isAutoCredited -> " · auto-credited, unchanged"
                                    else -> " · ${s.weight} credit${if (s.weight > 1) "s" else ""} missed"
                                },
                                fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, color = glass.textMuted
                            )
                        }
                    }
                }
            } else if (e.kind == BunkDayKind.NO_CLASS_DAY) {
                Text("No manually attended classes scheduled", fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, color = glass.textMuted)
            } else {
                Text("Free day · nothing to miss", fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, color = glass.textMuted)
            }
        }
    }
}

@Composable
private fun SubjectCard(s: SubjectImpact, target: Int, index: Int) {
    val glass = LocalGlassTheme.current
    val c = statusColor(glass, s.status)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.copy(alpha = 0.07f))
            .border(1.dp, c.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.subject.shortName, fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = glass.textPrimary)
                Text(s.subject.code, fontFamily = PlusJakartaSansFontFamily, fontSize = 10.5.sp, color = glass.textMuted)
            }
            BunkChip(statusLabel(s.status), c)
        }
        if (s.currentPercent != null) {
            Text(
                "Now ${s.currentAttended}/${s.currentConducted} · ${pct(s.currentPercent)}%",
                fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, color = glass.textSecondary
            )
            AttendanceBar(s.currentPercent, target, glass.textMuted)
        }
        Text(
            "Projected ${s.projectedAttended}/${s.projectedConducted} · ${pct(s.projectedPercent)}%",
            fontFamily = PlusJakartaSansFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = c
        )
        AttendanceBar(s.projectedPercent, target, c)
        Text(
            "${s.missedWeight} weighted session${if (s.missedWeight > 1) "s" else ""} missed · target $target%",
            fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp, color = glass.textMuted
        )
    }
}
