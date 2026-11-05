package com.example.ui.whatif

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.whatif.*
import com.example.ui.components.EntranceTracker
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import com.example.ui.theme.LocalGlassTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LONG_DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun longDate(iso: String): String = runCatching { LocalDate.parse(iso).format(LONG_DATE) }.getOrDefault(iso)

// ───────────────────────── intro ─────────────────────────

@Composable
fun IntroScreen(ui: WhatIfUiState, c: WhatIfController) {
    val t = LocalGlassTheme.current
    val tracker = rememberEntranceTracker()
    val b = ui.baseline
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.loadIn(0, tracker)) {
                WiText("What if I…?", size = 30.sp, weight = FontWeight.Bold, display = true)
                WiText(
                    "Plan future classes as Present or Absent and see how your attendance would change. Nothing here touches your real attendance.",
                    size = 14.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item {
            GlassCard(Modifier.loadIn(1, tracker)) {
                SectionTitle("Your actual attendance")
                if (b == null || !b.hasData) {
                    WiText("No attendance marked yet. Mark a few classes first, then come back to plan ahead.", color = t.textSecondary, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimatedPct(b.percentage)
                        WiText("${b.attended} / ${b.conducted} hours", size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        val partial = b.completeness == BaselineCompleteness.PARTIAL
                        WiPill(if (partial) "From available records" else "Records look complete", if (partial) t.amberGold else t.emeraldNeon)
                        val prof = c.context().profile
                        WiPill("Exam ${prof.examTarget}% · Stipend ${prof.stipendTarget}%", t.royalIndigo)
                    }
                    b.notes.forEach { WiText(it, size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
        item {
            Column(Modifier.loadIn(2, tracker), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("How far ahead?")
                ModeCard(ProjectionMode.PLAN_ONLY, ui.mode == ProjectionMode.PLAN_ONLY,
                    "Only the dates you pick are counted. Other upcoming classes are left out entirely.") { c.setMode(ProjectionMode.PLAN_ONLY) }
                ModeCard(ProjectionMode.MONTH_END, ui.mode == ProjectionMode.MONTH_END,
                    "Projects to the end of this month. Classes you don’t plan are assumed Present.") { c.setMode(ProjectionMode.MONTH_END) }
            }
        }
        item {
            Column(Modifier.loadIn(3, tracker), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WiPrimaryButton("Start planning", Modifier.fillMaxWidth(), enabled = b?.hasData == true) { c.go(WhatIfScreen.CALENDAR) }
                WiGhostButton(
                    "Saved scenarios (${ui.saved.size})", Modifier.fillMaxWidth()
                ) { c.go(WhatIfScreen.SAVED) }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: ProjectionMode, selected: Boolean, text: String, onClick: () -> Unit) {
    val t = LocalGlassTheme.current
    val border = wiColor(if (selected) t.royalIndigo else t.cardBorder)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(t.cardSurface).border(BorderStroke(if (selected) 2.dp else 1.dp, border), shape)
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WiText(mode.label, size = 16.sp, weight = FontWeight.Bold, display = true, modifier = Modifier.weight(1f))
            if (selected) WiPill("Selected", t.royalIndigo)
        }
        WiText(text, size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

// ───────────────────────── calendar ─────────────────────────

@Composable
fun CalendarScreen(ui: WhatIfUiState, c: WhatIfController) {
    val t = LocalGlassTheme.current
    val today = remember { c.context().today }
    val month = ui.visibleMonth
    val minMonth = YearMonth.from(today)
    val maxMonth = minMonth.plusMonths(5)
    val infos: Map<String, DayInfo> = remember(month, ui.plan.items.size, ui.baseline) {
        (1..month.lengthOfMonth()).associate { d -> val date = month.atDay(d); date.toString() to c.dayInfo(date) }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    val wholeDay = ui.planningStyle == WhatIfSnapshot.STYLE_WHOLE_DAY

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WiSegment(listOf("Whole day", "Choose classes"), if (wholeDay) 0 else 1) {
                c.setStyle(if (it == 0) WhatIfSnapshot.STYLE_WHOLE_DAY else WhatIfSnapshot.STYLE_SELECTED)
            }
            GlassCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NavArrow("‹", month > minMonth) { c.setVisibleMonth(month.minusMonths(1)) }
                    WiText(month.format(MONTH_TITLE), Modifier.weight(1f), size = 17.sp, weight = FontWeight.Bold, display = true, align = TextAlign.Center)
                    NavArrow("›", month < maxMonth) { c.setVisibleMonth(month.plusMonths(1)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        WiText(it, Modifier.weight(1f), size = 11.sp, weight = FontWeight.Bold, color = t.textMuted, align = TextAlign.Center)
                    }
                }
                val lead = month.atDay(1).dayOfWeek.value - 1
                val cells = lead + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        for (col in 0 until 7) {
                            val day = r * 7 + col - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (day in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(day)
                                    val iso = date.toString()
                                    val info = infos[iso]
                                    DayCell(
                                        day = day, info = info, isToday = date == today, isSelected = selected == iso,
                                        planned = ui.plan.forDate(iso)
                                    ) {
                                        if (info?.availability == DayAvailability.PLANNABLE) {
                                            if (wholeDay) {
                                                selected = if (selected == iso) null else iso
                                            } else {
                                                c.openClasses(iso)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(t.emeraldNeon, "Present"); LegendDot(t.crimsonRose, "Absent"); LegendDot(t.amberGold, "Mixed")
                }
            }
            val sel = selected
            if (sel != null && wholeDay) {
                val info = infos[sel]
                GlassCard(accent = t.royalIndigo) {
                    WiText(longDate(sel), size = 16.sp, weight = FontWeight.Bold, display = true)
                    WiText("${info?.manual?.size ?: 0} manual classes", size = 12.sp, color = t.textSecondary)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecisionButton("Present", t.emeraldNeon, Modifier.weight(1f)) { c.applyWholeDay(sel, PlanDecision.PRESENT) }
                        DecisionButton("Absent", t.crimsonRose, Modifier.weight(1f)) { c.applyWholeDay(sel, PlanDecision.ABSENT) }
                        DecisionButton("Clear", t.textMuted, Modifier.weight(1f)) { c.applyWholeDay(sel, null) }
                    }
                    WiText(
                        "Customise single classes", size = 12.sp, weight = FontWeight.SemiBold, color = t.royalIndigo,
                        modifier = Modifier.padding(top = 10.dp).clickable { c.openClasses(sel) }
                    )
                }
            }
        }
        BottomBar(ui, c)
    }
}

@Composable
private fun NavArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalGlassTheme.current
    Box(
        Modifier.size(36.dp).clip(CircleShape).alpha(if (enabled) 1f else 0.25f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { WiText(glyph, size = 22.sp, weight = FontWeight.Bold, color = t.textPrimary) }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        WiText(label, size = 11.sp, color = LocalGlassTheme.current.textSecondary)
    }
}

@Composable
private fun DayCell(day: Int, info: DayInfo?, isToday: Boolean, isSelected: Boolean, planned: List<PlannedClass>, onClick: () -> Unit) {
    val t = LocalGlassTheme.current
    val plannable = info?.availability == DayAvailability.PLANNABLE
    val present = planned.count { it.decision == PlanDecision.PRESENT }
    val absent = planned.count { it.decision == PlanDecision.ABSENT }
    val fill = when {
        present > 0 && absent == 0 -> t.emeraldNeon
        absent > 0 && present == 0 -> t.crimsonRose
        present > 0 && absent > 0 -> t.amberGold
        else -> Color.Transparent
    }
    val bg = wiColor(if (fill == Color.Transparent) Color.Transparent else fill.copy(alpha = 0.30f))
    val ring = wiColor(
        when {
            isSelected -> t.royalIndigo
            isToday -> t.electricTeal
            plannable -> t.cardBorder
            else -> Color.Transparent
        }
    )
    Box(
        Modifier.fillMaxSize(0.88f).clip(CircleShape).background(bg).border(if (isSelected) 2.dp else 1.dp, ring, CircleShape)
            .alpha(if (plannable || isToday) 1f else 0.3f).clickable(enabled = plannable, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        WiText("$day", size = 13.sp, weight = if (plannable) FontWeight.SemiBold else FontWeight.Normal, color = t.textPrimary)
    }
}

@Composable
private fun DecisionButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(42.dp).clip(shape).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { WiText(text, size = 13.sp, weight = FontWeight.Bold, color = color) }
}

@Composable
private fun BottomBar(ui: WhatIfUiState, c: WhatIfController) {
    val t = LocalGlassTheme.current
    Column(
        Modifier.fillMaxWidth().background(t.cardSurface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val p = ui.plan
        WiText(
            if (p.isEmpty) "No dates planned yet" else "${p.dates.size} date(s) · ${p.presentCount} present · ${p.absentCount} absent",
            size = 13.sp, color = t.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WiGhostButton("Undo", enabled = ui.undoStack.isNotEmpty()) { c.undo() }
            WiGhostButton("Clear all", enabled = !p.isEmpty, color = t.crimsonRose) { c.clearAll() }
            WiPrimaryButton(
                "See results",
                Modifier.weight(1f).height(44.dp),
                enabled = !p.isEmpty || ui.mode == ProjectionMode.MONTH_END
            ) { c.go(WhatIfScreen.RESULTS) }
        }
    }
}

// ───────────────────────── per-class picker ─────────────────────────

@Composable
fun ClassesScreen(ui: WhatIfUiState, c: WhatIfController) {
    val t = LocalGlassTheme.current
    val date = ui.focusDate ?: return
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
    val info = remember(date, ui.baseline) { c.dayInfo(parsedDate) }
    val tracker = rememberEntranceTracker()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecisionButton("All present", t.emeraldNeon, Modifier.weight(1f)) { c.applyWholeDay(date, PlanDecision.PRESENT) }
                DecisionButton("All absent", t.crimsonRose, Modifier.weight(1f)) { c.applyWholeDay(date, PlanDecision.ABSENT) }
                DecisionButton("Clear", t.textMuted, Modifier.weight(1f)) { c.applyWholeDay(date, null) }
            }
        }
        if (info.availability != DayAvailability.PLANNABLE) {
            item { GlassCard { WiText("Nothing to plan on this date.", color = t.textSecondary) } }
        }
        items(info.manual.size) { i ->
            val slot = info.manual[i]
            val decision = ui.plan.decisionFor(date, slot.periodNumber)
            GlassCard(Modifier.loadIn(i, tracker)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        WiText(slot.subject.shortName, size = 16.sp, weight = FontWeight.Bold, display = true)
                        WiText("${slot.periodLabel} · ${slot.timeRange}", size = 12.sp, color = t.textSecondary)
                    }
                    WiPill("${slot.subject.creditHours}h" + if (slot.subject.fullName.contains("Lab", true)) " · Lab" else "", t.electricViolet)
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChoice("Present", t.emeraldNeon, decision == PlanDecision.PRESENT, Modifier.weight(1f)) {
                        c.setClass(date, slot, if (decision == PlanDecision.PRESENT) null else PlanDecision.PRESENT)
                    }
                    ToggleChoice("Absent", t.crimsonRose, decision == PlanDecision.ABSENT, Modifier.weight(1f)) {
                        c.setClass(date, slot, if (decision == PlanDecision.ABSENT) null else PlanDecision.ABSENT)
                    }
                }
            }
        }
        if (info.auto.isNotEmpty()) {
            item {
                GlassCard {
                    SectionTitle("Credited automatically")
                    info.auto.forEach {
                        WiText("${it.subject.shortName} · ${it.timeRange}", size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (info.cancelled.isNotEmpty()) {
            item {
                GlassCard {
                    SectionTitle("Marked cancelled")
                    info.cancelled.forEach {
                        WiText("${it.subject.shortName} · excluded", size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item {
            WiText(
                if (ui.mode == ProjectionMode.MONTH_END) "Classes left undecided are assumed Present in Month End."
                else "Classes left undecided are not counted in Plan Only.",
                size = 12.sp, color = t.textMuted
            )
        }
    }
}

@Composable
private fun ToggleChoice(text: String, color: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalGlassTheme.current
    val shape = RoundedCornerShape(12.dp)
    val bg = wiColor(if (on) color else Color.Transparent)
    Box(
        modifier.height(44.dp).clip(shape).background(bg).border(1.dp, if (on) color else t.cardBorder, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { WiText(text, size = 13.sp, weight = FontWeight.Bold, color = if (on) Color.White else t.textSecondary) }
}
