package uk.co.tripassistant.core.text

/**
 * Safe cleanup of recognised text (spec section 15).
 *
 * The contract here is narrow on purpose: a correction is only ever applied to a token that is
 * *already* mostly digits, and the caller re-validates the result. Nothing in this file may ever
 * produce a value that was not on screen — an unreadable number stays unreadable, which is what
 * keeps the pipeline honest (spec section 63).
 */
object TextNormalizer {

    /** Characters OCR routinely swaps for digits when it is reading a numeric field. */
    private val DIGIT_CONFUSIONS = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0',
        'l' to '1', 'I' to '1', '|' to '1', 'i' to '1',
        'S' to '5', 's' to '5',
        'B' to '8',
        'Z' to '2', 'z' to '2',
        'g' to '9'
    )

    /** Collapses whitespace and unifies the handful of symbols that vary between fonts. */
    fun normalizeLine(raw: String): String = raw
        .replace(' ', ' ')  // non-breaking space
        .replace(' ', ' ')  // figure space
        .replace(' ', ' ')  // narrow no-break space
        .replace('’', '\'') // curly apostrophe
        .replace('–', '-')  // en dash
        .replace('—', '-')  // em dash
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Parses a numeric token, applying digit corrections only if the raw token does not parse.
     *
     * Returns the value plus whether a correction was needed, so the caller can record a
     * [uk.co.tripassistant.core.model.ParseNote] for the diagnostics screen.
     */
    fun parseNumber(token: String): NumberParse? {
        val cleaned = token.trim().trim('.', ',')
        if (cleaned.isEmpty()) return null

        // "18,50" is a decimal comma; "1,850" is a thousands separator. Only the first is a number
        // this product ever sees on a UK Uber screen, and only when exactly two digits follow.
        val commaResolved = when {
            Regex("^\\d{1,3},\\d{2}$").matches(cleaned) -> cleaned.replace(',', '.')
            else -> cleaned.replace(",", "")
        }

        commaResolved.toDoubleOrNull()?.let {
            return NumberParse(it, corrected = commaResolved != cleaned)
        }

        // Refuse to "repair" something that was mostly letters — that is guessing, not correcting.
        // The ratio is measured on what OCR actually returned, not on the repaired string, so a
        // token like "OO" can never be talked into becoming 0.
        val realDigits = commaResolved.count { it.isDigit() }
        val numericLooking = commaResolved.count { it.isDigit() || it == '.' }.toDouble()
        if (realDigits == 0 || numericLooking / commaResolved.length < 0.5) return null

        val repaired = commaResolved.map { DIGIT_CONFUSIONS[it] ?: it }.joinToString("")
        return repaired.toDoubleOrNull()?.let { NumberParse(it, corrected = true) }
    }

    data class NumberParse(val value: Double, val corrected: Boolean)
}
