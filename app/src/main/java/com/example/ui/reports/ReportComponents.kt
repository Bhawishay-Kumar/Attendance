package com.example.ui.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SubjectCategory
import com.example.data.model.TierStatus
import com.example.data.reports.ArchiveEntry
import com.example.data.reports.ReportAnalytics
import com.example.data.reports.ReportSessionStatus
import com.example.data.reports.ReportTimeframe
import com.example.data.reports.SessionEntry
import com.example.data.reports.SubjectReport
import com.example.ui.components.ScenicSkyToggle

enum class ReportSection(val label: String) {
    OVERVIEW("Overview"), SUBJECTS("Subjects"), TRENDS("Trends"), INSIGHTS("Insights"), SMART("Smart & PDF")
}

enum class SubjectSort(val label: String) {
    HIGHEST("Highest"), LOWEST("Lowest"), RECOVERY("Most recovery"), ALPHA("A–Z")
}

// ---------------------------------------------------------------- header / ribbon / selectors


/**
 * Compact archive ribbon built only from months with real marked attendance (plus the current
 * month). The container has its own glass/aurora background with a slow shine; the selected chip
 * has a moving gradient, glow, spring scale and its own faster shine.
 * [dimmed] is used in weekly mode (the chip of the week's month is lit but calmer).
 */
@Composable
fun MonthArchiveRibbon(
    p: RptPalette,
    entries: List<ArchiveEntry>,
    selectedMonthId: String?,
    dimmed: Boolean,
    reduced: Boolean,
    onSelect: (String) -> Unit
) {
    val state = rememberLazyListState()
    val selIdx = entries.indexOfFirst { it.monthId == selectedMonthId }
    LaunchedEffect(entries.size, selIdx) { if (selIdx >= 0) state.animateScrollToItem(selIdx) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(p.indigo.copy(alpha = if (p.isDark) 0.22f else 0.12f), p.violet.copy(alpha = if (p.isDark) 0.14f else 0.08f), p.cyan.copy(alpha = if (p.isDark) 0.10f else 0.06f))
                )
            )
            .border(BorderStroke(1.dp, p.border), shape)
            .shineSweep(!reduced, 6400, 0.09f)
    ) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.monthId }) { e ->
                MonthChip(p, e, selected = e.monthId == selectedMonthId, dimmed = dimmed, reduced = reduced, onClick = { onSelect(e.monthId) })
            }
        }
    }
}

@Composable
private fun MonthChip(p: RptPalette, e: ArchiveEntry, selected: Boolean, dimmed: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val lit by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "chipLit")
    val chipScale by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "chipScale")
    val shape = RoundedCornerShape(16.dp)
    val ym = remember(e.monthId) { runCatching { java.time.YearMonth.parse(e.monthId) }.getOrNull() }
    val name = ym?.month?.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) ?: e.monthId
    val yy = ym?.year?.toString()?.takeLast(2) ?: ""
    val onColor = if (selected) Color.White else p.text
    Box(
        Modifier
            .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
            .drawBehind {
                if (lit > 0.01f) {
                    drawCircle(
                        Brush.radialGradient(listOf(p.indigo.copy(alpha = 0.40f * lit * (if (dimmed) 0.6f else 1f)), Color.Transparent)),
                        radius = size.maxDimension * 0.75f
                    )
                }
            }
            .clip(shape)
            .background(p.card)
            .background(
                Brush.linearGradient(listOf(p.indigo, p.violet, p.cyan)),
                alpha = lit * (if (dimmed) 0.55f else 1f)
            )
            .border(BorderStroke(1.dp, if (selected) Color.White.copy(alpha = 0.35f) else p.border), shape)
            .shineSweep(selected && !dimmed && !reduced, 2600, 0.38f)
            .pressClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                RText("$name \u2019$yy", 11f, if (selected) Color.White.copy(alpha = 0.85f) else p.textSec, weight = FontWeight.SemiBold, maxLines = 1)
                RText(e.percentage?.let { "${fmt1(it)}%" } ?: "\u2014", 14f, onColor, weight = FontWeight.Bold, display = true, tnum = true, maxLines = 1)
            }
            if (e.isCurrentMonth) TrophyGlyph(if (selected) Color.White else p.amber, 14.dp)
            else LockGlyph(if (selected) Color.White.copy(alpha = 0.85f) else p.textMuted, 12.dp)
        }
    }
}

