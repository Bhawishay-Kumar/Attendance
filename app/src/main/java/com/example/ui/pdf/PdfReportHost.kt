package com.example.ui.pdf

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.holidays.AktuHolidays
import com.example.data.pdf.PdfFileManager
import com.example.data.pdf.PdfReportType
import com.example.ui.upgrade.Aurora
import com.example.ui.upgrade.AuroraBackground
import com.example.ui.upgrade.PdfReportScreen
import com.example.ui.upgrade.ReportState
import com.example.ui.upgrade.Txt
import com.example.ui.upgrade.pressScale
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.ui.viewmodel.PdfInputs
import com.example.ui.viewmodel.PdfReportViewModel
import com.example.ui.viewmodel.PdfUiState
import kotlinx.coroutines.delay

/**
 * Upgraded PDF Report Host.
 * Connects the real attendance PDF engine to the Aurora Glass PdfReportScreen.
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

@Composable
private fun PdfReportSurface(attendanceVm: AttendanceViewModel, pdfVm: PdfReportViewModel) {
    val repo = attendanceVm.repository
    val ctx = LocalContext.current

    val records by repo.recordsMap.collectAsState()
    val profile by attendanceVm.profile.collectAsState()
    val ui by pdfVm.ui.collectAsState()

    fun inputs() = PdfInputs(
        records = records,
        isHoliday = repo::isDateHoliday,
        holidayName = { AktuHolidays.getHoliday(it)?.name },
        profile = profile,
        today = AttendanceViewModel.liveToday()
    )

    var animatedProgress by remember { mutableFloatStateOf(0f) }

    val reportState = when (ui) {
        PdfUiState.Config -> ReportState.Idle
        PdfUiState.Generating -> ReportState.Generating
        is PdfUiState.Preview -> ReportState.Done
        else -> ReportState.Idle
    }

    LaunchedEffect(reportState) {
        if (reportState == ReportState.Generating) {
            animatedProgress = 0.08f
            // Progress smoothly increments across the 4 steps
            val totalSteps = 35
            for (step in 1..totalSteps) {
                delay(38)
                animatedProgress = (step.toFloat() / totalSteps).coerceIn(0.08f, 0.95f)
            }
        } else if (reportState == ReportState.Done) {
            animatedProgress = 1f
        } else {
            animatedProgress = 0f
        }
    }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Intercept clicks so underlying views do not react
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.18f), CircleShape)
                        .pressScale(haptic = true) {
                            if (ui == PdfUiState.Config) attendanceVm.showPdfPreview(false) else pdfVm.backToConfig()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Txt("←", 20, Aurora.TextHi, FontWeight.Bold)
                }

                Txt("PDF Report Generator", 13, Aurora.TextLo, FontWeight.SemiBold)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(50))
                        .pressScale(haptic = true) {
                            attendanceVm.showPdfPreview(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Txt("Close", 13, Aurora.TextHi, FontWeight.SemiBold)
                }
            }

            // Upgraded PDF Report Screen
            PdfReportScreen(
                state = reportState,
                progress = animatedProgress,
                onGenerate = { charts, subjectWise, predictions ->
                    pdfVm.update {
                        it.copy(reportType = if (subjectWise) PdfReportType.DETAILED else PdfReportType.SUMMARY)
                    }
                    pdfVm.generate(inputs())
                },
                onShare = {
                    val preview = ui as? PdfUiState.Preview
                    if (preview != null && preview.file.exists()) {
                        try {
                            ctx.startActivity(PdfFileManager.shareIntent(ctx, preview.file, preview.fileName))
                        } catch (e: Exception) {
                            pdfVm.reportShareFailure()
                        }
                    }
                },
                onReset = {
                    pdfVm.backToConfig()
                },
                modifier = Modifier.weight(1f)
            )

            // Feedback notice if generation failed or had insufficient data
            val noticeMessage = when (val s = ui) {
                is PdfUiState.Failed -> s.message
                is PdfUiState.Empty -> s.message
                is PdfUiState.Insufficient -> s.message
                else -> null
            }

            if (noticeMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Aurora.Rose.copy(0.18f))
                        .border(1.dp, Aurora.Rose.copy(0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Txt(noticeMessage, 13, Aurora.Rose, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Preview(name = "PDF Report Dark", showBackground = true, backgroundColor = 0xFF080816, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PdfReportScreenDarkPreview() {
    AuroraBackground {
        PdfReportScreen(
            state = ReportState.Done,
            progress = 1f,
            onGenerate = { _, _, _ -> },
            onShare = {},
            onReset = {}
        )
    }
}
