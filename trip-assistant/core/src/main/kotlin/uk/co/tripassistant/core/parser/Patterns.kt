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

    /** A line that is nothing but a rating, with or without a star: "4.91", "★ 4.91". */
    val RATING_ONLY_LINE = Regex("^[★☆⭐✩*\\s]*([1-5][.,]\\d{1,2})[★☆⭐✩*\\s]*$")

    val RATING_INLINE = Regex("\\b([1-5][.,]\\d{1,2})\\b")

    val STAR = Regex("[★☆⭐✩]")

    /** Words that mean "this number describes getting to the rider". */
    val PICKUP_KEYWORDS = listOf("away", "pickup", "pick up", "pick-up", "to rider", "to pickup")

    /** Words that mean "this number describes the paid journey". */
    val TRIP_KEYWORDS = listOf("trip", "dropoff", "drop off", "drop-off", "destination", "journey", "to destination")

    /** Amounts sitting next to these words are not the offered fare. */
    val FARE_EXCLUSION_KEYWORDS = listOf(
        "bonus", "promo", "promotion", "tip", "boost", "surge", "quest", "incentive",
        "per hour", "/hr", "/h", "per mile", "/mi", "extra", "included", "includes",
        "total earnings", "today", "week", "balance"
    )

    /** Words that make a screen look like a trip offer. */
    val OFFER_KEYWORDS = listOf(
        "accept", "match", "away", "trip", "uberx", "uber x", "comfort", "uber green", "green",
        "xl", "exclusive", "pet", "assist", "share", "reserve", "delivery", "premier",
        "verified", "surge", "guaranteed", "rider", "passenger"
    )

    /** Words that mean the driver is somewhere else in the Uber app entirely. */
    val NON_OFFER_KEYWORDS = listOf(
        "navigate", "start trip", "end trip", "complete trip", "arrived", "you're offline",
        "you are offline", "go online", "earnings", "wallet", "account", "settings", "inbox",
        "opportunities", "vehicle", "help", "safety toolkit"
    )

    fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it, ignoreCase = true) }
}
