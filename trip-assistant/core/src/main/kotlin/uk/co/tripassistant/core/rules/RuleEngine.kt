package uk.co.tripassistant.core.rules

import uk.co.tripassistant.core.economics.EconomicsEngine
import uk.co.tripassistant.core.model.MetricResult
import uk.co.tripassistant.core.model.MetricStatus
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.OfferMetrics
import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.model.RuleDirection
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.model.UnreadableReason
import uk.co.tripassistant.core.model.ValidatedOffer

/**
 * The deterministic decision engine of spec sections 20 and 21.
 *
 * There is no model, no scoring heuristic and no learning here on purpose (spec section 21):
 * given the same offer and the same profile this returns the same answer every time, which is
 * what makes the rule tester (spec section 43) an honest preview of the live overlay.
 */
object RuleEngine {

    /** Guards against 1.4999999999 being called RED when the driver typed 1.50. */
    private const val EPSILON = 1e-9

    /** Full path: validated offer -> economics -> recommendation. */
    fun evaluate(offer: ValidatedOffer, profile: RuleProfile): OfferEvaluation {
        val metrics = EconomicsEngine.calculate(offer)
            ?: return unknown(
                reason = UnreadableReason.ZERO_DISTANCE,
                profile = profile,
                parserVersion = offer.parserVersion,
                notes = offer.notes
            )
        return evaluateMetrics(
            metrics = metrics,
            profile = profile,
            confidence = offer.confidence,
            parserVersion = offer.parserVersion,
            notes = offer.notes
        )
    }

    /**
     * Scores already-calculated metrics. Used directly by the rule tester, where the driver types
     * the numbers in and there is no OCR involved.
     */
    fun evaluateMetrics(
        metrics: OfferMetrics,
        profile: RuleProfile,
        confidence: OfferConfidence = OfferConfidence.HIGH,
        parserVersion: String? = null,
        notes: List<ParseNote> = emptyList()
    ): OfferEvaluation {
        val tolerance = profile.toleranceFraction

        val results = profile.activeRules().map { (ruleId, setting) ->
            val actual = actualValueFor(ruleId, metrics)
            MetricResult(
                ruleId = ruleId,
                importance = setting.importance,
                target = setting.target,
                actual = actual,
                status = if (actual == null) {
                    MetricStatus.NOT_EVALUATED
                } else {
                    statusFor(ruleId.direction, actual, setting.target, tolerance)
                }
            )
        }

        // A HARD rule is the driver saying "this one is a dealbreaker". If the offer did not show
        // the value it needs, the honest answer is that we cannot tell — not a pass
        // (spec sections 49 and 63).
        val uncheckableHardRule = results.firstOrNull {
            it.importance == RuleImportance.HARD && it.status == MetricStatus.NOT_EVALUATED
        }
        if (uncheckableHardRule != null) {
            return unknown(
                reason = missingValueReason(uncheckableHardRule.ruleId),
                profile = profile,
                parserVersion = parserVersion,
                notes = notes,
                metrics = metrics,
                metricResults = results
            )
        }

        val reds = results.count { it.status == MetricStatus.RED }
        val ambers = results.count { it.status == MetricStatus.AMBER }
        val hardFailure = results.any { it.isHardFailure }
        val uncheckedSoftRules = results.filter {
            it.importance == RuleImportance.SOFT && it.status == MetricStatus.NOT_EVALUATED
        }

        // Spec section 21, in order.
        var recommendation = when {
            hardFailure -> Recommendation.POOR
            reds >= 2 -> Recommendation.POOR
            reds == 1 -> Recommendation.BORDERLINE
            ambers >= 1 -> Recommendation.BORDERLINE
            else -> Recommendation.GOOD
        }

        // "Never show GOOD based on incomplete data" (spec section 49). An offer that was only
        // partially readable, or where a switched-on soft rule could not be checked, tops out at
        // BORDERLINE — the driver still gets the economics, but not a green light.
        val incomplete = confidence == OfferConfidence.PARTIAL || uncheckedSoftRules.isNotEmpty()
        if (recommendation == Recommendation.GOOD && incomplete) {
            recommendation = Recommendation.BORDERLINE
        }

        return OfferEvaluation(
            recommendation = recommendation,
            metrics = metrics,
            metricResults = results,
            reasons = ReasonBuilder.build(results, recommendation, confidence, notes),
            confidence = confidence,
            profileId = profile.id,
            profileName = profile.name,
            parserVersion = parserVersion,
            unreadable = null,
            notes = notes
        )
    }

    /** Builds the UNKNOWN result. Neutral styling, never red or green (spec section 21). */
    fun unknown(
        reason: UnreadableReason,
        profile: RuleProfile,
        parserVersion: String? = null,
        notes: List<ParseNote> = emptyList(),
        metrics: OfferMetrics? = null,
        metricResults: List<MetricResult> = emptyList()
    ): OfferEvaluation = OfferEvaluation(
        recommendation = Recommendation.UNKNOWN,
        metrics = metrics,
        metricResults = metricResults,
        reasons = ReasonBuilder.unknownReasons(reason),
        confidence = OfferConfidence.LOW,
        profileId = profile.id,
        profileName = profile.name,
        parserVersion = parserVersion,
        unreadable = reason,
        notes = notes
    )

    /**
     * Green/amber/red for one metric (spec section 20).
     *
     * Minimum rules: GREEN at or above target, AMBER down to target*(1-tolerance), RED below.
     * Maximum rules: GREEN at or below target, AMBER up to target*(1+tolerance), RED above.
     */
    fun statusFor(
        direction: RuleDirection,
        actual: Double,
        target: Double,
        toleranceFraction: Double
    ): MetricStatus = when (direction) {
        RuleDirection.MINIMUM -> when {
            actual >= target - EPSILON -> MetricStatus.GREEN
            actual >= target * (1.0 - toleranceFraction) - EPSILON -> MetricStatus.AMBER
            else -> MetricStatus.RED
        }

        RuleDirection.MAXIMUM -> when {
            actual <= target + EPSILON -> MetricStatus.GREEN
            actual <= target * (1.0 + toleranceFraction) + EPSILON -> MetricStatus.AMBER
            else -> MetricStatus.RED
        }
    }

    /** Null means "the offer did not show what this rule needs". */
    private fun actualValueFor(ruleId: RuleId, metrics: OfferMetrics): Double? = when (ruleId) {
        RuleId.MIN_POUNDS_PER_MILE -> metrics.poundsPerMile
        RuleId.MIN_POUNDS_PER_HOUR -> metrics.poundsPerHour
        RuleId.MAX_PICKUP_MILES -> metrics.pickupMiles
        RuleId.MAX_PICKUP_MINUTES -> metrics.pickupMinutes
        RuleId.MIN_RIDER_RATING -> metrics.riderRating
        RuleId.MIN_FARE -> metrics.fareGbp
        RuleId.MAX_PICKUP_PERCENT -> metrics.pickupPercentage
    }

    private fun missingValueReason(ruleId: RuleId): UnreadableReason = when (ruleId) {
        RuleId.MIN_RIDER_RATING -> UnreadableReason.MISSING_RATING
        RuleId.MIN_POUNDS_PER_HOUR, RuleId.MAX_PICKUP_MINUTES -> UnreadableReason.MISSING_TIME
        else -> UnreadableReason.LOW_CONFIDENCE
    }
}
