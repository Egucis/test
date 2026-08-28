package uk.co.tripassistant.core.parser

import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.text.OcrText

/**
 * The inline layout, where a single line carries the numbers *and* the word that says what they
 * mean:
 *
 * ```
 * £18.50
 * ★ 4.91
 * 7 mins (2.4 mi) away
 * 26 mins (9.1 mi) trip
 * ```
 *
 * This parser only accepts a labelled line. It deliberately refuses to guess which line is which
 * — that job belongs to [UberUkStandardV2Parser], which records the assumption as a note.
 */
object UberUkStandardV1Parser : OfferParser {

    override val version: String = "UBER_UK_STANDARD_V1"

    override fun parse(text: OcrText): RawOffer? {
        val notes = mutableSetOf<ParseNote>()

        val legs = mutableMapOf<Leg, LegValues>()
        for (line in text.readingOrder()) {
            val leg = legFor(line.text) ?: continue
            val distance = OfferFields.distancesIn(line.text).firstOrNull()
            val duration = OfferFields.durationsIn(line.text).firstOrNull()
            if (distance == null && duration == null) continue

            // First labelled line wins: Uber shows each leg once, and anything later on the card
            // (an ETA banner, a surge strip) must not overwrite it.
            val existing = legs[leg]
            legs[leg] = LegValues(
                miles = existing?.miles ?: distance?.miles,
                minutes = existing?.minutes ?: duration?.minutes
            )
            if (distance?.corrected == true || duration?.corrected == true) {
                notes += ParseNote.OCR_DIGIT_CORRECTED
            }
            if (distance?.convertedFromKm == true) notes += ParseNote.KILOMETRES_CONVERTED
        }

        // No labelled numeric line at all means this is not the layout this parser handles.
        if (legs.isEmpty()) return null

        val fare = OfferFields.findFare(text)
        val rating = OfferFields.findRating(text)
        notes += fare.notes
        notes += rating.notes

        return RawOffer(
            fareGbp = fare.value,
            pickupMiles = legs[Leg.PICKUP]?.miles,
            pickupMinutes = legs[Leg.PICKUP]?.minutes,
            tripMiles = legs[Leg.TRIP]?.miles,
            tripMinutes = legs[Leg.TRIP]?.minutes,
            riderRating = rating.value,
            parserVersion = version,
            notes = notes.toList()
        )
    }

    /** Pickup wording is checked first — "away" is the more specific of the two. */
    private fun legFor(line: String): Leg? = when {
        Patterns.containsAny(line, Patterns.PICKUP_KEYWORDS) -> Leg.PICKUP
        Patterns.containsAny(line, Patterns.TRIP_KEYWORDS) -> Leg.TRIP
        else -> null
    }

    private data class LegValues(val miles: Double?, val minutes: Double?)
}
