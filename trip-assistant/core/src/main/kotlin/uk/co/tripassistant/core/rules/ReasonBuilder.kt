package uk.co.tripassistant.core.rules

import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.MetricResult
import uk.co.tripassistant.core.model.MetricStatus
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.Reason
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.model.RuleDirection
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.UnreadableReason
import kotlin.math.abs

/**
 * Turns rule results into the short "why" text the overlay shows (spec section 23).
 *
 * The first reason is the one the driver reads at a glance, so ordering matters more than
 * wording: hard failures, then red metrics, then amber, then anything that could not be checked.
 * Within a band, the rule that missed by the largest relative margin wins, with the rule's own
 * priority breaking ties.
 */
object ReasonBuilder {

    fun build(
        results: List<MetricResult>,
        recommendation: Recommendation,
        confidence: OfferConfidence,
        notes: List<ParseNote>
    ): List<Reason> {
        val problems = results
            .filter { it.status != MetricStatus.GREEN }
            .sortedWith(
                compareBy(
                    { severityRank(it) },
                    { -relativeMiss(it) },
                    { it.ruleId.reasonPriority }
                )
            )
            .map { toReason(it) }

        val caveats = mutableListOf<Reason>()
        if (recommendation == Recommendation.BORDERLINE && confidence == OfferConfidence.PARTIAL) {
            val note = notes.firstOrNull { it.degradesConfidence }
            caveats += Reason(
                headline = "Partly read",
                detail = note?.message ?: "Some of the offer could not be read cleanly",
                severity = MetricStatus.NOT_EVALUATED
            )
        }

        return problems + caveats
    }

    fun unknownReasons(reason: UnreadableReason): List<Reason> = listOf(
        Reason(
            headline = reason.shortText,
            detail = reason.detailText,
            severity = MetricStatus.NOT_EVALUATED
        )
    )

    private fun toReason(result: MetricResult): Reason {
        val rule = result.ruleId
        val actual = result.actual

        if (result.status == MetricStatus.NOT_EVALUATED || actual == null) {
            return Reason(
                headline = "${rule.metricLabel} not shown",
                detail = "Uber did not display this value, so the rule could not be checked",
                severity = MetricStatus.NOT_EVALUATED
            )
        }

        val valueText = Formats.actual(rule.unit, actual)
        val headline = if (rule.shortName.isBlank()) valueText else "${rule.shortName} $valueText"
        val targetText = Formats.targetShort(rule.unit, result.target)

        val detail = when (rule.direction) {
            RuleDirection.MINIMUM ->
                if (result.status == MetricStatus.RED) "Below $targetText target" else "Target $targetText"

            RuleDirection.MAXIMUM ->
                if (result.status == MetricStatus.RED) "Maximum $targetText" else "Limit $targetText"
        }

        val hardPrefix = if (result.importance == RuleImportance.HARD) "Must-pass rule · " else ""
        return Reason(headline = headline, detail = hardPrefix + detail, severity = result.status)
    }

    private fun severityRank(result: MetricResult): Int = when {
        result.isHardFailure -> 0
        result.status == MetricStatus.RED -> 1
        result.status == MetricStatus.AMBER -> 2
        else -> 3
    }

    /** How badly the metric missed its target, as a fraction, so the worst problem sorts first. */
    private fun relativeMiss(result: MetricResult): Double {
        val actual = result.actual ?: return 0.0
        val target = result.target
        if (target == 0.0) return 0.0
        return abs(actual - target) / abs(target)
    }
}
