package com.example.ui.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.holidays.AktuHolidays
import com.example.data.pdf.PdfFileManager
import com.example.data.reports.ReportAnalyticsEngine
import com.example.data.reports.ReportDateRange
import com.example.ui.reports.RText
import com.example.ui.reports.pressClickable
import com.example.ui.reports.rememberReducedMotion
import com.example.ui.reports.rememberReportHaptics
import com.example.ui.reports.rptPalette
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.ui.viewmodel.PdfInputs
import com.example.ui.viewmodel.PdfReportViewModel
import com.example.ui.viewmodel.PdfUiState
import java.time.LocalDate

/**
 * The single PDF Report implementation. Home, Reports and Notifications all open it through
 * [AttendanceViewModel.showPdfPreview], so there is exactly one flow, one generator and one set
 * of calculations. Mounted once in MainActivity as a full-screen overlay above the dock.
 *
 * Mode strings accepted by showPdfPreview: "SUMMARY", "DETAILED", "WEEKLY_SUMMARY", "WEEKLY_DETAILED".
 */
@Composable
fun PdfReportHost(attendanceVm: AttendanceViewModel, modifier: Modifier = Modifier) {
    val show by attendanceVm.showPdfPreviewDialog.collectAsState()
    val mode by attendanceVm.pdfMode.collectAsState()
    val pdfVm: PdfReportViewModel = viewModel()
    val ui by pdfVm.ui.collectAsState()

    LaunchedEffect(show) {
        if (show) pdfVm.open(mode, AttendanceViewModel.liveToday())
    }

    BackHandler(enabled = show) {
        when (ui) {
            PdfUiState.Config -> attendanceVm.showPdfPreview(false)
            PdfUiState.Generating -> Unit
            else -> pdfVm.backToConfig()
        }
    }

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(260)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 10 } +
            scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 0.95f),
        exit = fadeOut(tween(200)) + slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it / 12 } +
            scaleOut(tween(260), targetScale = 0.97f),
        modifier = modifier
    ) {
        PdfReportSurface(attendanceVm, pdfVm)
    }
}

private fun depth(s: PdfUiState): Int = when (s) {
    PdfUiState.Config -> 0
    PdfUiState.Generating -> 1
    else -> 2
}

@Composable
private fun PdfReportSurface(attendanceVm: AttendanceViewModel, pdfVm: PdfReportViewModel) {
    val glass = LocalGlassTheme.current
    val p = remember(glass.isDark) { rptPalette(glass.isDark) }
    val reduced = rememberReducedMotion()
    val repo = attendanceVm.repository
    val ctx = LocalContext.current

    val records by repo.recordsMap.collectAsState()
    val holidays by repo.holidayOverrides.collectAsState()
    val profile by attendanceVm.profile.collectAsState()
    val ui by pdfVm.ui.collectAsState()
    val config by pdfVm.config.collectAsState()
    val notice by pdfVm.notice.collectAsState()
    val today: LocalDate = AttendanceViewModel.liveToday()
    val haptic = rememberReportHaptics(profile.hapticFeedbackEnabled)

    // Only months with real (manual) attendance, plus the current month, are offered.
    val archive = remember(records, holidays, profile, today) {
        ReportAnalyticsEngine.archiveMonths(records, repo::isDateHoliday, profile, today)
    }
    val earliestWeek = remember(records, today) {
        val first = records.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(today) }.minOrNull() ?: today
        ReportDateRange.weekContaining(first).start
    }

    fun inputs() = PdfInputs(
        records = records,
        isHoliday = repo::isDateHoliday,
        holidayName = { AktuHolidays.getHoliday(it)?.name },
        profile = profile,
        today = AttendanceViewModel.liveToday()
    )

    val saveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> pdfVm.saveTo(uri) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(glass.canvasStart, glass.canvasEnd)))
            // Swallow touches so nothing underneath (dock, lists) reacts while the report is open.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        if (glass.isDark && profile.starfieldAnimationEnabled && !reduced) {
            com.example.ui.components.StarfieldCanvas(modifier = Modifier.fillMaxSize(), enabled = true)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val title = when (ui) {
                is PdfUiState.Preview -> "Report preview"
                PdfUiState.Generating -> "Creating report"
                else -> "PDF Report"
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(p.trackBg)
                        .pressClickable(enabled = ui != PdfUiState.Generating) {
                            haptic()
                            if (ui == PdfUiState.Config) attendanceVm.showPdfPreview(false) else pdfVm.backToConfig()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.text, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (fadeIn(tween(260, 60)) + slideInVertically(tween(300)) { it / 2 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 2 })
                    },
                    label = "pdfTitle"
                ) { t -> RText(t, 20f, p.text, weight = FontWeight.Bold, display = true) }
            }

            AnimatedContent(
                targetState = ui,
                contentKey = { it::class },
                transitionSpec = {
                    val dir = if (depth(targetState) >= depth(initialState)) 1 else -1
                    if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(340, 80)) +
                        slideInHorizontally(tween(440, easing = FastOutSlowInEasing)) { dir * it / 7 } +
                        scaleIn(tween(440, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(160)) +
                            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it / 9 } +
                            scaleOut(tween(260), targetScale = 0.98f))
                },
                label = "pdfState",
                modifier = Modifier.weight(1f)
            ) { s ->
                when (s) {
                    PdfUiState.Config -> PdfConfigScreen(
                        p = p, config = config, archive = archive, earliestWeek = earliestWeek, today = today,
                        haptic = haptic, onChange = { pdfVm.update(it) }, onGenerate = { pdfVm.generate(inputs()) },
                        reduced = reduced
                    )
                    PdfUiState.Generating -> PdfGeneratingState(p, reduced)
                    is PdfUiState.Preview -> PdfPreviewScreen(
                        p = p, state = s, notice = notice,
                        onSave = {
                            if (!s.file.exists()) pdfVm.fileMissing()
                            else try { saveLauncher.launch(s.fileName) } catch (e: Exception) {
                                pdfVm.reportSaveFailure()
                            }
                        },
                        onShare = {
                            if (!s.file.exists()) pdfVm.fileMissing()
                            else try { ctx.startActivity(PdfFileManager.shareIntent(ctx, s.file, s.fileName)) }
                            catch (e: Exception) { pdfVm.reportShareFailure() }
                        },
                        onEdit = { pdfVm.backToConfig() },
                        onClose = { attendanceVm.showPdfPreview(false) },
                        onDismissNotice = { pdfVm.clearNotice() },
                        reduced = reduced
                    )
                    is PdfUiState.Empty -> PdfMessageState(
                        p, kind = PdfMessageKind.INFO, title = "No attendance in this period", message = s.message,
                        primaryLabel = "Change period", onPrimary = { pdfVm.backToConfig() },
                        reduced = reduced
                    )
                    is PdfUiState.Insufficient -> PdfMessageState(
                        p, kind = PdfMessageKind.WARNING, title = "Not enough attendance data", message = s.message,
                        primaryLabel = "Change period", onPrimary = { pdfVm.backToConfig() },
                        reduced = reduced
                    )
                    is PdfUiState.Failed -> PdfMessageState(
                        p, kind = PdfMessageKind.ERROR, title = "Couldn't create the report", message = s.message,
                        primaryLabel = "Try again", onPrimary = { pdfVm.generate(inputs()) },
                        secondaryLabel = "Change period", onSecondary = { pdfVm.backToConfig() },
                        reduced = reduced
                    )
                }
            }
        }
    }
}
