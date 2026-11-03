package com.example.ui.whatif

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.whatif.*
import com.example.ui.components.EntranceTracker
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import com.example.ui.theme.LocalGlassTheme

@Composable
fun lineColor(k: LineKind): Color {
    val t = LocalGlassTheme.current
    return when (k) {
        LineKind.PLANNED_PRESENT -> t.emeraldNeon
        LineKind.PLANNED_ABSENT -> t.crimsonRose
        LineKind.ASSUMED_PRESENT -> t.royalIndigo
        LineKind.AUTO_CREDITED -> t.electricTeal
        LineKind.CANCELLED -> t.textMuted
        LineKind.NOT_COUNTED -> t.textMuted
    }
}

fun lineLabel(k: LineKind): String = when (k) {
    LineKind.PLANNED_PRESENT -> "Planned present"
    LineKind.PLANNED_ABSENT -> "Planned absent"
    LineKind.ASSUMED_PRESENT -> "Assumed present"
    LineKind.AUTO_CREDITED -> "Automatic"
    LineKind.CANCELLED -> "Cancelled"
    LineKind.NOT_COUNTED -> "Not counted"
}

// ───────────────────────── shared result sections ─────────────────────────

/** Used by both the live Results screen and the read-only saved-snapshot screen. */
fun LazyListScope.resultItems(
    result: WhatIfResult,
    recovery: List<RecoverySuggestion>,
    bestCase: BestCaseSummary?,
    tracker: EntranceTracker
) {
    item(key = "hero") { HeroCard(result, Modifier.loadIn(0, tracker)) }
    item(key = "targets") {
        Row(Modifier.loadIn(1, tracker), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TargetCard("Exam", result.exam, result.projectedAttended, result.projectedConducted, Modifier.weight(1f))
            TargetCard("Stipend", result.stipend, result.projectedAttended, result.projectedConducted, Modifier.weight(1f))
        }
    }
    item(key = "summary") { PlanSummaryCard(result, Modifier.loadIn(2, tracker)) }
    if (bestCase != null) item(key = "best") { BestCaseCard(bestCase, result, Modifier.loadIn(3, tracker)) }
    item(key = "assumptions") { AssumptionsCard(result, Modifier.loadIn(4, tracker)) }
    if (recovery.isNotEmpty()) {
        item(key = "recoveryTitle") { SectionTitle("Smart recovery") }
        items(recovery.size, key = { "rec$it" }) { i -> RecoveryCard(recovery[i], Modifier.loadIn(minOf(i + 5, 8), tracker, key = 100 + i)) }
    }
}

