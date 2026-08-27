package uk.co.cabcomply.app.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** Rasterises a generated PDF's pages to bitmaps so they can be shown inline in Compose, without
 *  a separate PDF viewer app. Shared by the Weekly Report screen and Officer Mode. */
object PdfPageRenderer {
    fun renderPages(file: File, targetWidthPx: Int = 1080): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
            }
        }
        return bitmaps
    }
}
