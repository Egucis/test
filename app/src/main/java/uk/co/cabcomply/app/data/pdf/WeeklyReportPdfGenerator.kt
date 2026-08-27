package uk.co.cabcomply.app.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import uk.co.cabcomply.app.R
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_WIDTH = 595 // A4 at 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

private const val COLOR_BRAND = 0xFF0B4F6C.toInt()
private const val COLOR_INK = 0xFF16232B.toInt()
private const val COLOR_MUTED = 0xFF5B6B73.toInt()
private const val COLOR_DEFECT = 0xFFB3261E.toInt()
private const val COLOR_OK = 0xFF1E8E5A.toInt()
private const val COLOR_RULE = 0xFFDDE3E6.toInt()

/**
 * Renders a [WeeklyReportData] as a print-friendly, multi-page A4 PDF with consistent CabComply
 * branding (product spec sections 38-40, 75). Pagination is handled by tracking a running
 * vertical cursor and starting a fresh page whenever the next block would not fit.
 */
@Singleton
class WeeklyReportPdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun generate(report: WeeklyReportData): File {
        val document = PdfDocument()
        val cursor = PageCursor(document)
        cursor.startPage()

        drawHeader(cursor, report)
        drawMetaBlock(cursor, report)
        drawDailyTable(cursor, report)
        drawMileageSummary(cursor, report)
        drawDefects(cursor, report)
        drawFooterNote(cursor)

        cursor.finishPage()

        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val file = File(dir, "cabcomply_weekly_${report.vehicleRegistration}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawHeader(cursor: PageCursor, report: WeeklyReportData) {
        val canvas = cursor.canvas
        val logoSize = 48f
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_cabcomply_logo)
        drawable?.setBounds(
            MARGIN.toInt(),
            cursor.y.toInt(),
            (MARGIN + logoSize).toInt(),
            (cursor.y + logoSize).toInt()
        )
        drawable?.draw(canvas)

        val titlePaint = textPaint(20f, COLOR_INK, bold = true)
        canvas.drawText("CabComply", MARGIN + logoSize + 14f, cursor.y + 22f, titlePaint)
        val subPaint = textPaint(11f, COLOR_MUTED)
        canvas.drawText("Vehicle Daily Check Record — Weekly Report", MARGIN + logoSize + 14f, cursor.y + 40f, subPaint)

