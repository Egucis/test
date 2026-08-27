package uk.co.cabcomply.app.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import uk.co.cabcomply.app.R
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_WIDTH = 595 // A4 at 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 28f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

private const val COLOR_BRAND = 0xFF0B4F6C.toInt()
private const val COLOR_INK = 0xFF16232B.toInt()
private const val COLOR_MUTED = 0xFF5B6B73.toInt()
private const val COLOR_DEFECT = 0xFFB3261E.toInt()
private const val COLOR_OK = 0xFF1E8E5A.toInt()
private const val COLOR_RULE = 0xFFB9C2C6.toInt()

/**
 * Renders a [WeeklyReportData] as a print-friendly, multi-page A4 PDF: an item-by-item matrix
 * (every checklist item as a row, every day as a column) with CabComply branding, matching the
 * format a licensing officer expects to see on paper (product spec sections 38-40, 75).
 * Pagination re-draws the day-header row at the top of every continuation page.
 */
@Singleton
class WeeklyReportPdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val itemColWidth = 180f
    private val dayColWidth = (CONTENT_WIDTH - itemColWidth) / 7f
    private val matrixHeaderHeight = 42f
    private val matrixRowHeight = 13.5f

    fun generate(report: WeeklyReportData): File {
        val document = PdfDocument()
        val cursor = PageCursor(document)
        cursor.startPage()

        drawHeader(cursor, report)
        drawMetaGrid(cursor, report)
        drawMatrix(cursor, report)
        drawFooterSections(cursor, report)
        drawLegend(cursor, report)

        cursor.finishPage()

        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val file = File(dir, "cabcomply_weekly_${report.vehicleRegistration}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawHeader(cursor: PageCursor, report: WeeklyReportData) {
        val canvas = cursor.canvas
        val logoSize = 34f
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_cabcomply_logo)
        drawable?.setBounds(MARGIN.toInt(), cursor.y.toInt(), (MARGIN + logoSize).toInt(), (cursor.y + logoSize).toInt())
        drawable?.draw(canvas)

        canvas.drawText("CabComply", MARGIN + logoSize + 10f, cursor.y + 15f, textPaint(13f, COLOR_INK, bold = true))
        canvas.drawText("Digital compliance record", MARGIN + logoSize + 10f, cursor.y + 28f, textPaint(8f, COLOR_MUTED))

        val titlePaint = textPaint(15f, COLOR_INK, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Daily Vehicle Check List & Defect Report", PAGE_WIDTH - MARGIN, cursor.y + 20f, titlePaint)

        cursor.y += logoSize + 12f
        drawRule(cursor)
        cursor.y += 12f
    }

    private fun drawMetaGrid(cursor: PageCursor, report: WeeklyReportData) {
        val rowHeight = 30f
        val colWidth = CONTENT_WIDTH / 2
        cursor.ensureSpace(rowHeight * 2)
        val top = cursor.y

        fun cell(col: Int, row: Int, label: String, value: String) {
            val x = MARGIN + col * colWidth
            val y = top + row * rowHeight
            cursor.canvas.drawRect(x, y, x + colWidth, y + rowHeight, gridStrokePaint())
            cursor.canvas.drawText(label, x + 8f, y + 12f, textPaint(8f, COLOR_MUTED, bold = true))
            cursor.canvas.drawText(value, x + 8f, y + 24f, textPaint(10.5f, COLOR_INK, bold = true))
        }

        cell(0, 0, "Vehicle Registration No", report.vehicleRegistration)
        cell(1, 0, "Vehicle Make / Model", report.vehicleMakeModel)
        cell(0, 1, "Driver's Name", report.driverName)
        cell(1, 1, "Week starting", report.weekStartLabel)

        cursor.y = top + rowHeight * 2 + 12f
    }

    private fun drawMatrixHeaderRow(cursor: PageCursor, report: WeeklyReportData) {
        cursor.ensureSpace(matrixHeaderHeight)
        val top = cursor.y
        var x = MARGIN
        cursor.canvas.drawRect(x, top, x + itemColWidth, top + matrixHeaderHeight, gridStrokePaint())
        x += itemColWidth
        report.dayHeaders.forEach { day ->
            cursor.canvas.drawRect(x, top, x + dayColWidth, top + matrixHeaderHeight, gridStrokePaint())
            val centerX = x + dayColWidth / 2
            drawCentered(cursor.canvas, day.dayLetter, centerX, top + 12f, textPaint(9f, COLOR_INK, bold = true))
            drawCentered(cursor.canvas, day.dateLabel, centerX, top + 22f, textPaint(6.5f, COLOR_MUTED))
            drawCentered(cursor.canvas, day.timeLabel ?: "", centerX, top + 31f, textPaint(6.5f, COLOR_MUTED))
            drawCentered(cursor.canvas, day.odometerLabel ?: "", centerX, top + 40f, textPaint(6.5f, COLOR_MUTED))
            x += dayColWidth
        }
        cursor.y = top + matrixHeaderHeight
    }

    private fun drawMatrix(cursor: PageCursor, report: WeeklyReportData) {
        drawMatrixHeaderRow(cursor, report)

        val nameTextPaint = textPaint(7.5f, COLOR_INK)
        report.itemRows.forEach { row ->
            if (cursor.y + matrixRowHeight > PAGE_HEIGHT - MARGIN) {
                cursor.finishPage()
                cursor.startPage()
                drawMatrixHeaderRow(cursor, report)
            }
            val top = cursor.y
            var x = MARGIN
            cursor.canvas.drawRect(x, top, x + itemColWidth, top + matrixRowHeight, gridStrokePaint())
            val label = ellipsize(row.itemName, nameTextPaint, itemColWidth - 8f)
            cursor.canvas.drawText(label, x + 4f, top + matrixRowHeight - 3.5f, nameTextPaint)
            x += itemColWidth

            row.statuses.forEach { status ->
                cursor.canvas.drawRect(x, top, x + dayColWidth, top + matrixRowHeight, gridStrokePaint())
                val centerX = x + dayColWidth / 2
                val baselineY = top + matrixRowHeight - 3.5f
                when (status) {
                    ItemDayStatus.OK -> drawCentered(cursor.canvas, "✓", centerX, baselineY, textPaint(9f, COLOR_OK, bold = true))
                    ItemDayStatus.DEFECT -> drawCentered(cursor.canvas, "X", centerX, baselineY, textPaint(9f, COLOR_DEFECT, bold = true))
                    ItemDayStatus.NOT_APPLICABLE -> drawCentered(cursor.canvas, "N/A", centerX, baselineY, textPaint(6.5f, COLOR_MUTED))
                    ItemDayStatus.NOT_RECORDED -> Unit
                }
                x += dayColWidth
            }
            cursor.y = top + matrixRowHeight
        }
        cursor.y += 10f
    }

    private fun drawFooterSections(cursor: PageCursor, report: WeeklyReportData) {
        val boxHeight = 110f
        cursor.ensureSpace(boxHeight)
        val top = cursor.y
        val colWidth = CONTENT_WIDTH / 3

        cursor.canvas.drawRect(MARGIN, top, MARGIN + CONTENT_WIDTH, top + boxHeight, gridStrokePaint())
        cursor.canvas.drawLine(MARGIN + colWidth, top, MARGIN + colWidth, top + boxHeight, gridStrokePaint())
        cursor.canvas.drawLine(MARGIN + colWidth * 2, top, MARGIN + colWidth * 2, top + boxHeight, gridStrokePaint())

        val openDefects = report.defects.filter { it.statusLabel == "Open" }
        val resolvedDefects = report.defects.filter { it.statusLabel == "Resolved" }

        drawWrappedColumn(
            cursor.canvas, "Report defects here", MARGIN + 8f, top + 6f, colWidth - 16f,
            if (openDefects.isEmpty() && report.defects.isEmpty()) "No defects recorded." else
                openDefects.joinToString("\n") { "${it.dateLabel} · ${it.checklistItem}: ${it.description}" }
                    .ifBlank { "No open defects — all recorded defects resolved." }
        )
        drawWrappedColumn(
            cursor.canvas, "Rectified", MARGIN + colWidth + 8f, top + 6f, colWidth - 16f,
            if (resolvedDefects.isEmpty()) "No defects required rectification." else
                resolvedDefects.joinToString("\n") { "${it.dateLabel} · ${it.checklistItem}${it.resolutionNote?.let { n -> ": $n" } ?: ""}" }
        )
        drawWrappedColumn(
            cursor.canvas, "Driver's signature", MARGIN + colWidth * 2 + 8f, top + 6f, colWidth - 16f,
            if (report.driverSignatures.isEmpty()) "No checks completed this week." else report.driverSignatures.joinToString("\n")
        )

        cursor.y = top + boxHeight + 14f
    }

    private fun drawLegend(cursor: PageCursor, report: WeeklyReportData) {
        cursor.ensureSpace(24f)
        cursor.canvas.drawText(
            "Legend: ✓ = OK   X = defect   N/A = not applicable   blank = no digital record for that day",
            MARGIN, cursor.y, textPaint(7f, COLOR_MUTED)
        )
        cursor.y += 12f
        cursor.canvas.drawText("Generated ${report.generatedAtLabel}", MARGIN, cursor.y, textPaint(7f, COLOR_MUTED))
    }

    private fun drawWrappedColumn(canvas: Canvas, title: String, x: Float, y: Float, width: Float, body: String) {
        canvas.drawText(title, x, y + 8f, textPaint(8f, COLOR_INK, bold = true))
        val layout = buildLayout(body, textPaint(7f, COLOR_MUTED), width)
        canvas.save()
        canvas.translate(x, y + 16f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawRule(cursor: PageCursor) {
        cursor.canvas.drawLine(MARGIN, cursor.y, PAGE_WIDTH - MARGIN, cursor.y, gridStrokePaint())
    }

    private fun drawCentered(canvas: Canvas, text: String, centerX: Float, baselineY: Float, paint: Paint) {
        val align = paint.textAlign
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, centerX, baselineY, paint)
        paint.textAlign = align
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    private fun buildLayout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()

    private fun gridStrokePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_RULE
        style = Paint.Style.STROKE
        strokeWidth = 0.75f
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            isFakeBoldText = bold
        }

    /** Tracks the current PDF page/canvas and starts a fresh page automatically when content runs out of room. */
    private inner class PageCursor(private val document: PdfDocument) {
        var y: Float = MARGIN
        lateinit var canvas: Canvas
            private set
        private var currentPage: PdfDocument.Page? = null
        private var pageNumber = 0

        fun startPage() {
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage!!.canvas
            y = MARGIN
        }

        fun finishPage() {
            currentPage?.let { document.finishPage(it) }
        }

        fun ensureSpace(heightNeeded: Float) {
            if (y + heightNeeded > PAGE_HEIGHT - MARGIN) {
                finishPage()
                startPage()
            }
        }
    }
}
