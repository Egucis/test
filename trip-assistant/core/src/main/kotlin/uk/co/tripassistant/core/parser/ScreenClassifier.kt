package uk.co.tripassistant.core.parser

import uk.co.tripassistant.core.text.OcrText

enum class ScreenType {
    /** Nothing offer-like on screen — the assistant stays quiet rather than flashing UNKNOWN. */
    NOT_OFFER,

    /** Offer-shaped, but the parsers may still fail. A failure here is a real UNKNOWN. */
    POSSIBLE_OFFER,

    /** Confidently a trip offer. */
    OFFER
}

data class ScreenClassification(
    val type: ScreenType,
    val score: Int,
    val signals: List<String>
)

/**
 * Cheap "is this even a trip offer?" check that runs before any parsing (spec section 12).
 *
 * It exists for two reasons: it keeps the assistant silent while the driver is on the map or in
 * the earnings screen, and it draws the line between "not an offer" (say nothing) and "looks like
 * an offer but could not be read" (say UNKNOWN, spec section 49).
 */
object ScreenClassifier {

    private const val OFFER_THRESHOLD = 6
    private const val POSSIBLE_THRESHOLD = 4

    fun classify(text: OcrText): ScreenClassification {
        if (text.isEmpty()) return ScreenClassification(ScreenType.NOT_OFFER, 0, emptyList())

        val body = text.joined
        val signals = mutableListOf<String>()
        var score = 0

        if (Patterns.CURRENCY.containsMatchIn(body)) {
            score += 2
            signals += "currency amount"
        }
        if (Patterns.DISTANCE.containsMatchIn(body)) {
            score += 2
            signals += "distance"
        }
        if (Patterns.DURATION.containsMatchIn(body)) {
            score += 1
            signals += "duration"
        }
        if (Patterns.STAR.containsMatchIn(body)) {
            score += 1
            signals += "star/rating"
        }
        if (Patterns.containsAny(body, Patterns.OFFER_KEYWORDS)) {
            score += 2
            signals += "offer wording"
        }

        val distractions = Patterns.NON_OFFER_KEYWORDS.count { body.contains(it, ignoreCase = true) }
        if (distractions > 0) {
            score -= 2 * distractions
            signals += "non-offer wording x$distractions"
        }

        val type = when {
            score >= OFFER_THRESHOLD -> ScreenType.OFFER
            score >= POSSIBLE_THRESHOLD -> ScreenType.POSSIBLE_OFFER
            else -> ScreenType.NOT_OFFER
        }
        return ScreenClassification(type, score, signals)
    }
}
