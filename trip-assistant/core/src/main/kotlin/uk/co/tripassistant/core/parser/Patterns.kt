package uk.co.tripassistant.core.parser

/**
 * The regular expressions the Uber parsers are built from (spec section 13).
 *
 * Numbers are matched with a character class that also contains the letters OCR habitually
 * substitutes for digits, so "1O.5O" is *matched* here and then either repaired and re-validated
 * by [uk.co.tripassistant.core.text.TextNormalizer] or thrown away. Matching loosely and
 * validating strictly is what lets the parser cope with imperfect recognition without ever
 * inventing a value.
 */
internal object Patterns {

    /** Digits plus their common OCR look-alikes. */
    private const val D = "[0-9OoQDlIi|SsBZzg]"

    /** A decimal number: digits, optionally a decimal separator and one or two more digits. */
    private const val DEC = "$D+(?:[.,]$D{1,2})?"

    val CURRENCY = Regex("£\\s*($DEC)")

    val DISTANCE = Regex(
        "($DEC)\\s*(miles|mile|mi|kilometres|kilometre|kms|km)\\b",
        RegexOption.IGNORE_CASE
    )

    val DURATION = Regex(
        "($D+)\\s*(minutes|minute|mins|min|hours|hour|hrs|hr)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * A line that is nothing but a rating, allowing a few characters of decoration either side:
     * "4.91", "★ 4.91", "* 4.91" — and, importantly, whatever OCR makes of a star glyph it does
     * not recognise. Uber puts the rating on a line of its own, so a rating-shaped number sitting
     * alone is the rating; being strict about the star meant a mis-read glyph downgraded every
     * offer to "partly read".
     */
    val RATING_STANDALONE_LINE = Regex("^[^0-9]{0,3}([1-5][.,]\\d{1,2})[^0-9]{0,3}$")

    val RATING_INLINE = Regex("\\b([1-5][.,]\\d{1,2})\\b")

    val STAR = Regex("[★☆⭐✩]")

    /** Words that mean "this number describes getting to the rider". */
    val PICKUP_KEYWORDS = listOf("away", "pickup", "pick up", "pick-up", "to rider", "to pickup")

    /** Words that mean "this number describes the paid journey". */
    val TRIP_KEYWORDS = listOf("trip", "dropoff", "drop off", "drop-off", "destination", "journey", "to destination")

    /** Amounts sitting next to these words are not the offered fare. */
    val FARE_EXCLUSION_KEYWORDS = listOf(
        // UK cards show a breakdown line under the fare: "£8.85 + est. holiday pay of £0.19".
        // Both amounts on it are components of the headline fare, not the fare itself.
        "holiday pay", "est.",
        "bonus", "promo", "promotion", "tip", "boost", "surge", "quest", "incentive",
        "per hour", "/hr", "/h", "per mile", "/mi", "extra", "included", "includes",
        "total earnings", "today", "week", "balance"
    )

    /** Words that make a screen look like a trip offer. */
    val OFFER_KEYWORDS = listOf(
        // "confirm" and "let's go" are the buttons on current UK cards; "accept" is kept for
        // older layouts and other markets.
        "accept", "confirm", "let's go", "lets go", "match", "away", "trip",
        "uberx", "uber x", "comfort", "uber green", "green",
        "xl", "exclusive", "pet", "assist", "share", "reserve", "delivery", "premier",
        "verified", "surge", "guaranteed", "rider", "passenger"
    )

    /** Words that mean the driver is somewhere else in the Uber app entirely. */
    val NON_OFFER_KEYWORDS = listOf(
        "navigate", "start trip", "end trip", "complete trip", "arrived", "you're offline",
        "you are offline", "go online", "earnings", "wallet", "account", "settings", "inbox",
        "opportunities", "vehicle", "help", "safety toolkit"
    )

    /** A line carrying money is never a rating — "£4.62" must not become a 4.62 rating. */
    fun containsCurrency(line: String): Boolean =
        line.any { it == '£' || it == '$' || it == '\u20AC' }

    fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it, ignoreCase = true) }
}
