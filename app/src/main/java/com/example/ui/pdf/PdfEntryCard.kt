package com.example.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.pdf.PdfReportBuilder
import com.example.data.reports.ReportAnalytics
import com.example.data.reports.ReportAnalyticsEngine
import com.example.ui.reports.GlassCard
import com.example.ui.reports.RText
import com.example.ui.reports.RptPalette
import com.example.ui.reports.shineSweep
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

/**
 * The Reports-screen entry to the PDF feature (replaces the old "Coming soon" card). It only
 * opens the shared PDF flow via [onOpen]; it never generates anything itself. The optional
 * snapshot line reuses the figures already on screen (one line, no repeated blocks).
 */
@Composable
fun PdfReportEntryCard(p: RptPalette, analytics: ReportAnalytics?, onOpen: (String) -> Unit) {
    val reduced = com.example.ui.reports.rememberReducedMotion()
    GlassCard(p, Modifier.fillMaxWidth(), corner = 24.dp, accent = p.cyan) {
        Column(
            Modifier.shineSweep(enabled = !reduced, periodMs = 5200, strength = 0.12f).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).floating(reduced).clip(RoundedCornerShape(14.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.indigo, p.violet, p.cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    RText("PDF Report", 15f, p.text, weight = FontWeight.Bold, display = true)
                    RText("Weekly, Monthly or Custom Range", 11f, p.textSec)
                }
            }
            if (analytics != null && analytics.hasManualData) {
                val r = analytics.range
                val label = if (r.isWholeMonth) ReportAnalyticsEngine.monthLabel(YearMonth.from(r.start))
                else "${r.start.format(dayFmt)} – ${r.end.format(dayFmt)}"
                val pct = PdfReportBuilder.percentText(
                    analytics.attendedCredits, analytics.conductedCredits,
                    listOf(analytics.examTarget, analytics.stipendTarget)
                )
                RText(
                    "$label: $pct  ·  ${analytics.attendedCredits}/${analytics.conductedCredits} credit hrs",
                    11.5f, p.textSec, tnum = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PdfButton(p, "Summary", Modifier.weight(1f), filled = true) { onOpen("SUMMARY") }
                PdfButton(p, "Detailed", Modifier.weight(1f), filled = false) { onOpen("DETAILED") }
            }
        }
    }
}
