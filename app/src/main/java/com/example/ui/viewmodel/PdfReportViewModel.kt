package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.pdf.PdfConfig
import com.example.data.pdf.PdfConfigResolver
import com.example.data.pdf.PdfFileManager
import com.example.data.pdf.PdfPeriodType
import com.example.data.pdf.PdfReportBuilder
import com.example.data.pdf.PdfReportOutcome
import com.example.data.pdf.PdfReportRenderer
import com.example.data.pdf.PdfReportRequest
import com.example.data.pdf.PdfReportType
import com.example.data.reports.ReportDateRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

sealed interface PdfUiState {
    data object Config : PdfUiState
    data object Generating : PdfUiState
    data class Preview(val file: File, val fileName: String, val pageCount: Int) : PdfUiState
    data class Empty(val message: String) : PdfUiState
    data class Insufficient(val message: String) : PdfUiState
    data class Failed(val message: String) : PdfUiState
}

/** One-line feedback shown inside the preview screen after a save/share attempt. */
data class PdfNotice(val text: String, val isError: Boolean)

/** Read-only snapshot of the app data a report is built from. */
class PdfInputs(
    val records: Map<String, Map<Int, PeriodStatus>>,
    val isHoliday: (String) -> Boolean,
    val holidayName: (String) -> String?,
    val profile: StudentProfile,
    val today: LocalDate
)

/**
 * Owns the PDF report flow (configuration, generation, preview, save/share feedback). It only
 * reads attendance data handed to it as a snapshot and never writes to attendance, timetable,
 * holiday, target or profile data.
 */
class PdfReportViewModel(application: Application) : AndroidViewModel(application) {

    private val _config = MutableStateFlow(PdfConfig())
    val config: StateFlow<PdfConfig> = _config.asStateFlow()

    private val _ui = MutableStateFlow<PdfUiState>(PdfUiState.Config)
    val ui: StateFlow<PdfUiState> = _ui.asStateFlow()

    private val _notice = MutableStateFlow<PdfNotice?>(null)
    val notice: StateFlow<PdfNotice?> = _notice.asStateFlow()

    private var job: Job? = null

    private companion object {
        const val MIN_GENERATING_MS = 1300L
    }

    /** Called whenever the report screen is opened from any entry point. */
    fun open(mode: String, today: LocalDate) {
        if (job?.isActive == true) return // keep an in-flight generation
        val wantsDetailed = mode.uppercase().contains("DETAILED")
        val wantsWeekly = mode.uppercase().startsWith("WEEKLY")
        _config.value = _config.value.let { c ->
            c.copy(
                reportType = if (wantsDetailed) PdfReportType.DETAILED else PdfReportType.SUMMARY,
                periodType = if (wantsWeekly) PdfPeriodType.WEEKLY else c.periodType,
                monthId = c.monthId ?: today.toString().take(7),
                weekStart = c.weekStart ?: ReportDateRange.weekContaining(today).start,
                customEnd = (c.customEnd ?: today).let { if (it.isAfter(today)) today else it },
                customStart = (c.customStart ?: today.withDayOfMonth(1)).let { if (it.isAfter(today)) today else it }
            )
        }
        _notice.value = null
        _ui.value = PdfUiState.Config
    }

    fun update(transform: (PdfConfig) -> PdfConfig) {
        if (_ui.value is PdfUiState.Generating) return
        _config.value = transform(_config.value)
    }

    fun backToConfig() {
        if (_ui.value is PdfUiState.Generating) return
        _notice.value = null
        _ui.value = PdfUiState.Config
    }

    /** Builds the report off the main thread. Repeated taps while generating are ignored. */
    fun generate(inputs: PdfInputs) {
        if (_ui.value is PdfUiState.Generating || job?.isActive == true) return
        val cfg = _config.value
        val (range, error) = PdfConfigResolver.resolve(cfg, inputs.today)
        if (range == null) {
            _ui.value = PdfUiState.Failed(error ?: "Please check the selected period.")
            return
        }
        _notice.value = null
        _ui.value = PdfUiState.Generating
        val app = getApplication<Application>()
        job = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val next: PdfUiState = try {
                withContext(Dispatchers.IO) {
                    val request = PdfReportRequest(cfg.periodType, cfg.reportType, range)
                    when (val o = PdfReportBuilder.build(
                        request, inputs.records, inputs.isHoliday, inputs.holidayName, inputs.profile, inputs.today
                    )) {
                        is PdfReportOutcome.Ready -> {
                            val file = PdfFileManager.newTarget(app, o.data.fileName)
                            val pages = PdfReportRenderer.render(o.data, file)
                            check(file.exists() && file.length() > 0L) { "The PDF file was not created." }
                            PdfUiState.Preview(file, o.data.fileName, pages)
                        }
                        is PdfReportOutcome.NoRecords -> PdfUiState.Empty(o.message)
                        is PdfReportOutcome.InsufficientData -> PdfUiState.Insufficient(o.message)
                        is PdfReportOutcome.Invalid -> PdfUiState.Failed(o.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PdfUiState.Failed("We couldn't create the PDF. Please try again.")
            }
            // Keep the "creating" animation on screen long enough to read as a smooth transition.
            val wait = MIN_GENERATING_MS - (System.currentTimeMillis() - startedAt)
            if (wait > 0) delay(wait)
            _ui.value = next
        }
    }

    /** Copies the generated PDF to the document the user picked. Reports the real outcome. */
    fun saveTo(uri: Uri?) {
        if (uri == null) return // picker cancelled
        val st = _ui.value as? PdfUiState.Preview ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!st.file.exists()) Result.failure(IllegalStateException("The report file is no longer available. Generate it again."))
                else PdfFileManager.saveTo(app, st.file, uri)
            }
            _notice.value = result.fold(
                onSuccess = { PdfNotice("PDF saved to the location you chose.", false) },
                onFailure = { PdfNotice("Couldn't save the PDF: ${it.message ?: "unknown error"}", true) }
            )
        }
    }

    fun reportSaveFailure() {
        _notice.value = PdfNotice("Couldn't open the file picker. Try sharing the PDF instead.", true)
    }

    fun reportShareFailure() {
        _notice.value = PdfNotice("Couldn't open the share sheet. Try saving the PDF instead.", true)
    }

    fun fileMissing() {
        _notice.value = PdfNotice("The report file is no longer available. Generate it again.", true)
    }

    fun clearNotice() { _notice.value = null }
}
