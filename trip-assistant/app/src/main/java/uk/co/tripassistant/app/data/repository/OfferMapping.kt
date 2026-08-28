package uk.co.tripassistant.app.data.repository

import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity
import uk.co.tripassistant.core.dedupe.OfferFingerprint
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.OfferMetrics
import uk.co.tripassistant.core.model.OfferOutcome

/**
 * Between the decision engine's result and the history row.
 *
 * Only economics and the decision cross this boundary. Pickup and destination text, which the
 * parser may have used as an anchor, is left behind here on purpose (spec section 40).
 */
fun OfferEvaluation.toEntity(timestamp: Long, outcome: OfferOutcome = OfferOutcome.SEEN): EvaluatedOfferEntity? {
    val metrics = metrics ?: return null
    return EvaluatedOfferEntity(
        timestamp = timestamp,
        fare = metrics.fareGbp,
        pickupMiles = metrics.pickupMiles,
        tripMiles = metrics.tripMiles,
        totalMiles = metrics.totalMiles,
        pickupMinutes = metrics.pickupMinutes,
        tripMinutes = metrics.tripMinutes,
        totalMinutes = metrics.totalMinutes,
        riderRating = metrics.riderRating,
        poundsPerMile = metrics.poundsPerMile,
        passengerPoundsPerMile = metrics.passengerPoundsPerMile,
        poundsPerHour = metrics.poundsPerHour,
        pickupPercentage = metrics.pickupPercentage,
        recommendation = recommendation,
        primaryReason = primaryReason?.let { "${it.headline} — ${it.detail}" },
        allReasons = reasons.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "${it.headline} — ${it.detail}" },
        profileId = profileId,
        profileName = profileName,
        outcome = outcome,
        parserVersion = parserVersion,
        fingerprint = OfferFingerprint.of(metrics),
        confidence = confidence
    )
}

fun EvaluatedOfferEntity.toMetrics(): OfferMetrics = OfferMetrics(
    fareGbp = fare,
    pickupMiles = pickupMiles,
    tripMiles = tripMiles,
    totalMiles = totalMiles,
    pickupMinutes = pickupMinutes,
    tripMinutes = tripMinutes,
    totalMinutes = totalMinutes,
    riderRating = riderRating,
    poundsPerMile = poundsPerMile,
    passengerPoundsPerMile = passengerPoundsPerMile,
    poundsPerHour = poundsPerHour,
    pickupPercentage = pickupPercentage
)
