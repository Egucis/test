package uk.co.tripassistant.core

import uk.co.tripassistant.core.model.MetricStatus
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.OfferMetrics
import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.model.RuleDirection
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.UnreadableReason
import uk.co.tripassistant.core.rules.RuleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Spec sections 20, 21, 22 and 56. */
class RuleEngineTest {

    private fun metrics(
        fare: Double = 18.0,
        pickupMiles: Double = 2.0,
        tripMiles: Double = 8.0,
        pickupMinutes: Double? = 6.0,
        tripMinutes: Double? = 24.0,
        rating: Double? = 4.91,
        poundsPerMile: Double = 1.80,
        poundsPerHour: Double? = 36.0,
        pickupPercentage: Double = 20.0
    ) = OfferMetrics(
        fareGbp = fare,
        pickupMiles = pickupMiles,
        tripMiles = tripMiles,
        totalMiles = pickupMiles + tripMiles,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        totalMinutes = if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else null,
        riderRating = rating,
        poundsPerMile = poundsPerMile,
        passengerPoundsPerMile = fare / tripMiles,
        poundsPerHour = poundsPerHour,
        pickupPercentage = pickupPercentage
    )

    // --- spec section 20: the traffic light -------------------------------------------------

    @Test
    fun `minimum rule exactly on target is green`() {
        assertEquals(
            MetricStatus.GREEN,
            RuleEngine.statusFor(RuleDirection.MINIMUM, actual = 1.50, target = 1.50, toleranceFraction = 0.10)
        )
    }

    @Test
    fun `minimum rule one percent below target is amber`() {
        assertEquals(
            MetricStatus.AMBER,
            RuleEngine.statusFor(RuleDirection.MINIMUM, actual = 1.485, target = 1.50, toleranceFraction = 0.10)
        )
    }

    @Test
    fun `minimum rule at the bottom of the amber band is still amber`() {
        // Spec section 20: with a £1.50 target and 10% tolerance, £1.35 is amber, not red.
        assertEquals(
            MetricStatus.AMBER,
            RuleEngine.statusFor(RuleDirection.MINIMUM, actual = 1.35, target = 1.50, toleranceFraction = 0.10)
        )
    }

    @Test
    fun `minimum rule beyond the tolerance is red`() {
        assertEquals(
            MetricStatus.RED,
            RuleEngine.statusFor(RuleDirection.MINIMUM, actual = 1.34, target = 1.50, toleranceFraction = 0.10)
        )
    }

    @Test
    fun `maximum rule follows the spec pickup example`() {
        // Spec section 20: max pickup 4.0 mi -> green to 4.0, amber to 4.4, red beyond.
        val tolerance = 0.10
        assertEquals(MetricStatus.GREEN, RuleEngine.statusFor(RuleDirection.MAXIMUM, 4.0, 4.0, tolerance))
        assertEquals(MetricStatus.AMBER, RuleEngine.statusFor(RuleDirection.MAXIMUM, 4.01, 4.0, tolerance))
        assertEquals(MetricStatus.AMBER, RuleEngine.statusFor(RuleDirection.MAXIMUM, 4.4, 4.0, tolerance))
        assertEquals(MetricStatus.RED, RuleEngine.statusFor(RuleDirection.MAXIMUM, 4.41, 4.0, tolerance))
    }

    @Test
    fun `tolerance is configurable`() {
        assertEquals(
            MetricStatus.RED,
            RuleEngine.statusFor(RuleDirection.MINIMUM, actual = 1.45, target = 1.50, toleranceFraction = 0.01)
        )
    }

    // --- spec section 21: the overall recommendation -----------------------------------------

    @Test
    fun `the worked example from spec section 22 is GOOD`() {
        val result = RuleEngine.evaluateMetrics(metrics(), TestProfiles.specSection22())
        assertEquals(Recommendation.GOOD, result.recommendation)
        assertTrue(result.reasons.isEmpty(), "a GOOD offer needs no failure reasons")
    }

