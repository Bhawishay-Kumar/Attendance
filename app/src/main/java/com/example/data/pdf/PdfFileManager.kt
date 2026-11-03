package com.example.data.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Cache, save and share handling for generated reports. The single PDF written to
 * `cache/reports/` is the file the preview shows, the file that is saved and the file that is
 * shared, so all three are always identical. No storage permission is needed: saving goes
 * through the system file picker (the user picks the location and nothing is overwritten
 * silently) and sharing goes through a read-only FileProvider grant.
 */
object PdfFileManager {

    private const val DIR = "reports"
    private fun authority(context: Context) = "${context.packageName}.reports.fileprovider"

    fun reportDir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /** Deletes previously generated reports so the cache never grows. */
    fun clear(context: Context) {
        reportDir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** A fresh, empty target file inside the report cache. */
    fun newTarget(context: Context, fileName: String): File {
        clear(context)
        return File(reportDir(context), fileName)
    }

    /** Copies the generated PDF to a document the user chose via the system picker. */
    fun saveTo(context: Context, source: File, destination: Uri): Result<Unit> = runCatching {
        val out = context.contentResolver.openOutputStream(destination, "w")
            ?: error("Couldn't open the chosen location for writing.")
        out.use { o -> source.inputStream().use { it.copyTo(o) } }
    }

    /** Share-sheet intent for the generated PDF, with a temporary read grant. */
    fun shareIntent(context: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share attendance report").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
