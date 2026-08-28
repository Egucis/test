package uk.co.tripassistant.app.capture

import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Skips frames that have not meaningfully changed (spec section 12).
 *
 * This is the cheapest stage in the pipeline and the one that decides how much work everything
 * after it does. It reads a small grid of pixels straight out of the capture buffer — no bitmap is
 * allocated, no copy is made — and only lets a frame through when enough of that grid moved.
 *
 * A driver's screen is static most of the time, so in practice this turns a 60fps capture into a
 * handful of OCR passes when an offer actually appears.
 */
class FrameChangeDetector(
    private val gridSize: Int = 16,
    /** Per-cell luminance difference that counts as movement, 0-255. */
    private val cellThreshold: Int = 10,
    /** How many cells must move before the frame is worth looking at. */
    private val changedCellsThreshold: Int = 3
) {

    private var previous: IntArray? = null

    /** Forgets the last frame, so the next one always counts as changed. */
    fun reset() {
        previous = null
    }

    /**
     * @param buffer the capture plane; its position is restored before returning.
     * @return true when this frame is different enough to be worth recognising.
     */
    fun hasChanged(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): Boolean {
        if (width <= 0 || height <= 0) return false

        val current = sample(buffer, width, height, rowStride, pixelStride)
        val last = previous
        previous = current

        if (last == null || last.size != current.size) return true

        var moved = 0
        for (i in current.indices) {
            if (abs(current[i] - last[i]) > cellThreshold) {
                moved++
                if (moved > changedCellsThreshold) return true
            }
        }
        return false
    }

    private fun sample(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): IntArray {
        val originalPosition = buffer.position()
        val grid = IntArray(gridSize * gridSize)
        try {
            for (row in 0 until gridSize) {
                val y = (row * height / gridSize).coerceIn(0, height - 1)
                for (column in 0 until gridSize) {
                    val x = (column * width / gridSize).coerceIn(0, width - 1)
                    val offset = y * rowStride + x * pixelStride
                    if (offset + 2 >= buffer.limit()) continue
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    // Integer luminance — close enough to Rec. 601 and free of floating point.
                    grid[row * gridSize + column] = (r * 77 + g * 151 + b * 28) shr 8
                }
            }
        } finally {
            buffer.position(originalPosition)
        }
        return grid
    }
}
