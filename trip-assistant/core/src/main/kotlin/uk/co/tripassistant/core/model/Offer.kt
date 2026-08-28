package uk.co.tripassistant.core.model

/**
 * Something the parser had to assume, fix or convert. Surfaced on the diagnostics screen and
 * used to decide whether an offer is HIGH or PARTIAL confidence.
 */
enum class ParseNote(val message: String, val degradesConfidence: Boolean) {
    /** A safe, re-validated correction (spec section 15) — recorded, but not a reason to doubt. */
    OCR_DIGIT_CORRECTED("A digit was corrected before it was accepted", degradesConfidence = false),

    /** A deterministic unit conversion — no judgement involved. */
    KILOMETRES_CONVERTED("A distance was converted from kilometres to miles", degradesConfidence = false),

    /** Pickup and trip were told apart by screen position because no away/trip label was read. */
    PICKUP_TRIP_ORDER_ASSUMED("Pickup/trip were told apart by their position, not by a label", degradesConfidence = true),

    /** More than one plausible amount was on screen and the most prominent one was taken. */
    FARE_CHOSEN_BY_PROMINENCE("Several amounts were on screen; the most prominent was taken as the fare", degradesConfidence = true),

    /** A rating-shaped number with no star next to it. */
    RATING_WITHOUT_STAR_ANCHOR("A rating was accepted without a star symbol next to it", degradesConfidence = true)
}

/**
 * Exactly what the parser read, before any validation. Every field is nullable because not every
 * Uber offer type shows every field (spec section 14).
 *
 * Location text is carried here only so a parser can use it as an anchor; it is deliberately
 * never persisted (spec section 40).
 */
data class RawOffer(
    val fareGbp: Double? = null,
    val pickupMiles: Double? = null,
    val pickupMinutes: Double? = null,
    val tripMiles: Double? = null,
    val tripMinutes: Double? = null,
    val riderRating: Double? = null,
    val pickupLabel: String? = null,
    val destinationLabel: String? = null,
    val parserVersion: String = "UNKNOWN",
    val notes: List<ParseNote> = emptyList()
)

/**
 * A [RawOffer] that has passed sanity validation (spec section 16). The three fields the whole
 * product depends on — fare, pickup distance, trip distance — are non-null by construction.
 */
data class ValidatedOffer(
    val fareGbp: Double,
    val pickupMiles: Double,
    val tripMiles: Double,
    val pickupMinutes: Double?,
    val tripMinutes: Double?,
    val riderRating: Double?,
    val parserVersion: String,
    val confidence: OfferConfidence,
    val notes: List<ParseNote>
)

/** The derived economics of spec section 17. */
data class OfferMetrics(
    val fareGbp: Double,
    val pickupMiles: Double,
    val tripMiles: Double,
    val totalMiles: Double,
    val pickupMinutes: Double?,
    val tripMinutes: Double?,
    val totalMinutes: Double?,
    val riderRating: Double?,
    /** fare / totalMiles — the principal mileage metric (spec section 17.3). */
    val poundsPerMile: Double,
    /** fare / tripMiles — informational only, never replaces [poundsPerMile] (spec section 17.4). */
    val passengerPoundsPerMile: Double,
    /** fare / (totalMinutes / 60). Null when Uber did not show both times. */
    val poundsPerHour: Double?,
    /** pickupMiles / totalMiles * 100 (spec section 17.6). */
    val pickupPercentage: Double
)

/** How one rule scored against one offer. */
data class MetricResult(
    val ruleId: RuleId,
    val importance: RuleImportance,
    val target: Double,
    val actual: Double?,
    val status: MetricStatus
) {
    val isHardFailure: Boolean
        get() = importance == RuleImportance.HARD && status == MetricStatus.RED
}

/** One line of "why is this not GOOD" (spec section 23). */
data class Reason(
    val headline: String,
    val detail: String,
    val severity: MetricStatus
)

/** The complete result the overlay, history and rule tester all render. */
data class OfferEvaluation(
    val recommendation: Recommendation,
    val metrics: OfferMetrics?,
    val metricResults: List<MetricResult>,
    val reasons: List<Reason>,
    val confidence: OfferConfidence,
    val profileId: Long,
    val profileName: String,
    val parserVersion: String?,
    val unreadable: UnreadableReason?,
    val notes: List<ParseNote> = emptyList()
) {
    val primaryReason: Reason? get() = reasons.firstOrNull()
}