@Composable
private fun HeroCard(r: WhatIfResult, modifier: Modifier) {
    val t = LocalGlassTheme.current
    GlassCard(modifier, accent = statusColor(r.exam.status)) {
        SectionTitle("Projected attendance")
        if (r.projectedConducted <= 0) {
            WiText("Nothing is counted yet. Plan at least one class to see a projection.", color = t.textSecondary, modifier = Modifier.padding(top = 6.dp))
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedPct(r.projectedPct, color = statusColor(r.exam.status))
            WiText(fmtDelta(r.deltaPoints), size = 14.sp, weight = FontWeight.SemiBold,
                color = when { (r.deltaPoints ?: 0.0) > 0 -> t.emeraldNeon; (r.deltaPoints ?: 0.0) < 0 -> t.crimsonRose; else -> t.textSecondary },
                modifier = Modifier.padding(bottom = 8.dp))
        }
        WiProgress((r.projectedPct / 100.0).toFloat(), statusColor(r.exam.status), Modifier.padding(top = 4.dp))
        WiText(
            "Now ${fmtPct(r.baselinePct)} (${r.baseline.attended}/${r.baseline.conducted} h)  →  ${fmtPct(r.projectedPct)} (${r.projectedAttended}/${r.projectedConducted} h)",
            size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 8.dp)
        )
        WiText(
            "${r.mode.label}" + (r.horizonEnd?.let { " · to ${WhatIfCalculator.shortDate(it)}" } ?: ""),
            size = 12.sp, weight = FontWeight.SemiBold, color = t.royalIndigo, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TargetCard(title: String, c: TargetCheck, att: Int, cond: Int, modifier: Modifier) {
    val t = LocalGlassTheme.current
    GlassCard(modifier, accent = statusColor(c.status), padding = 14.dp) {
        WiText("$title ${c.target}%", size = 13.sp, color = t.textSecondary, weight = FontWeight.SemiBold)
        WiText(statusLabel(c.status), size = 20.sp, weight = FontWeight.Bold, display = true, color = statusColor(c.status), modifier = Modifier.padding(top = 2.dp))
        val line = when (c.status) {
            TargetStatus.MET -> "You could miss ${hoursLabel(c.safeSkipHours)} more and still meet it."
            TargetStatus.NOT_MET -> if (c.hoursNeeded < 0) "Cannot be reached again after a miss." else "Needs ${hoursLabel(c.hoursNeeded)} more attended in a row."
            TargetStatus.NO_DATA -> "No sessions counted yet."
        }
        WiText(line, size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp))
        if (cond > 0) WiText("Exact: $att/$cond h", size = 11.sp, color = t.textMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PlanSummaryCard(r: WhatIfResult, modifier: Modifier) {
    val t = LocalGlassTheme.current
    GlassCard(modifier) {
        SectionTitle("What’s counted")
        fun row(k: LineKind) = r.count(k) to r.weight(k)
        listOf(LineKind.PLANNED_PRESENT, LineKind.PLANNED_ABSENT, LineKind.ASSUMED_PRESENT, LineKind.AUTO_CREDITED).forEach { k ->
            val (n, w) = row(k)
            if (n > 0) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                WiPill(lineLabel(k), lineColor(k))
                Spacer(Modifier.weight(1f))
                WiText("$n class${if (n == 1) "" else "es"} · ${w}h", size = 13.sp, color = t.textSecondary)
            }
        }
        val cancelled = r.count(LineKind.CANCELLED)
        if (cancelled > 0) WiText("$cancelled cancelled class(es) excluded", size = 12.sp, color = t.textMuted, modifier = Modifier.padding(top = 8.dp))
        if (r.lines.none { it.kind != LineKind.CANCELLED && it.kind != LineKind.NOT_COUNTED }) {
            WiText("No simulated sessions yet.", size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun BestCaseCard(best: BestCaseSummary, r: WhatIfResult, modifier: Modifier) {
    val t = LocalGlassTheme.current
    GlassCard(modifier) {
        SectionTitle("Best case")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WiText(fmtPct(best.pct), size = 26.sp, weight = FontWeight.Bold, display = true)
            WiPill("Exam ${statusLabel(best.exam)}", statusColor(best.exam))
            WiPill("Stipend ${statusLabel(best.stipend)}", statusColor(best.stipend))
        }
        WiText(
            "If you attend every remaining manual class through ${WhatIfCalculator.shortDate(best.horizonEnd)}. This is the ceiling, independent of your plan.",
            size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun AssumptionsCard(r: WhatIfResult, modifier: Modifier) {
    val t = LocalGlassTheme.current
    GlassCard(modifier) {
        SectionTitle("Assumptions")
        r.assumptions.forEach { WiText("•  $it", size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 5.dp)) }
        r.boundaryNote?.let { WiText(it, size = 12.sp, weight = FontWeight.SemiBold, color = t.amberGold, modifier = Modifier.padding(top = 8.dp)) }
        r.dropped.forEach {
            WiText("Skipped ${WhatIfCalculator.shortDate(it.item.date)} (${it.item.subjectCode}): ${it.reason}", size = 12.sp, color = t.amberGold, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RecoveryCard(s: RecoverySuggestion, modifier: Modifier) {
    val t = LocalGlassTheme.current
    val color = when (s.state) {
        RecoveryState.ATTEND_NEXT -> t.emeraldNeon
        RecoveryState.APPROACHING -> t.amberGold
        RecoveryState.NOT_ENOUGH_SESSIONS, RecoveryState.UNREACHABLE_100 -> t.crimsonRose
        else -> t.textMuted
    }
    GlassCard(modifier, accent = color, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WiText("${s.scopeName} · ${s.targetKind.label} ${s.target}%", size = 14.sp, weight = FontWeight.Bold, display = true, modifier = Modifier.weight(1f))
            WiPill(
                when (s.state) {
                    RecoveryState.ATTEND_NEXT -> "Recoverable"
                    RecoveryState.APPROACHING -> "Close to limit"
                    RecoveryState.NOT_ENOUGH_SESSIONS -> "Not enough classes"
                    RecoveryState.UNREACHABLE_100 -> "Unreachable"
                    RecoveryState.NO_DATA -> "No data"
                    RecoveryState.TARGET_MET -> "Met"
                }, color
            )
        }
        WiText("${fmtPct(s.currentPct)} now → ${fmtPct(s.projectedPct)} projected", size = 12.sp, color = t.textMuted, modifier = Modifier.padding(top = 2.dp))
        WiText(s.message, size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(top = 6.dp))
        s.flip?.let {
            WiText(
                "Or switch ${it.classCount} planned-absent class(es) (from ${WhatIfCalculator.shortDate(it.firstDate)}) to Present: ${fmtPct(it.projectedPct)}, target ${statusLabel(it.status).lowercase()}.",
                size = 12.sp, color = t.royalIndigo, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ───────────────────────── results screen ─────────────────────────

@Composable
fun ResultsScreen(ui: WhatIfUiState, c: WhatIfController) {
    val r = ui.result ?: return
    val tracker = rememberEntranceTracker()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "mode") {
            WiSegment(listOf("Plan Only", "Month End"), if (ui.mode == ProjectionMode.PLAN_ONLY) 0 else 1) {
                c.setMode(if (it == 0) ProjectionMode.PLAN_ONLY else ProjectionMode.MONTH_END)
            }
        }
        resultItems(r, ui.recovery, ui.bestCase, tracker)
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                WiGhostButton("Subject & day breakdown", Modifier.fillMaxWidth()) { c.go(WhatIfScreen.BREAKDOWN) }
                WiPrimaryButton("Save this scenario", Modifier.fillMaxWidth()) { c.beginSave() }
                WiGhostButton("Compare with saved scenarios", Modifier.fillMaxWidth()) {
                    c.setCompareWithCurrent()
                    c.go(WhatIfScreen.SAVED)
                }
            }
        }
    }
}

// ───────────────────────── breakdown ─────────────────────────

@Composable
fun BreakdownScreen(result: WhatIfResult) {
    val t = LocalGlassTheme.current
    val tracker = rememberEntranceTracker()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("By subject") }
        val subs = result.subjects.filter { it.projectedConducted > 0 || it.baselineConducted > 0 }
        items(subs.size, key = { "sub${subs[it].code}" }) { i ->
            val s = subs[i]
            GlassCard(Modifier.loadIn(minOf(i, 8), tracker), accent = if (s.exam.status == TargetStatus.NOT_MET) t.crimsonRose else null, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WiText(s.shortName + if (s.isLab) " · Lab" else "", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Bold, display = true)
                    WiText("${fmtPct(s.baselinePct)} → ${fmtPct(s.projectedPct)}", size = 14.sp, weight = FontWeight.SemiBold, color = statusColor(s.exam.status))
                }
                WiProgress((s.projectedPct / 100.0).toFloat(), statusColor(s.exam.status), Modifier.padding(vertical = 6.dp), 6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WiPill("Exam ${statusLabel(s.exam.status)}", statusColor(s.exam.status))
                    WiPill("Stipend ${statusLabel(s.stipend.status)}", statusColor(s.stipend.status))
                }
                WiText("${s.baselineAttended}/${s.baselineConducted} h now · ${s.projectedAttended}/${s.projectedConducted} h projected", size = 11.sp, color = t.textMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val byDate = result.lines.groupBy { it.date }.toSortedMap()
        if (byDate.isNotEmpty()) item { SectionTitle("Day by day") }
        val dates = byDate.keys.toList()
        items(dates.size, key = { "day${dates[it]}" }) { i ->
            val d = dates[i]
            GlassCard(padding = 14.dp) {
                WiText(longDate(d), size = 14.sp, weight = FontWeight.Bold, display = true)
                byDate[d].orEmpty().sortedBy { it.periodNumber }.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        WiText("${l.subjectName} · ${l.periodLabel}", Modifier.weight(1f), size = 12.sp, color = t.textSecondary)
                        WiPill("${lineLabel(l.kind)} · ${l.weight}h", lineColor(l.kind))
                    }
                }
            }
        }
        if (result.excludedDates.isNotEmpty()) {
            item { SectionTitle("Excluded") }
            item {
                GlassCard {
                    result.excludedDates.forEach { WiText("${longDate(it.date)} — ${it.reason}", size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}
