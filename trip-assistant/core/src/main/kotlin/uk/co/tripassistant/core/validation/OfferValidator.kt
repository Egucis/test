package uk.co.tripassistant.core.validation

import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.model.UnreadableReason
import uk.co.tripassistant.core.model.ValidatedOffer

/** Result of sanity-checking a parsed offer (spec section 16). */
sealed interface ValidationOutcome {
    data class Valid(val offer: ValidatedOffer) : ValidationOutcome
    data class Invalid(val reason: UnreadableReason, val issues: List<String>) : ValidationOutcome
}

/**
 * Sanity validation, spec section 16.
 *
 * The rule this class exists to enforce: a number that cannot be believed is thrown away, never
 * replaced by a guess. Anything that fails here becomes UNKNOWN downstream.
 */
object OfferValidator {

    // Realistic bounds for a UK private-hire offer. Deliberately generous — these exist to catch
    // OCR nonsense (a phone number read as a fare), not to second-guess Uber's pricing.
    const val MIN_FARE = 0.50
    const val MAX_FARE = 1_000.0
    const val MAX_PICKUP_MILES = 100.0
    const val MAX_TRIP_MILES = 500.0
    const val MAX_PICKUP_MINUTES = 240.0
    const val MAX_TRIP_MINUTES = 720.0
    const val MIN_RATING = 1.0
    const val MAX_RATING = 5.0

    // Cross-field plausibility. An offer outside these is self-contradictory, not merely unusual.
    private const val MIN_IMPLIED_POUNDS_PER_MILE = 0.10
    private const val MAX_IMPLIED_POUNDS_PER_MILE = 60.0
    private const val MAX_IMPLIED_AVERAGE_MPH = 90.0

    fun validate(raw: RawOffer): ValidationOutcome {
        val issues = mutableListOf<String>()

        // --- mandatory fields (spec section 14: everything else may legitimately be absent) ---
        val fare = raw.fareGbp
            ?: return ValidationOutcome.Invalid(UnreadableReason.MISSING_FARE, listOf("Fare not found"))
        val tripMiles = raw.tripMiles
            ?: return ValidationOutcome.Invalid(UnreadableReason.MISSING_DISTANCE, listOf("Trip distance not found"))
        // A pickup distance of exactly zero is legitimate (rider standing at the car), so only a
        // missing value is fatal here.
        val pickupMiles = raw.pickupMiles
            ?: return ValidationOutcome.Invalid(UnreadableReason.MISSING_DISTANCE, listOf("Pickup distance not found"))

        // --- per-field ranges ---
        if (fare < MIN_FARE || fare > MAX_FARE) issues += "Fare ${fare} outside £$MIN_FARE–£$MAX_FARE"
        if (pickupMiles < 0.0 || pickupMiles > MAX_PICKUP_MILES) issues += "Pickup distance ${pickupMiles} mi implausible"
        if (tripMiles <= 0.0 || tripMiles > MAX_TRIP_MILES) issues += "Trip distance ${tripMiles} mi implausible"
        raw.pickupMinutes?.let {
            if (it < 0.0 || it > MAX_PICKUP_MINUTES) issues += "Pickup time ${it} min implausible"
        }
        raw.tripMinutes?.let {
            if (it <= 0.0 || it > MAX_TRIP_MINUTES) issues += "Trip time ${it} min implausible"
        }
        raw.riderRating?.let {
            if (it < MIN_RATING || it > MAX_RATING) issues += "Rider rating ${it} outside $MIN_RATING–$MAX_RATING"
        }

        if (issues.isNotEmpty()) {
            return ValidationOutcome.Invalid(UnreadableReason.IMPLAUSIBLE_VALUES, issues)
        }

        val totalMiles = pickupMiles + tripMiles
        if (totalMiles <= 0.0) {
            return ValidationOutcome.Invalid(UnreadableReason.ZERO_DISTANCE, listOf("Total distance is zero"))
        }

        // --- cross-field contradictions (spec section 16: a rating must not become mileage) ---
        val impliedPerMile = fare / totalMiles
        if (impliedPerMile < MIN_IMPLIED_POUNDS_PER_MILE || impliedPerMile > MAX_IMPLIED_POUNDS_PER_MILE) {
            return ValidationOutcome.Invalid(
                UnreadableReason.CONTRADICTORY_VALUES,
                listOf("Implied £/mile of ${impliedPerMile} does not make sense for this fare and distance")
            )
        }

        val totalMinutes = if (raw.pickupMinutes != null && raw.tripMinutes != null) {
            raw.pickupMinutes + raw.tripMinutes
        } else {
            null
        }
        if (totalMinutes != null && totalMinutes > 0.0) {
            val impliedMph = totalMiles / (totalMinutes / 60.0)
            if (impliedMph > MAX_IMPLIED_AVERAGE_MPH) {
                return ValidationOutcome.Invalid(
                    UnreadableReason.CONTRADICTORY_VALUES,
                    listOf("Implied average speed of ${impliedMph} mph is not achievable")
                )
            }
        }

        val confidence = if (raw.notes.any { it.degradesConfidence }) {
            OfferConfidence.PARTIAL
        } else {
            OfferConfidence.HIGH
        }

        return ValidationOutcome.Valid(
            ValidatedOffer(
                fareGbp = fare,
                pickupMiles = pickupMiles,
                tripMiles = tripMiles,
                pickupMinutes = raw.pickupMinutes,
                tripMinutes = raw.tripMinutes,
                riderRating = raw.riderRating,
                parserVersion = raw.parserVersion,
                confidence = confidence,
                notes = raw.notes
            )
        )
    }
}
