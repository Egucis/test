package uk.co.tripassistant.core.model

/**
 * The four outcomes the driver ever sees (spec section 21).
 *
 * [UNKNOWN] is a first-class result, not an error path: whenever the screen could not be read
 * well enough to be sure, UNKNOWN is the *correct* answer and must never be quietly upgraded to
 * [GOOD] by filling in a missing number (spec section 63).
 */
enum class Recommendation {
    GOOD,
    BORDERLINE,
    POOR,
    UNKNOWN
}

/** Per-metric traffic light (spec section 20). */
enum class MetricStatus {
    GREEN,
    AMBER,
    RED,

    /**
     * The rule is switched on but the offer did not show the value it needs (for example Uber did
     * not display a rider rating). Never treated as a pass.
     */
    NOT_EVALUATED
}

/** How much a single rule matters (spec section 19). */
enum class RuleImportance {
    /** Rule ignored entirely. */
    OFF,

    /** Influences the recommendation but cannot on its own reject the offer. */
    SOFT,

    /** A RED result on this rule forces POOR. */
    HARD
}

/**
 * How much of the offer could be read.
 *
 * [HIGH]    every value the active profile needs was read cleanly.
 * [PARTIAL] readable and safe to score, but something was assumed, corrected or missing — the
 *           evaluation is capped at BORDERLINE so an incomplete read can never show GOOD
 *           (spec sections 49 and 63).
 * [LOW]     not good enough to score at all; the result is UNKNOWN.
 */
enum class OfferConfidence {
    HIGH,
    PARTIAL,
    LOW
}

/** Recorded outcome of an offer (spec section 30). Never guessed. */
enum class OfferOutcome {
    SEEN,
    ACCEPTED,
    NOT_ACCEPTED,
    UNKNOWN_OUTCOME
}

/** Why an evaluation came back UNKNOWN. Shown on the diagnostics screen (spec sections 44, 49). */
enum class UnreadableReason(val shortText: String, val detailText: String) {
    NOT_AN_OFFER_SCREEN("Offer format not recognised", "This screen does not look like a trip offer"),
    MISSING_FARE("Can't read offer", "Fare not found"),
    MISSING_DISTANCE("Can't read offer", "Trip distance not found"),
    MISSING_TIME("Can't read offer", "Trip time not found"),
    MISSING_RATING("Can't read offer", "Rider rating not found"),
    CONTRADICTORY_VALUES("Can't read offer", "Recognised values contradict each other"),
    IMPLAUSIBLE_VALUES("Can't read offer", "Recognised values are outside a realistic range"),
    ZERO_DISTANCE("Can't read offer", "Total distance came out as zero"),
    LOW_CONFIDENCE("Can't read offer", "Not enough of the offer could be read reliably")
}
