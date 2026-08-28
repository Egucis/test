package uk.co.tripassistant.core.parser

import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.text.OcrText

/**
 * The stacked layout, where the label sits on its own line and the numbers follow underneath:
 *
 * ```
 * £18.50
 * 4.91
 * Pickup
 * 2.4 mi
 * 7 min
 * Dropoff
 * 9.1 mi
 * 26 min
 * ```
 *
 * A label claims every number below it until the next label appears. When a card carries no
 * labels at all — which happens on some compact offer cards — and exactly two lines carry a
 * distance, the top one is taken as the pickup and that assumption is recorded as a note, which
 * caps the offer at BORDERLINE rather than letting a guess produce a green light.
 */
object UberUkStandardV2Parser : OfferParser {

    override val version: String = "UBER_UK_STANDARD_V2"

    override fun parse(text: OcrText): RawOffer? {
        val notes = mutableSetOf<ParseNote>()
        val lines = text.readingOrder()

        val values = mutableMapOf<Leg, MutableLegValues>()
        var current: Leg? = null
        var sawLabel = false

        for (line in lines) {
            legFor(line.text)?.let {
                current = it
                sawLabel = true
            }
            val leg = current ?: continue

            val target = values.getOrPut(leg) { MutableLegValues() }
            OfferFields.distancesIn(line.text).firstOrNull()?.let { distance ->
                if (target.miles == null) {
                    target.miles = distance.miles
                    if (distance.corrected) notes += ParseNote.OCR_DIGIT_CORRECTED
                    if (distance.convertedFromKm) notes += ParseNote.KILOMETRES_CONVERTED
                }
            }
            OfferFields.durationsIn(line.text).firstOrNull()?.let { duration ->
                if (target.minutes == null) {
                    target.minutes = duration.minutes
                    if (duration.corrected) notes += ParseNote.OCR_DIGIT_CORRECTED
                }
            }
        }

        if (!sawLabel) {
            val fallback = orderedFallback(text) ?: return null
            values.clear()
            values.putAll(fallback)
            notes += ParseNote.PICKUP_TRIP_ORDER_ASSUMED
        }

        // A label on its own proves nothing — "Looking for trips" is not an offer. This parser has
        // only recognised the layout once it has actually claimed a number.
        if (values.values.none { it.miles != null || it.minutes != null }) return null

        val fare = OfferFields.findFare(text)
        val rating = OfferFields.findRating(text)
        notes += fare.notes
        notes += rating.notes

        return RawOffer(
            fareGbp = fare.value,
            pickupMiles = values[Leg.PICKUP]?.miles,
            pickupMinutes = values[Leg.PICKUP]?.minutes,
            tripMiles = values[Leg.TRIP]?.miles,
            tripMinutes = values[Leg.TRIP]?.minutes,
            riderRating = rating.value,
            parserVersion = version,
            notes = notes.toList()
        )
    }

    /**
     * Unlabelled cards.
     *
     * Each distance starts a block, and a following duration-only line joins the block above it —
     * that covers both "2.4 mi · 7 min" on one line and the two stacked on separate lines. The
     * fallback only fires when exactly two blocks come out, so "the top one is the pickup" is a
     * reading of the layout rather than a coin toss. Even then the caller records
     * PICKUP_TRIP_ORDER_ASSUMED, which caps the offer at BORDERLINE.
     */
    private fun orderedFallback(text: OcrText): Map<Leg, MutableLegValues>? {
        val blocks = mutableListOf<MutableLegValues>()
        for (line in text.readingOrder()) {
            val distance = OfferFields.distancesIn(line.text).firstOrNull()
            val duration = OfferFields.durationsIn(line.text).firstOrNull()
            when {
                distance != null -> blocks += MutableLegValues(distance.miles, duration?.minutes)
                duration != null -> blocks.lastOrNull()?.let { block ->
                    if (block.minutes == null) block.minutes = duration.minutes
                }
            }
        }
        if (blocks.size != 2) return null
        return mapOf(Leg.PICKUP to blocks[0], Leg.TRIP to blocks[1])
    }

    private fun legFor(line: String): Leg? = when {
        Patterns.containsAny(line, Patterns.PICKUP_KEYWORDS) -> Leg.PICKUP
        Patterns.containsAny(line, Patterns.TRIP_KEYWORDS) -> Leg.TRIP
        else -> null
    }

    private data class MutableLegValues(var miles: Double? = null, var minutes: Double? = null)
}