/** Monthly / Weekly switch with a sliding gradient thumb. */
@Composable
fun TimeframeSegmented(p: RptPalette, current: ReportTimeframe, onChange: (ReportTimeframe) -> Unit) {
    val weekly = current == ReportTimeframe.WEEKLY
    val pos by animateFloatAsState(if (weekly) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "segPos")
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(BorderStroke(1.dp, p.border), shape)
            .padding(3.dp)
    ) {
        val half = maxWidth / 2
        Box(
            Modifier
                .offset(x = half * pos)
                .width(half)
                .height(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.horizontalGradient(listOf(p.indigo, p.violet)))
                .shineSweep(true, 3200, 0.25f)
        )
        Row(Modifier.fillMaxWidth().height(38.dp)) {
            listOf(ReportTimeframe.MONTHLY to "Monthly Report", ReportTimeframe.WEEKLY to "Weekly Report").forEach { (tf, label) ->
                val selected = tf == current
                val tc by animateColorAsState(if (selected) Color.White else p.textSec, tween(200), label = "segText")
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressClickable { onChange(tf) },
                    contentAlignment = Alignment.Center
                ) { RText(label, 12.5f, tc, weight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
    }
}

/** Scrollable section tabs that bring the selected tab into view and underline it with a spring. */
@Composable
fun SectionTabs(
    p: RptPalette,
    current: ReportSection,
    subjectCount: Int,
    insightsNeedAttention: Boolean,
    onSelect: (ReportSection) -> Unit
) {
    val state = rememberLazyListState()
    val sections = remember { ReportSection.values().toList() }
    LaunchedEffect(current) { state.animateScrollToItem((sections.indexOf(current) - 1).coerceAtLeast(0)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(sections, key = { it.name }) { s ->
            val selected = s == current
            val color by animateColorAsState(if (selected) p.indigo else p.textSec, tween(200), label = "tab")
            val underline by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "ul")
            Column(Modifier.pressClickable { onSelect(s) }, horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText(s.label, 14f, color, weight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    when {
                        s == ReportSection.SUBJECTS && subjectCount > 0 -> {
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.clip(RoundedCornerShape(50)).background(p.trackBg).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                RText("$subjectCount", 10f, p.textSec, weight = FontWeight.Bold, tnum = true)
                            }
                        }
                        s == ReportSection.INSIGHTS && insightsNeedAttention -> {
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(p.rose))
                        }
                        s == ReportSection.SMART -> {
                            Spacer(Modifier.width(4.dp)); RText("\u2726", 11f, p.amber)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .fillMaxWidth(underline.coerceIn(0.001f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(p.indigo, p.cyan)))
                )
            }
        }
    }
}

// ---------------------------------------------------------------- overview widgets

fun tierColor(t: TierStatus, p: RptPalette): Color = when (t) {
    TierStatus.COMPLETELY_SAFE -> p.emerald
    TierStatus.SAFE_ZONE -> p.amber
    TierStatus.DANGER_ZONE -> p.rose
    TierStatus.NO_DATA -> p.textMuted
}

@Composable
fun HeroCard(
    p: RptPalette,
    a: ReportAnalytics,
    reducedMotion: Boolean,
    onOpenMonthSummary: (() -> Unit)?
) {
    val animatedPct by animateFloatAsState(
        a.percentage.toFloat(), tween(if (reducedMotion) 0 else 900), label = "heroPct"
    )
    val tint = tierColor(a.tierStatus, p)
    GlassCard(p, Modifier.fillMaxWidth(), corner = 28.dp, accent = tint) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    fraction = (a.percentage / 100.0).toFloat(),
                    examTarget = a.examTarget, stipendTarget = a.stipendTarget,
                    p = p, reduced = reducedMotion, ringSize = 132.dp,
                    showTargets = a.hasManualData
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RText(
                            if (a.hasData) "${fmt1(animatedPct.toDouble())}%" else "—",
                            26f, p.text, weight = FontWeight.ExtraBold, display = true, tnum = true
                        )
                        RText("OVERALL", 10f, p.textSec, weight = FontWeight.Bold, letterSpacing = 1.2f)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    StatusPill(if (a.hasData) a.tierStatus.label else "No Data Yet", tint)
                    if (a.hasData) {
                        val diff = a.percentage - a.stipendTarget
                        RText(
                            "${if (diff >= 0) "+" else ""}${fmt1(diff)}% ${if (diff >= 0) "above" else "below"} target",
                            12f, if (diff >= 0) p.emerald else p.rose, weight = FontWeight.SemiBold, tnum = true
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        RText("${a.attendedCredits}", 22f, p.text, weight = FontWeight.Bold, display = true, tnum = true)
                        RText(" / ${a.conductedCredits} credit hrs", 11f, p.textSec, tnum = true)
                    }
                    RText("${a.workingDays} working days", 11f, p.text, weight = FontWeight.SemiBold, tnum = true)
                    RText(
                        "${a.holidayDays} holiday${if (a.holidayDays == 1) "" else "s"} · ${a.counts.noClass} cancelled",
                        10f, p.textMuted, tnum = true
                    )
                }
            }
            if (!a.hasData) {
                Spacer(Modifier.height(10.dp))
                RText("No confirmed attendance in this period yet. Numbers appear once sessions are marked.", 11f, p.textMuted)
            }
            if (a.counts.pending > 0) {
                Spacer(Modifier.height(10.dp))
                RText("🕒 ${a.counts.pending} session(s) unrecorded — not counted as present or absent.", 11f, p.amber)
            }
            if (onOpenMonthSummary != null && a.hasData) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(p.indigo.copy(alpha = 0.16f))
                        .pressClickable { onOpenMonthSummary() }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { RText("🎓 Month-end summary", 12f, p.indigo, weight = FontWeight.Bold) }
            }
        }
    }
}

