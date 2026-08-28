package uk.co.tripassistant.core.text

/**
 * A rectangle in *normalised* screen coordinates: 0.0 is the left/top edge of the captured frame,
 * 1.0 the right/bottom edge.
 *
 * Everything downstream of OCR works in these units, never in pixels, so a parser written against
 * one phone keeps working on a different resolution, a different display scale or a different
 * font size (spec sections 7 and 13).
 */
data class Rect01(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun containsPoint(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun contains(other: Rect01): Boolean = containsPoint(other.centerX, other.centerY)

    fun expandedBy(margin: Float): Rect01 = Rect01(
        left = left - margin,
        top = top - margin,
        right = right + margin,
        bottom = bottom + margin
    )

    companion object {
        val FULL = Rect01(0f, 0f, 1f, 1f)
    }
}

/** One line of recognised text and where it sat on screen. */
data class TextLine(
    val text: String,
    val box: Rect01
) {
    /** Line height is the only reliable proxy for "how prominent is this?" across devices. */
    val prominence: Float get() = box.height
}

/**
 * Everything OCR found on one captured frame.
 *
 * This is the boundary between the Android side and the decision logic: the ML Kit result is
 * converted into this and never travels further, which is what lets the whole parser suite run as
 * plain JVM unit tests (spec sections 55 and 62).
 */
data class OcrText(val lines: List<TextLine>) {

    /** All lines joined top-to-bottom — handy for keyword checks and diagnostics. */
    val joined: String by lazy { readingOrder().joinToString("\n") { it.text } }

    fun readingOrder(): List<TextLine> = lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))

    /**
     * Drops anything sitting inside [regions].
     *
     * Used to cut the app's own floating overlay out of a whole-screen capture so it never reads
     * its own figures back and evaluates them as a new offer (spec section 27).
     */
    fun excluding(regions: List<Rect01>): OcrText {
        if (regions.isEmpty()) return this
        return OcrText(lines.filterNot { line -> regions.any { it.contains(line.box) } })
    }

    fun containsIgnoreCase(needle: String): Boolean = joined.contains(needle, ignoreCase = true)

    fun isEmpty(): Boolean = lines.isEmpty()

    companion object {
        val EMPTY = OcrText(emptyList())

        /**
         * Builds an [OcrText] from plain lines, spacing them evenly down the frame.
         * Only used by tests and the rule tester — the live path always has real boxes.
         */
        fun ofLines(vararg lines: String): OcrText = OcrText(
            lines.mapIndexed { index, text ->
                val top = 0.1f + index * 0.05f
                TextLine(text, Rect01(0.05f, top, 0.95f, top + 0.04f))
            }
        )
    }
}
