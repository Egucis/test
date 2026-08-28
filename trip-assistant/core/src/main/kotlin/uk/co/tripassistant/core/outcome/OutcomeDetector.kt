package uk.co.tripassistant.core.outcome

import uk.co.tripassistant.core.text.OcrText

/** What the screen after an offer says about what happened to it. */
enum class PostOfferSignal {
    /** Strong evidence the driver is now on their way to a rider. */
    TRIP_ACCEPTED,

    /** Nothing conclusive. */
    NONE
}

/**
 * Outcome detection, spec section 30.
 *
 * The bar is deliberately high and one-sided. An offer disappearing proves nothing — it may have
 * timed out, been withdrawn, or gone to another driver — so this class has no way to report
 * "declined" at all. It only ever reports that a trip was clearly accepted, and only when the
 * screen says something that simply does not appear on an offer card.
 *
 * Accuracy beats attractive statistics (spec section 30).
 */
object OutcomeDetector {

    /**
     * Phrases that belong to the in-trip screens and nowhere else. Deliberately specific: "trip"
     * or "navigate" on their own are far too common to mean anything.
     */
    private val ACCEPTED_PHRASES = listOf(
        // Current UK flow: accepting an offer replaces the "Confirm" card with the same card
        // under a "Matched" heading and a "Let's go" button. Neither wording appears on an offer
        // that is still open, so seeing them is real evidence the trip was taken.
        "matched",
        "let's go",
        "lets go",

        // In-trip screens.
        "cancel trip",
        "start trip",
        "begin trip",
        "arrived at pickup",
        "navigate to pickup",
        "slide to start",
        "swipe to start",
        "confirm arrival",
        "confirm pickup",
        "waiting for rider",
        "waiting for your rider",
        "rider is on the way",
        "meet your rider"
    )

    fun signal(text: OcrText): PostOfferSignal {
        val body = text.joined
        val matched = ACCEPTED_PHRASES.any { body.contains(it, ignoreCase = true) }
        return if (matched) PostOfferSignal.TRIP_ACCEPTED else PostOfferSignal.NONE
    }
}
