package com.example.data.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Draws a [PdfReportData] into a genuine multi-page PDF using the framework [PdfDocument]
 * (no extra dependency). The page is always white with print-friendly colours, whatever theme the
 * app is using. Rows are never split across pages and table headers repeat on every page.
 *
 * Page numbering ("Page X of Y") needs the page count up front, so the document is laid out
 * twice: a throw-away dry run to count pages, then the real run written to [render]'s file.
 */
object PdfReportRenderer {

    // A4 in PostScript points.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val FOOTER_Y = PAGE_H - 28f
    private const val BOTTOM = PAGE_H - 54f

    private const val NAVY = 0xFF0B1F3A.toInt()
    private const val TEAL = 0xFF0F8B8D.toInt()
    private const val TEAL_SOFT = 0xFFE6F4F4.toInt()
    private const val GREY = 0xFF64748B.toInt()
    private const val INK = 0xFF1E293B.toInt()
    private const val RULE = 0xFFE2E8F0.toInt()
    private const val BAND = 0xFFF1F5F9.toInt()
    private const val RED = 0xFFB91C1C.toInt()
    private const val AMBER = 0xFFB45309.toInt()

    /** Writes the PDF to [outFile] and returns the number of pages. Throws on any failure. */
    fun render(data: PdfReportData, outFile: File): Int {
        val pages = draw(data, totalPages = 0, sink = null)
        draw(data, totalPages = pages, sink = outFile)
        return pages
    }

    private fun draw(data: PdfReportData, totalPages: Int, sink: File?): Int {
        val doc = PdfDocument()
        try {
            val w = Writer(doc, data, totalPages)
            w.newPage()
            w.firstPageHeader()
            w.summary()
            if (data.detailRows != null) w.details(data.detailRows)
            w.finishPage()
            if (sink != null) {
                FileOutputStream(sink).use { doc.writeTo(it) }
            }
            return w.pageNo
        } finally {
            doc.close()
        }
    }

    private class Writer(val doc: PdfDocument, val data: PdfReportData, val totalPages: Int) {
        var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var c: Canvas
        var y = MARGIN