    @Test
    fun `one soft red is BORDERLINE`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(2.50),
            RuleId.MIN_POUNDS_PER_HOUR to TestProfiles.soft(25.0)
        )
        val result = RuleEngine.evaluateMetrics(metrics(), profile)
        assertEquals(Recommendation.BORDERLINE, result.recommendation)
    }

    @Test
    fun `two soft reds are POOR`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(2.50),
            RuleId.MIN_POUNDS_PER_HOUR to TestProfiles.soft(60.0)
        )
        val result = RuleEngine.evaluateMetrics(metrics(), profile)
        assertEquals(Recommendation.POOR, result.recommendation)
    }

    @Test
    fun `an amber metric with no reds is BORDERLINE`() {
        val profile = TestProfiles.of(RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(1.90))
        val result = RuleEngine.evaluateMetrics(metrics(poundsPerMile = 1.80), profile)
        assertEquals(Recommendation.BORDERLINE, result.recommendation)
        assertEquals(MetricStatus.AMBER, result.metricResults.single().status)
    }

    @Test
    fun `a hard rule failing makes the offer POOR on its own`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(1.50),
            RuleId.MIN_RIDER_RATING to TestProfiles.hard(4.90)
        )
        // 4.30 is well outside a 10% band below 4.90, so the hard rule is RED.
        val result = RuleEngine.evaluateMetrics(metrics(rating = 4.30), profile)
        assertEquals(Recommendation.POOR, result.recommendation)
        assertEquals("Rider ★4.30", result.primaryReason?.headline)
    }

    @Test
    fun `a hard rule inside the amber band does not force POOR`() {
        // Spec section 21 lists "one or more enabled metrics are AMBER" under BORDERLINE, so a
        // HARD rule only *fails* when it is RED.
        val profile = TestProfiles.of(RuleId.MAX_PICKUP_MILES to TestProfiles.hard(4.0))
        val result = RuleEngine.evaluateMetrics(metrics(pickupMiles = 4.2), profile)
        assertEquals(Recommendation.BORDERLINE, result.recommendation)
    }

    @Test
    fun `rules that are switched off are ignored`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(1.50),
            RuleId.MIN_RIDER_RATING to TestProfiles.off(4.99)
        )
        val result = RuleEngine.evaluateMetrics(metrics(rating = 4.10), profile)
        assertEquals(Recommendation.GOOD, result.recommendation)
        assertEquals(1, result.metricResults.size)
    }

    // --- spec sections 49 and 63: incomplete data can never be GOOD --------------------------

    @Test
    fun `a hard rule whose value was not shown is UNKNOWN, never a pass`() {
        val profile = TestProfiles.of(RuleId.MIN_RIDER_RATING to TestProfiles.hard(4.75))
        val result = RuleEngine.evaluateMetrics(metrics(rating = null), profile)
        assertEquals(Recommendation.UNKNOWN, result.recommendation)
        assertEquals(UnreadableReason.MISSING_RATING, result.unreadable)
    }

    @Test
    fun `a soft rule whose value was not shown caps the offer at BORDERLINE`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(1.50),
            RuleId.MIN_POUNDS_PER_HOUR to TestProfiles.soft(25.0)
        )
        val result = RuleEngine.evaluateMetrics(metrics(poundsPerHour = null, tripMinutes = null), profile)
        assertEquals(Recommendation.BORDERLINE, result.recommendation)
        val notShown = result.metricResults.single { it.ruleId == RuleId.MIN_POUNDS_PER_HOUR }
        assertEquals(MetricStatus.NOT_EVALUATED, notShown.status)
        assertTrue(result.reasons.any { it.headline.contains("not shown") })
    }

    @Test
    fun `a partly read offer is never GOOD`() {
        val result = RuleEngine.evaluateMetrics(
            metrics = metrics(),
            profile = TestProfiles.specSection22(),
            confidence = OfferConfidence.PARTIAL,
            notes = listOf(ParseNote.PICKUP_TRIP_ORDER_ASSUMED)
        )
        assertEquals(Recommendation.BORDERLINE, result.recommendation)
        assertTrue(result.reasons.any { it.headline == "Partly read" })
    }

    @Test
    fun `a partly read offer that is genuinely poor stays POOR`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(3.00),
            RuleId.MIN_POUNDS_PER_HOUR to TestProfiles.soft(60.0)
        )
        val result = RuleEngine.evaluateMetrics(
            metrics = metrics(),
            profile = profile,
            confidence = OfferConfidence.PARTIAL,
            notes = listOf(ParseNote.PICKUP_TRIP_ORDER_ASSUMED)
        )
        assertEquals(Recommendation.POOR, result.recommendation)
    }

    // --- spec section 23: reason text ---------------------------------------------------------

    @Test
    fun `reason text matches the spec examples`() {
        val profile = TestProfiles.of(RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(1.50))

        // Note the recommendation: one soft RED on its own is BORDERLINE under spec section 21.
        // The spec section 23 sample shows this same reason text under a POOR heading, which is
        // what it becomes as soon as a second rule fails or the rule is made HARD.
        val red = RuleEngine.evaluateMetrics(metrics(poundsPerMile = 0.96), profile)
        assertEquals(Recommendation.BORDERLINE, red.recommendation)
        assertEquals("£0.96/mi", red.primaryReason?.headline)
        assertEquals("Below £1.50 target", red.primaryReason?.detail)

        val borderline = RuleEngine.evaluateMetrics(metrics(poundsPerMile = 1.43), profile)
        assertEquals("£1.43/mi", borderline.primaryReason?.headline)
        assertEquals("Target £1.50", borderline.primaryReason?.detail)
    }

    @Test
    fun `a pickup that is too long reads like the spec example`() {
        val profile = TestProfiles.of(RuleId.MAX_PICKUP_MILES to TestProfiles.soft(4.0))
        val result = RuleEngine.evaluateMetrics(metrics(pickupMiles = 6.2), profile)
        assertEquals("Pickup 6.2 mi", result.primaryReason?.headline)
        assertEquals("Maximum 4.0 mi", result.primaryReason?.detail)
    }

    @Test
    fun `the most important problem is listed first`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(1.90),   // amber
            RuleId.MAX_PICKUP_MILES to TestProfiles.hard(1.0)        // hard red
        )
        val result = RuleEngine.evaluateMetrics(metrics(poundsPerMile = 1.80, pickupMiles = 6.0), profile)
        assertEquals(Recommendation.POOR, result.recommendation)
        val first = assertNotNull(result.primaryReason)
        assertEquals("Pickup 6.0 mi", first.headline)
        assertTrue(first.detail.startsWith("Must-pass rule"))
    }

    @Test
    fun `the worst red sorts above a milder red`() {
        val profile = TestProfiles.of(
            RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(2.20),   // 1.80 -> 18% under, red
            RuleId.MIN_POUNDS_PER_HOUR to TestProfiles.soft(90.0)    // 36 -> 60% under, red
        )
        val result = RuleEngine.evaluateMetrics(metrics(), profile)
        assertEquals(Recommendation.POOR, result.recommendation)
        assertEquals("£36/h", result.primaryReason?.headline)
    }
}
