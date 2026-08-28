package uk.co.tripassistant.app.overlay

import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.Recommendation

/**
 * Exactly what the floating window shows (spec sections 25 and 26).
 *
 * Pre-formatted strings rather than numbers: the overlay redraws while OCR is running on the same
 * device, so formatting happens once, off the drawing path. The words come from
 * [uk.co.tripassistant.core.format.Formats], the same code the history list uses, so the two can
 * never disagree about what an offer was worth.
 */
data class OverlayState(
    /** Null while the assistant is running but no offer is on screen. */
    val recommendation: Recommendation?,
    val metricsLine: String,
    val reasonLine: String,
    val fare: String,
    val perMile: String,
    val perHour: String,
    val pickup: String,
    val trip: String,
    val rider: String,
    val profileLine: String
) {

    companion object {

        private const val NOT_SHOWN = "—"

        fun waiting(profileName: String): OverlayState = OverlayState(
            recommendation = null,
            metricsLine = "",
            reasonLine = "",
            fare = NOT_SHOWN,
            perMile = NOT_SHOWN,
            perHour = NOT_SHOWN,
            pickup = NOT_SHOWN,
            trip = NOT_SHOWN,
            rider = NOT_SHOWN,
            profileLine = "Profile · $profileName"
        )

        fun from(evaluation: OfferEvaluation): OverlayState {
            val metrics = evaluation.metrics
            val reason = evaluation.primaryReason

            // Collapsed line: the two numbers a driver decides on, and nothing else
            // (spec section 25).
            val metricsLine = when {
                metrics == null -> reason?.headline.orEmpty()
                else -> listOfNotNull(
                    Formats.poundsPerMile(metrics.poundsPerMile),
                    metrics.poundsPerHour?.let { Formats.poundsPerHour(it) }
                ).joinToString(" · ")
            }

            return OverlayState(
                recommendation = evaluation.recommendation,
                metricsLine = metricsLine,
                reasonLine = reason?.let { "${it.headline} · ${it.detail}" }.orEmpty(),
                fare = metrics?.let { Formats.money(it.fareGbp) } ?: NOT_SHOWN,
                perMile = metrics?.let { Formats.poundsPerMile(it.poundsPerMile) } ?: NOT_SHOWN,
                perHour = metrics?.poundsPerHour?.let { Formats.poundsPerHourPrecise(it) } ?: NOT_SHOWN,
                pickup = metrics?.let { legText(it.pickupMiles, it.pickupMinutes) } ?: NOT_SHOWN,
                trip = metrics?.let { legText(it.tripMiles, it.tripMinutes) } ?: NOT_SHOWN,
                rider = metrics?.riderRating?.let { Formats.rating(it) } ?: NOT_SHOWN,
                profileLine = "Profile · ${evaluation.profileName}"
            )
        }

        private fun legText(miles: Double, minutes: Double?): String = listOfNotNull(
            Formats.miles(miles),
            minutes?.let { Formats.minutes(it) }
        ).joinToString(" · ")
    }
}
