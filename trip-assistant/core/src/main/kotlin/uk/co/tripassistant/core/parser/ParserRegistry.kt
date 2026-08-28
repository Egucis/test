package uk.co.tripassistant.core.parser

import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.text.OcrText

/** A successful parse and the parser that produced it. */
data class ParsedCandidate(val offer: RawOffer, val parserVersion: String)

/**
 * Runs every known layout parser and keeps the best result (spec section 13).
 *
 * "Best" is the most complete read, not the first one that returns something: a card can look
 * partly like two layouts, and the parser that recovered the trip distance is more useful than
 * the one that only found the fare. Assumptions cost a point, so a confident partial read beats a
 * guessy complete one.
 */
class ParserRegistry(
    private val parsers: List<OfferParser> = DEFAULT_PARSERS
) {

    fun parse(text: OcrText): ParsedCandidate? =
        parsers
            .mapNotNull { parser -> parser.parse(text)?.let { ParsedCandidate(it, parser.version) } }
            .maxByOrNull { completeness(it.offer) }

    /** Which layouts this build knows about — shown on the diagnostics screen. */
    fun knownVersions(): List<String> = parsers.map { it.version }

    private fun completeness(offer: RawOffer): Int {
        var score = 0
        if (offer.fareGbp != null) score += 3
        if (offer.pickupMiles != null) score += 3
        if (offer.tripMiles != null) score += 3
        if (offer.pickupMinutes != null) score += 1
        if (offer.tripMinutes != null) score += 1
        if (offer.riderRating != null) score += 1
        score -= offer.notes.count { it.degradesConfidence }
        return score
    }

    companion object {
        val DEFAULT_PARSERS: List<OfferParser> = listOf(
            UberUkStandardV1Parser,
            UberUkStandardV2Parser
        )
    }
}