private fun recoveryOrBuffer(eligible: Boolean, hasData: Boolean, buffer: Int, recovery: Int): String = when {
    !hasData -> "No data"
    eligible -> "+$buffer Hrs buffer"
    recovery < 0 -> "Not reachable"
    else -> "Need $recovery Hrs"
}

@Composable
fun EligibilityTile(
    p: RptPalette,
    title: String,
    eligible: Boolean,
    hasData: Boolean,
    buffer: Int,
    recovery: Int,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (!hasData) p.textMuted else if (eligible) p.emerald else p.rose
    GlassCard(p, modifier, corner = 20.dp) {
        Column(Modifier.padding(14.dp)) {
            RText(title, 12f, p.text, weight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            StatusPill(if (!hasData) "No data" else if (eligible) "Eligible" else "Not eligible", color, size = 10f)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RText(recoveryOrBuffer(eligible, hasData, buffer, recovery), 13f, color, weight = FontWeight.Bold, tnum = true, modifier = Modifier.weight(1f))
                icon(color)
            }
        }
    }
}

@Composable
fun AutoPresentStrip(p: RptPalette, a: ReportAnalytics) {
    val auto = a.subjects.filter { it.subject.isAutoPresent && it.conductedCredits > 0 }
    if (auto.isEmpty() && a.autoPresentBoostHours == 0) return
    GlassCard(p, Modifier.fillMaxWidth(), corner = 20.dp, accent = p.cyan) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(p.cyan.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                FlameGlyph(p.cyan, 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                RText("Auto-Present Boost", 13f, p.cyan, weight = FontWeight.Bold)
                RText(
                    auto.joinToString(" & ") { "${it.subject.shortName} (${it.attendedCredits}/${it.conductedCredits})" },
                    11f, p.textSec, maxLines = 2, tnum = true
                )
            }
            StatusPill("+${a.autoPresentBoostHours} Free Hrs", p.cyan)
        }
    }
}

