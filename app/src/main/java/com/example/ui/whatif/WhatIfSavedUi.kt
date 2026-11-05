package com.example.ui.whatif

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.whatif.*
import com.example.ui.components.loadIn
import com.example.ui.components.rememberEntranceTracker
import com.example.ui.theme.LocalGlassTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAVED_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
fun savedLabel(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Kolkata")).format(SAVED_FMT)

// ───────────────────────── dialogs ─────────────────────────

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { if (it.length <= 48) text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ───────────────────────── saved list ─────────────────────────

@Composable
fun SavedScreen(ui: WhatIfUiState, c: WhatIfController) {
    val t = LocalGlassTheme.current
    val tracker = rememberEntranceTracker()
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val hasCurrent = ui.result != null && !ui.plan.isEmpty

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ui.unreadableSaved > 0 || ui.newerVersionSaved > 0) {
                item {
                    GlassCard(accent = t.amberGold) {
                        WiText(
                            buildString {
                                if (ui.unreadableSaved > 0) append("${ui.unreadableSaved} saved scenario(s) couldn’t be read and were left untouched. ")
                                if (ui.newerVersionSaved > 0) append("${ui.newerVersionSaved} were saved by a newer app version and are hidden until you update.")
                            }, size = 12.sp, color = t.amberGold
                        )
                    }
                }
            }
            if (hasCurrent) {
                item {
                    val r = ui.result ?: return@item
                    SelectableCard(
                        title = "Current plan (unsaved)", subtitle = "${r.mode.label} · ${ui.plan.dates.size} date(s)",
                        pct = r.projectedPct, exam = r.exam.status, stipend = r.stipend.status,
                        partial = r.baseline.completeness == BaselineCompleteness.PARTIAL,
                        selected = WhatIfController.CURRENT_ID in ui.compareSelection, onToggle = { c.toggleCompare(WhatIfController.CURRENT_ID) },
                        onOpen = null, actions = null
                    )
                }
            }
            if (ui.saved.isEmpty()) {
                item {
                    GlassCard {
                        WiText("No saved scenarios yet", size = 16.sp, weight = FontWeight.Bold, display = true)
                        WiText("Plan something on the calendar, open the results and tap “Save this scenario”. Saved scenarios keep their exact numbers.", size = 13.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            items(ui.saved.size, key = { ui.saved[it].id }) { i ->
                val s = ui.saved[i]
                SelectableCard(
                    title = s.name, subtitle = "${savedLabel(s.savedAtMillis)} · ${s.mode.label}",
                    pct = s.result.projectedPct, exam = s.result.exam.status, stipend = s.result.stipend.status,
                    partial = s.result.baseline.completeness == BaselineCompleteness.PARTIAL,
                    selected = s.id in ui.compareSelection, onToggle = { c.toggleCompare(s.id) },
                    onOpen = { c.openSnapshot(s.id) },
                    actions = listOf("Rename" to { renameId = s.id }, "Duplicate" to { c.duplicate(s.id) }, "Delete" to { deleteId = s.id }),
                    modifier = Modifier.loadIn(minOf(i, 8), tracker)
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(t.cardSurface).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WiText(
                if (ui.compareSelection.size < 2) "Select 2–${WhatIfController.MAX_COMPARE} scenarios to compare" else "${ui.compareSelection.size} selected",
                size = 12.sp, color = t.textSecondary
            )
            WiPrimaryButton("Compare", Modifier.fillMaxWidth(), enabled = ui.compareSelection.size >= 2) { c.startCompare() }
        }
    }

    renameId?.let { id ->
        val cur = ui.saved.firstOrNull { it.id == id }
        NameDialog("Rename scenario", cur?.name ?: "", "Rename", { renameId = null }) { c.rename(id, it); renameId = null }
    }
    deleteId?.let { id ->
        ConfirmDialog("Delete scenario?", "This can’t be undone. Your real attendance is not affected.", "Delete", { deleteId = null }) { c.delete(id); deleteId = null }
    }
}

@Composable
private fun SelectableCard(
    title: String, subtitle: String, pct: Double, exam: TargetStatus, stipend: TargetStatus, partial: Boolean,
    selected: Boolean, onToggle: () -> Unit, onOpen: (() -> Unit)?, actions: List<Pair<String, () -> Unit>>?,
    modifier: Modifier = Modifier
) {
    val t = LocalGlassTheme.current
    GlassCard(modifier.then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier), accent = if (selected) t.royalIndigo else null, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                WiText(title, size = 16.sp, weight = FontWeight.Bold, display = true, maxLines = 1)
                WiText(subtitle, size = 11.sp, color = t.textMuted, maxLines = 1)
            }
            WiText(fmtPct(pct), size = 18.sp, weight = FontWeight.Bold, display = true)
            val ring = wiColor(if (selected) t.royalIndigo else t.cardBorder)
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (selected) t.royalIndigo else Color.Transparent).border(2.dp, ring, CircleShape).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) { if (selected) WiText("✓", size = 13.sp, weight = FontWeight.Bold, color = Color.White) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WiPill("Exam ${statusLabel(exam)}", statusColor(exam))
            WiPill("Stipend ${statusLabel(stipend)}", statusColor(stipend))
            if (partial) WiPill("Partial baseline", t.amberGold)
        }
        if (actions != null) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                actions.forEach { (label, fn) ->
                    WiText(label, size = 12.sp, weight = FontWeight.SemiBold, color = if (label == "Delete") t.crimsonRose else t.royalIndigo, modifier = Modifier.clickable(onClick = fn))
                }
            }
        }
    }
}

