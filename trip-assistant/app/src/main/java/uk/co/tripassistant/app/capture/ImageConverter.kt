package uk.co.tripassistant.app.capture

import android.graphics.Bitmap
import android.media.Image

/**
 * Turns one captured [Image] into a bitmap OCR can read.
 *
 * MediaProjection hands back RGBA_8888 rows that are padded out to a hardware-friendly stride, so
 * the buffer is wider than the screen. Copying it straight into a bitmap of the screen's width
 * produces the familiar diagonal skew; the padding has to be accounted for and then cropped away.
 */
object ImageConverter {

    fun toBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null

        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        if (paddedWidth <= 0 || image.height <= 0) return null

        buffer.rewind()
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)

        if (rowPadding == 0) return padded

        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }
}