/** Coming-soon PDF card. Intentionally has no click handlers: no fake preview/download/share. */
@Composable
fun PdfComingSoonCard(p: RptPalette) {
    GlassCard(p, Modifier.fillMaxWidth(), corner = 24.dp, accent = p.violet) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.trackBg), contentAlignment = Alignment.Center) { RText("📄", 20f, p.text) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    RText("PDF Report Generator", 15f, p.text, weight = FontWeight.Bold, display = true)
                    RText("Export Weekly, Monthly or Custom Range", 11f, p.textSec)
                }
                StatusPill("Coming Soon", p.amber, size = 10f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Summary PDF", "Detailed Matrix").forEach { t ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.trackBg)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { RText(t, 12f, p.textMuted, weight = FontWeight.SemiBold) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("👁️ Preview", "⬇️ Download PDF", "↗️").forEachIndexed { i, t ->
                    Box(
                        Modifier.weight(if (i == 1) 1.6f else 1f).clip(RoundedCornerShape(14.dp)).background(p.trackBg)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { RText(t, 12f, p.textMuted.copy(alpha = 0.7f), weight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
            RText("PDF export is not available in this version. Your report numbers above are unaffected.", 10f, p.textMuted)
        }
    }
}

// ---------------------------------------------------------------- subjects

fun subjectEmoji(key: String): String = when (key) {
    "grid" -> "🧵"; "flask" -> "🧪"; "testtube" -> "🧫"; "microscope" -> "🔬"; "scissors" -> "✂️"
    "workflow" -> "🧶"; "scale" -> "⚖️"; "sliders" -> "🎚️"; "book" -> "📚"; "presentation" -> "🎤"
    "monitorplay" -> "💻"; "award" -> "🏅"; "sparkles" -> "✨"; "activity" -> "🏃"
    else -> "📘"
}

fun subjectTypeLabel(r: SubjectReport): String {
    val cat = when (r.subject.category) {
        SubjectCategory.MAJOR -> "Major"
        SubjectCategory.MINOR -> "Minor/Honours"
        SubjectCategory.LIBRARY -> "Library"
        SubjectCategory.ALL -> ""
    }
    val kind = when {
        r.subject.isAutoPresent -> "Auto-credited · ${r.subject.creditHours}x"
        r.subject.creditHours >= 2 -> "${r.subject.creditHours}x Practical"
        else -> "1x Theory"
    }
    return listOf(cat, kind).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Status pill text/colour for one subject, from real numbers and configured targets. */
fun subjectStatus(r: SubjectReport, a: ReportAnalytics, p: RptPalette): Pair<String, Color> {
    if (r.conductedCredits == 0) return "No data" to p.textMuted
    fun need(h: Int, t: Int) = if (h < 0) "$t% unreachable" else "Need $h hrs for $t%"
    return when {
        r.percentage < a.examTarget -> need(r.recoveryHoursExam, a.examTarget) to p.rose
        r.percentage < a.stipendTarget -> need(r.recoveryHoursStipend, a.stipendTarget) to p.amber
        r.safeSkipHoursStipend <= 0 -> "At Stipend Limit" to p.amber
        else -> "Safe · Skip ${r.safeSkipHoursStipend} hr${if (r.safeSkipHoursStipend == 1) "" else "s"}" to p.emerald
    }
}

fun subjectPctColor(r: SubjectReport, a: ReportAnalytics, p: RptPalette): Color = when {
    r.conductedCredits == 0 -> p.textMuted
    r.percentage < a.examTarget -> p.rose
    r.percentage < a.stipendTarget -> p.amber
    else -> p.indigo
}

@Composable
fun SubjectRow(
    p: RptPalette,
    a: ReportAnalytics,
    r: SubjectReport,
    expanded: Boolean,
    onClick: () -> Unit,
    periodLabel: String,
    showAllHistory: Boolean,
    onToggleHistory: () -> Unit
) {
    val (statusText, statusColor) = subjectStatus(r, a, p)
    val danger = r.conductedCredits > 0 && r.percentage < a.examTarget
    GlassCard(p, Modifier.fillMaxWidth().pressClickable { onClick() }, corner = 20.dp, accent = if (danger) p.rose else null) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.trackBg), contentAlignment = Alignment.Center) {
                    RText(subjectEmoji(r.subject.iconKey), 18f, p.text)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText(r.subject.shortName, 14f, p.text, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (r.subject.isAutoPresent) { Spacer(Modifier.width(6.dp)); StatusPill("Auto", p.cyan, size = 10f) }
                    }
                    RText("${r.subject.code} · ${subjectTypeLabel(r)}", 11f, p.textSec, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        RText("${r.attendedCredits}/${r.conductedCredits} Hrs", 13f, p.text, weight = FontWeight.Bold, tnum = true)
                        if (r.conductedCredits > 0) {
                            RText(" (${fmt1(r.percentage)}%)", 12f, subjectPctColor(r, a, p), weight = FontWeight.Bold, tnum = true)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    StatusPill(statusText, statusColor, size = 10f)
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
            ) {
                SubjectDetail(p, a, r, periodLabel, showAllHistory, onToggleHistory)
            }
        }
    }
}