// ───────────────────────── snapshot detail ─────────────────────────

@Composable
fun SnapshotScreen(ui: WhatIfUiState, c: WhatIfController) {
    val t = LocalGlassTheme.current
    val snap = ui.saved.firstOrNull { it.id == ui.openSnapshotId }
    if (snap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { WiText("This scenario is no longer available.", color = t.textSecondary) }
        return
    }
    val shown = ui.recalculated ?: snap
    val tracker = rememberEntranceTracker()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "banner") {
            if (ui.recalculated == null) {
                GlassCard(accent = t.royalIndigo) {
                    WiText(snap.name, size = 18.sp, weight = FontWeight.Bold, display = true)
                    WiText("Saved ${savedLabel(snap.savedAtMillis)}", size = 12.sp, color = t.textSecondary)
                    WiText("Shown exactly as saved — nothing is recalculated.", size = 12.sp, color = t.royalIndigo, modifier = Modifier.padding(top = 4.dp))
                    val b = snap.result.baseline
                    WiText(
                        "Baseline ${b.attended}/${b.conducted} h (${b.rangeStart?.let { WhatIfCalculator.shortDate(it) } ?: "—"} → ${WhatIfCalculator.shortDate(b.rangeEnd)}) · ${snap.result.batch}, ${snap.result.course}",
                        size = 11.sp, color = t.textMuted, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                GlassCard(accent = t.amberGold) {
                    WiText("Recalculated with today’s data", size = 16.sp, weight = FontWeight.Bold, display = true, color = t.amberGold)
                    WiText(
                        "Saved ${fmtPct(snap.result.projectedPct)} → now ${fmtPct(ui.recalculated.result.projectedPct)}. This is a new, unsaved result; the original is unchanged.",
                        size = 12.sp, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        resultItems(shown.result, shown.recovery, shown.bestCase, tracker)
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (ui.recalculated == null) {
                    WiPrimaryButton("Recalculate with current data", Modifier.fillMaxWidth()) { c.recalculateOpenSnapshot() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WiGhostButton("Rename", Modifier.weight(1f)) { renaming = true }
                        WiGhostButton("Duplicate", Modifier.weight(1f)) { c.duplicate(snap.id) }
                        WiGhostButton("Delete", Modifier.weight(1f), color = t.crimsonRose) { deleting = true }
                    }
                } else {
                    WiPrimaryButton("Save as new scenario", Modifier.fillMaxWidth()) { c.saveRecalculationAsNew() }
                    WiGhostButton("Back to saved values", Modifier.fillMaxWidth()) { c.dismissRecalculation() }
                }
            }
        }
    }
    if (renaming) NameDialog("Rename scenario", snap.name, "Rename", { renaming = false }) { c.rename(snap.id, it); renaming = false }
    if (deleting) ConfirmDialog("Delete scenario?", "This can’t be undone.", "Delete", { deleting = false }) { deleting = false; c.delete(snap.id); c.back() }
}

// ───────────────────────── comparison ─────────────────────────

@Composable
fun CompareScreen(report: ComparisonReport) {
    val t = LocalGlassTheme.current
    val tracker = rememberEntranceTracker()
    val e = report.entries
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (report.differences.isNotEmpty()) {
            item {
                GlassCard(accent = t.amberGold) {
                    WiText("Not a like-for-like comparison", size = 15.sp, weight = FontWeight.Bold, display = true, color = t.amberGold)
                    report.differences.forEach {
                        WiText(it.title, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        WiText(it.detail, size = 11.sp, color = t.textSecondary)
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.loadIn(0, tracker)) {
                WiText(report.explanation, size = 13.sp, color = t.textSecondary)
            }
        }
        item {
            MetricRow("Projected attendance", e, report.leaderIndex, Modifier.loadIn(1, tracker)) { s, leader ->
                WiText(fmtPct(s.result.projectedPct), size = 20.sp, weight = FontWeight.Bold, display = true, color = if (leader) t.emeraldNeon else t.textPrimary)
                WiText(fmtDelta(s.result.deltaPoints) + " vs baseline", size = 10.sp, color = t.textMuted)
                if (leader) WiPill("Strongest", t.emeraldNeon, Modifier.padding(top = 4.dp))
            }
        }
        item {
            MetricRow("Targets", e, null, Modifier.loadIn(2, tracker)) { s, _ ->
                WiPill("Exam ${s.result.examTarget}%: ${statusLabel(s.result.exam.status)}", statusColor(s.result.exam.status))
                Spacer(Modifier.height(4.dp))
                WiPill("Stipend ${s.result.stipendTarget}%: ${statusLabel(s.result.stipend.status)}", statusColor(s.result.stipend.status))
            }
        }
        item {
            MetricRow("Plan", e, null, Modifier.loadIn(3, tracker)) { s, _ ->
                WiText("${s.result.weight(LineKind.PLANNED_PRESENT)}h present", size = 12.sp, color = t.emeraldNeon)
                WiText("${s.result.weight(LineKind.PLANNED_ABSENT)}h absent", size = 12.sp, color = t.crimsonRose)
                if (s.result.weight(LineKind.ASSUMED_PRESENT) > 0) WiText("${s.result.weight(LineKind.ASSUMED_PRESENT)}h assumed", size = 11.sp, color = t.textMuted)
            }
        }
        item {
            MetricRow("Baseline & horizon", e, null, Modifier.loadIn(4, tracker)) { s, _ ->
                WiText("${s.result.baseline.attended}/${s.result.baseline.conducted} h", size = 12.sp)
                WiText(s.result.mode.label + (s.result.horizonEnd?.let { " · " + WhatIfCalculator.shortDate(it) } ?: ""), size = 11.sp, color = t.textMuted)
                if (s.result.baseline.completeness == BaselineCompleteness.PARTIAL) WiText("partial baseline", size = 10.sp, color = t.amberGold)
            }
        }
        item { SectionTitle("By subject (projected)") }
        items(report.subjectRows.size, key = { "cs${report.subjectRows[it].code}" }) { i ->
            val row = report.subjectRows[i]
            GlassCard(padding = 12.dp) {
                WiText(row.name, size = 13.sp, weight = FontWeight.Bold, display = true)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    row.values.forEach { v ->
                        WiText(if (v == null || v.projectedConducted == 0) "—" else fmtPct(v.pct), Modifier.weight(1f), size = 14.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    title: String, entries: List<WhatIfSnapshot>, leaderIndex: Int?, modifier: Modifier,
    cell: @Composable ColumnScope.(WhatIfSnapshot, Boolean) -> Unit
) {
    val t = LocalGlassTheme.current
    GlassCard(modifier, padding = 14.dp) {
        SectionTitle(title)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEachIndexed { i, s ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    WiText(s.name, size = 11.sp, color = t.textSecondary, maxLines = 1, align = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    cell(s, leaderIndex == i)
                }
            }
        }
    }
}