        private fun tp(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
        private fun stroke(color: Int, w: Float = 0.8f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = w }

        private fun layout(text: String, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(align)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build()

        /** Draws wrapped text with its top-left at (x, top) and returns its height. */
        private fun text(
            s: String, x: Float, top: Float, width: Float, paint: TextPaint,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        ): Float {
            val l = layout(s, paint, width, align)
            c.save(); c.translate(x, top); l.draw(c); c.restore()
            return l.height.toFloat()
        }

        private fun measure(s: String, width: Float, paint: TextPaint): Float = layout(s, paint, width).height.toFloat()

        fun newPage() {
            finishPage()
            pageNo++
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            val p = doc.startPage(info)
            page = p
            c = p.canvas
            c.drawColor(0xFFFFFFFF.toInt())
            y = MARGIN
            // Footer on every page.
            c.drawLine(MARGIN, FOOTER_Y - 10f, PAGE_W - MARGIN, FOOTER_Y - 10f, stroke(RULE))
            val fp = tp(8f, GREY)
            c.drawText("${data.studentName}  ·  Generated ${data.generatedOn}", MARGIN, FOOTER_Y, fp)
            val label = if (totalPages > 0) "Page $pageNo of $totalPages" else "Page $pageNo"
            c.drawText(label, PAGE_W - MARGIN - fp.measureText(label), FOOTER_Y, fp)
            if (pageNo > 1) {
                // Slim running header on continuation pages.
                c.drawText("Attendance Report  ·  ${data.periodLabel}", MARGIN, y + 8f, tp(9f, NAVY, true))
                c.drawLine(MARGIN, y + 16f, PAGE_W - MARGIN, y + 16f, stroke(RULE))
                y += 30f
            }
        }

        fun finishPage() {
            page?.let { doc.finishPage(it) }
            page = null
        }

        private fun ensure(h: Float, repeatHeader: (() -> Unit)? = null) {
            if (y + h > BOTTOM) {
                newPage()
                repeatHeader?.invoke()
            }
        }

        // ------------------------------------------------------------------ page 1

        fun firstPageHeader() {
            c.drawText("Attendance Report", MARGIN, y + 18f, tp(22f, NAVY, true))
            val kind = if (data.reportType == PdfReportType.SUMMARY) "SUMMARY REPORT" else "DETAILED REPORT"
            val kp = tp(9f, TEAL, true)
            c.drawText(kind, PAGE_W - MARGIN - kp.measureText(kind), y + 16f, kp)
            y += 28f
            c.drawRect(MARGIN, y, MARGIN + 46f, y + 2.5f, fill(TEAL))
            c.drawLine(MARGIN + 46f, y + 1f, PAGE_W - MARGIN, y + 1f, stroke(RULE))
            y += 14f

            // Student block (left) and period block (right).
            val colW = CONTENT_W / 2f - 8f
            var ly = y
            ly += text(data.studentName, MARGIN, ly, colW, tp(14f, NAVY, true)) + 3f
            data.studentDetails.forEach { (k, v) ->
                ly += text("$k: $v", MARGIN, ly, colW, tp(8.8f, GREY)) + 1.5f
            }
            var ry = y
            val rx = MARGIN + CONTENT_W / 2f + 8f
            ry += text("REPORT PERIOD", rx, ry, colW, tp(7.5f, GREY, true)) + 2f
            ry += text(data.periodLabel, rx, ry, colW, tp(11f, NAVY, true)) + 2f
            data.toDateNote?.let { ry += text(it, rx, ry, colW, tp(8.8f, TEAL, true)) + 2f }
            ry += text("Generated ${data.generatedOn}", rx, ry, colW, tp(8.8f, GREY))
            y = maxOf(ly, ry) + 14f
        }

        fun summary() {
            // Hero card: big percentage + credit totals.
            val heroH = 78f
            c.drawRoundRect(MARGIN, y, MARGIN + CONTENT_W, y + heroH, 10f, 10f, fill(TEAL_SOFT))
            c.drawRoundRect(MARGIN, y, MARGIN + CONTENT_W, y + heroH, 10f, 10f, stroke(0xFFBFE3E3.toInt()))
            val pctColor = if (data.meetsExamTarget) TEAL else AMBER
            c.drawText("OVERALL ATTENDANCE", MARGIN + 16f, y + 20f, tp(7.5f, GREY, true))
            c.drawText(data.percentText, MARGIN + 16f, y + 58f, tp(36f, pctColor, true))
            val rx = MARGIN + CONTENT_W * 0.58f
            c.drawLine(rx - 14f, y + 14f, rx - 14f, y + heroH - 14f, stroke(0xFFBFE3E3.toInt()))
            c.drawText("ATTENDED CREDIT HOURS", rx, y + 22f, tp(7.5f, GREY, true))
            c.drawText("${data.attendedCredits}", rx, y + 40f, tp(16f, NAVY, true))
            c.drawText("CONDUCTED CREDIT HOURS", rx + 118f, y + 22f, tp(7.5f, GREY, true))
            c.drawText("${data.conductedCredits}", rx + 118f, y + 40f, tp(16f, NAVY, true))
            y += heroH + 12f

            // Target cards.
            val gap = 10f
            val cardW = (CONTENT_W - gap) / 2f
            val cardTop = y
            var cardH = 0f
            data.targets.forEachIndexed { i, t ->
                val h = 22f + measure(t.detail, cardW - 24f, tp(8.5f, GREY)) + 22f
                cardH = maxOf(cardH, h)
            }
            data.targets.forEachIndexed { i, t ->
                val x = MARGIN + i * (cardW + gap)
                c.drawRoundRect(x, cardTop, x + cardW, cardTop + cardH, 8f, 8f, stroke(RULE, 1f))
                val col = if (t.met) TEAL else AMBER
                c.drawText("${t.label.uppercase()} · ${t.targetPercent}%", x + 12f, cardTop + 17f, tp(7.5f, GREY, true))
                val badge = if (t.met) "Met" else "Not met"
                val bp = tp(8.5f, col, true)
                c.drawText(badge, x + cardW - 12f - bp.measureText(badge), cardTop + 17f, bp)
                text(t.detail, x + 12f, cardTop + 26f, cardW - 24f, tp(8.5f, INK))
            }
            y = cardTop + cardH + 10f

            // Concise assessment.
            y += text(data.assessment, MARGIN, y, CONTENT_W, tp(9.5f, NAVY, true)) + 14f

            // Subject table.
            c.drawText("Subject-wise attendance", MARGIN, y + 10f, tp(11f, NAVY, true))
            y += 20f
            val cols = floatArrayOf(CONTENT_W - 270f, 80f, 90f, 100f)
            val head = listOf("Subject", "Attended", "Conducted", "Attendance")
            fun tableHead() = tableHeader(cols, head, rightFrom = 1)
            ensure(24f)
            tableHead()
            data.subjects.forEachIndexed { i, s ->
                val name = if (s.isAutoCredited) "${s.name} (auto-credited)" else s.name
                val np = tp(9f, INK)
                val h = maxOf(measure(name, cols[0] - 14f, np), 11f) + 12f
                ensure(h, ::tableHead)
                if (i % 2 == 1) c.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + h, fill(0xFFF8FAFC.toInt()))
                text(name, MARGIN + 8f, y + 6f, cols[0] - 14f, np)
                var x = MARGIN + cols[0]
                listOf(s.attendedCredits.toString(), s.conductedCredits.toString()).forEachIndexed { k, v ->
                    text(v, x, y + 6f, cols[k + 1] - 8f, tp(9f, INK), Layout.Alignment.ALIGN_OPPOSITE)
                    x += cols[k + 1]
                }
                val pc = if (s.meetsExamTarget) TEAL else AMBER
                text(s.percentText, x, y + 6f, cols[3] - 8f, tp(9f, pc, true), Layout.Alignment.ALIGN_OPPOSITE)
                c.drawLine(MARGIN, y + h, MARGIN + CONTENT_W, y + h, stroke(RULE, 0.5f))
                y += h
            }
            y += 12f

            // Footnotes.
            data.footnotes.forEach {
                val fp = tp(8f, GREY)
                val h = measure("• $it", CONTENT_W, fp)
                ensure(h + 3f)
                y += text("• $it", MARGIN, y, CONTENT_W, fp) + 3f
            }
        }

        // ------------------------------------------------------------------ detail pages

        private fun tableHeader(cols: FloatArray, head: List<String>, rightFrom: Int) {
            val h = 20f
            c.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + h, fill(BAND))
            var x = MARGIN
            head.forEachIndexed { i, s ->
                val p = tp(8f, NAVY, true)
                if (i >= rightFrom) c.drawText(s, x + cols[i] - 8f - p.measureText(s), y + 13.5f, p)
                else c.drawText(s, x + 8f, y + 13.5f, p)
                x += cols[i]
            }
            y += h
        }