@Composable
private fun SubjectDetail(
    p: RptPalette,
    a: ReportAnalytics,
    r: SubjectReport,
    periodLabel: String,
    showAll: Boolean,
    onToggleHistory: () -> Unit
) {
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        // session counts
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(p, "Present", "${r.counts.present + r.counts.autoPresent}", p.emerald, Modifier.weight(1f))
            MiniStat(p, "Absent", "${r.counts.absent}", p.rose, Modifier.weight(1f))
            MiniStat(p, "No Class", "${r.counts.noClass}", p.amber, Modifier.weight(1f))
            if (r.counts.pending > 0) MiniStat(p, "Pending", "${r.counts.pending}", p.textSec, Modifier.weight(1f))
        }
        // recovery calculation
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.trackBg).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RText("Recovery Calculation", 12f, p.text, weight = FontWeight.Bold)
            if (r.conductedCredits == 0) {
                RText("No confirmed hours for this subject in $periodLabel.", 11f, p.textMuted)
            } else {
                RecoveryLine(p, "Exam target (${a.examTarget}%)", r.percentage >= a.examTarget, r.recoveryHoursExam, r.safeSkipHoursExam)
                RecoveryLine(p, "Stipend target (${a.stipendTarget}%)", r.percentage >= a.stipendTarget, r.recoveryHoursStipend, r.safeSkipHoursStipend)
            }
        }
        // history
        RText("Detailed Attendance History · $periodLabel", 12f, p.text, weight = FontWeight.Bold)
        val rows = r.sessions.filter { it.status != ReportSessionStatus.UPCOMING }.sortedBy { it.date }
        if (rows.isEmpty()) {
            RText("No past sessions in this period.", 11f, p.textMuted)
        } else {
            val shown = if (showAll || rows.size <= 12) rows else rows.takeLast(12)
            shown.forEach { HistoryRow(p, it) }
            if (rows.size > 12) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.indigo.copy(alpha = 0.14f)).pressClickable { onToggleHistory() }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    RText(if (showAll) "Show recent only" else "Show all ${rows.size} sessions", 11f, p.indigo, weight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(p: RptPalette, label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RText(value, 16f, color, weight = FontWeight.Bold, display = true, tnum = true)
        RText(label, 10f, p.textSec)
    }
}

@Composable
private fun RecoveryLine(p: RptPalette, label: String, met: Boolean, recovery: Int, skip: Int) {
    val text = when {
        met -> "Met · can miss $skip hr(s)"
        recovery < 0 -> "Not reachable"
        else -> "Attend next $recovery hr(s) in a row"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RText(label, 11f, p.textSec)
        RText(text, 11f, if (met) p.emerald else p.amber, weight = FontWeight.SemiBold, tnum = true)
    }
}

@Composable
private fun HistoryRow(p: RptPalette, s: SessionEntry) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(shortDate(s.date), 11f, p.text, weight = FontWeight.SemiBold, tnum = true, modifier = Modifier.width(56.dp))
            RText("${s.periodLabel}${if (s.isTutorial) " · Tutorial" else ""}", 10f, p.textMuted, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("${s.weight}h", 10f, p.textMuted, tnum = true)
            Spacer(Modifier.width(8.dp))
            StatusPill(statusLabel(s.status), statusColor(s.status, p), size = 10f)
        }
    }
}

@Composable
fun FilterChip(p: RptPalette, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) p.indigo else p.trackBg, tween(180), label = "chip")
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).pressClickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)
    ) { RText(label, 12f, if (selected) Color.White else p.textSec, weight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun SectionTitle(p: RptPalette, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            RText(title, 17f, p.text, weight = FontWeight.Bold, display = true)
            if (subtitle != null) RText(subtitle, 11f, p.textSec)
        }
        if (action != null && onAction != null) {
            Box(Modifier.pressClickable { onAction() }.padding(4.dp)) { RText(action, 12f, p.indigo, weight = FontWeight.Bold) }
        }
    }
}
