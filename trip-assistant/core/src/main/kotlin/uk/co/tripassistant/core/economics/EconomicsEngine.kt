package uk.co.tripassistant.core.economics

import uk.co.tripassistant.core.model.OfferMetrics
import uk.co.tripassistant.core.model.ValidatedOffer

/**
 * Turns a validated offer into the derived numbers of spec section 17.
 *
 * Every division is guarded: a zero denominator returns null rather than Infinity or NaN, which
 * is what makes "zero distance -> UNKNOWN" fall out naturally instead of crashing
 * (spec section 56).
 */
object EconomicsEngine {

    /** Returns null when the offer cannot produce meaningful economics (zero total distance). */
    fun calculate(offer: ValidatedOffer): OfferMetrics? {
        val totalMiles = offer.pickupMiles + offer.tripMiles
        if (totalMiles <= 0.0 || offer.tripMiles <= 0.0) return null

        val totalMinutes = if (offer.pickupMinutes != null && offer.tripMinutes != null) {
            offer.pickupMinutes + offer.tripMinutes
        } else {
            null
        }

        val poundsPerHour = totalMinutes
            ?.takeIf { it > 0.0 }
            ?.let { offer.fareGbp / (it / 60.0) }

        return OfferMetrics(
            fareGbp = offer.fareGbp,
            pickupMiles = offer.pickupMiles,
            tripMiles = offer.tripMiles,
            totalMiles = totalMiles,
            pickupMinutes = offer.pickupMinutes,
            tripMinutes = offer.tripMinutes,
            totalMinutes = totalMinutes,
            riderRating = offer.riderRating,
            poundsPerMile = offer.fareGbp / totalMiles,
            passengerPoundsPerMile = offer.fareGbp / offer.tripMiles,
            poundsPerHour = poundsPerHour,
            pickupPercentage = offer.pickupMiles / totalMiles * 100.0
        )
    }
}