        fun details(rows: List<PdfDetailRow>) {
            newPage()
            c.drawText("Date-wise attendance records", MARGIN, y + 12f, tp(13f, NAVY, true))
            y += 22f
            y += text(
                "Only classes marked Present or Absent are listed, in date order. No Class and holidays are shown for context and do not count.",
                MARGIN, y, CONTENT_W, tp(8.5f, GREY)
            ) + 10f

            val cols = floatArrayOf(150f, CONTENT_W - 150f - 110f, 110f)
            val head = listOf("Date", "Subject", "Status")
            fun tableHead() = tableHeader(cols, head, rightFrom = 99)
            ensure(24f)
            tableHead()
            rows.forEachIndexed { i, r ->
                val sp = tp(9f, INK)
                val h = maxOf(measure(r.subject, cols[1] - 14f, sp), 11f) + 10f
                ensure(h, ::tableHead)
                if (i % 2 == 1) c.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + h, fill(0xFFF8FAFC.toInt()))
                text(r.dateLabel, MARGIN + 8f, y + 5f, cols[0] - 10f, tp(9f, INK))
                text(r.subject, MARGIN + cols[0] + 8f, y + 5f, cols[1] - 14f, sp)
                val sx = MARGIN + cols[0] + cols[1] + 8f
                val color = when (r.status) {
                    PdfDetailStatus.PRESENT -> TEAL
                    PdfDetailStatus.ABSENT -> RED
                    else -> GREY
                }
                c.drawCircle(sx + 3f, y + 5f + 6.2f, 3f, fill(color))
                text(r.status.label, sx + 12f, y + 5f, cols[2] - 20f, tp(9f, color, true))
                c.drawLine(MARGIN, y + h, MARGIN + CONTENT_W, y + h, stroke(RULE, 0.5f))
                y += h
            }
        }
    }
}