        cursor.y += logoSize + 16f
        drawRule(cursor)
        cursor.y += 14f
    }

    private fun drawMetaBlock(cursor: PageCursor, report: WeeklyReportData) {
        val labelPaint = textPaint(10f, COLOR_MUTED)
        val valuePaint = textPaint(12f, COLOR_INK, bold = true)

        val leftX = MARGIN
        val rightX = MARGIN + CONTENT_WIDTH / 2

        fun row(x: Float, y: Float, label: String, value: String) {
            cursor.canvas.drawText(label, x, y, labelPaint)
            cursor.canvas.drawText(value, x, y + 15f, valuePaint)
        }

        row(leftX, cursor.y, "Vehicle", "${report.vehicleRegistration} · ${report.vehicleMakeModel}")
        row(rightX, cursor.y, "Driver", report.driverName)
        cursor.y += 34f
        row(leftX, cursor.y, "Licensing authority", report.licensingAuthorityName ?: "Not set")
        row(rightX, cursor.y, "Week", "${report.weekStartLabel} – ${report.weekEndLabel}")
        cursor.y += 34f

        drawRule(cursor)
        cursor.y += 16f
    }

    private fun drawDailyTable(cursor: PageCursor, report: WeeklyReportData) {
        cursor.ensureSpace(24f)
        cursor.canvas.drawText("Daily checks", MARGIN, cursor.y, textPaint(13f, COLOR_INK, bold = true))
        cursor.y += 18f

        val colDay = MARGIN
        val colStatus = MARGIN + 110f
        val colTime = MARGIN + 250f
        val colOdo = MARGIN + 330f
        val colDefect = MARGIN + 430f

        val headerPaint = textPaint(9.5f, COLOR_MUTED, bold = true)
        cursor.ensureSpace(20f)
        cursor.canvas.drawText("DAY", colDay, cursor.y, headerPaint)
        cursor.canvas.drawText("STATUS", colStatus, cursor.y, headerPaint)
        cursor.canvas.drawText("TIME", colTime, cursor.y, headerPaint)
        cursor.canvas.drawText("ODOMETER", colOdo, cursor.y, headerPaint)
        cursor.canvas.drawText("DEFECT", colDefect, cursor.y, headerPaint)
        cursor.y += 6f
        drawRule(cursor)
        cursor.y += 16f

        val bodyPaint = textPaint(11f, COLOR_INK)
        report.days.forEach { day ->
            cursor.ensureSpace(20f)
            cursor.canvas.drawText("${day.dayOfWeekLabel} ${day.dateLabel}", colDay, cursor.y, bodyPaint)

            val statusText = if (day.completed) "Completed" else "Not completed"
            val statusPaint = textPaint(11f, if (day.completed) COLOR_OK else COLOR_MUTED, bold = day.completed)
            cursor.canvas.drawText(
                statusText + if (day.isQuickCheck) " (Quick Check)" else "",
                colStatus,
                cursor.y,
                statusPaint
            )

            cursor.canvas.drawText(day.completionTimeLabel ?: "—", colTime, cursor.y, bodyPaint)
            cursor.canvas.drawText(day.odometer?.toString() ?: "—", colOdo, cursor.y, bodyPaint)
            if (day.hasDefect) {
                cursor.canvas.drawText("Yes", colDefect, cursor.y, textPaint(11f, COLOR_DEFECT, bold = true))
            } else {
                cursor.canvas.drawText("—", colDefect, cursor.y, bodyPaint)
            }
            cursor.y += 20f
        }
        cursor.y += 6f
        drawRule(cursor)
        cursor.y += 16f
    }

    private fun drawMileageSummary(cursor: PageCursor, report: WeeklyReportData) {
        cursor.ensureSpace(40f)
        cursor.canvas.drawText("Mileage this week", MARGIN, cursor.y, textPaint(13f, COLOR_INK, bold = true))
        cursor.y += 18f
        val bodyPaint = textPaint(11f, COLOR_INK)
        cursor.canvas.drawText(
            "Total: ${report.mileageTotalMiles} miles   •   Business: ${report.mileageBusinessMiles} miles",
            MARGIN,
            cursor.y,
            bodyPaint
        )
        cursor.y += 24f
        drawRule(cursor)
        cursor.y += 16f
    }

    private fun drawDefects(cursor: PageCursor, report: WeeklyReportData) {
        cursor.ensureSpace(24f)
        cursor.canvas.drawText(
            if (report.defects.isEmpty()) "No defects recorded this week" else "Defects recorded this week",
            MARGIN,
            cursor.y,
            textPaint(13f, COLOR_INK, bold = true)
        )
        cursor.y += 18f

        report.defects.forEach { defect ->
            val headerPaint = textPaint(11f, COLOR_INK, bold = true)
            val header = "${defect.dateLabel} · ${defect.checklistItem} · ${defect.statusLabel}"
            val headerLayout = buildLayout(header, headerPaint, CONTENT_WIDTH)
            val bodyLayout = buildLayout(defect.description, textPaint(11f, COLOR_MUTED), CONTENT_WIDTH)
            val blockHeight = headerLayout.height + bodyLayout.height + 16f

            cursor.ensureSpace(blockHeight)
            cursor.canvas.save()
            cursor.canvas.translate(MARGIN, cursor.y)
            headerLayout.draw(cursor.canvas)
            cursor.canvas.translate(0f, headerLayout.height.toFloat() + 2f)
            bodyLayout.draw(cursor.canvas)
            cursor.canvas.restore()
            cursor.y += blockHeight
        }
    }

    private fun drawFooterNote(cursor: PageCursor) {
        cursor.ensureSpace(40f)
        val notePaint = textPaint(8.5f, COLOR_MUTED)
        val layout = buildLayout(
            "This record was created by the driver using CabComply and reflects the driver's own inspection. " +
                "CabComply is an independent record-keeping tool and does not itself guarantee legal or licensing compliance.",
            notePaint,
            CONTENT_WIDTH
        )
        cursor.ensureSpace(layout.height.toFloat())
        cursor.canvas.save()
        cursor.canvas.translate(MARGIN, cursor.y)
        layout.draw(cursor.canvas)
        cursor.canvas.restore()
        cursor.y += layout.height
    }

    private fun drawRule(cursor: PageCursor) {
        val paint = Paint().apply { color = COLOR_RULE; strokeWidth = 1f }
        cursor.canvas.drawLine(MARGIN, cursor.y, PAGE_WIDTH - MARGIN, cursor.y, paint)
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()

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

        /** Ensures at least [heightNeeded] points remain below [y]; otherwise starts a new page first. */
        fun ensureSpace(heightNeeded: Float) {
            if (y + heightNeeded > PAGE_HEIGHT - MARGIN) {
                finishPage()
                startPage()
            }
        }
    }
}
