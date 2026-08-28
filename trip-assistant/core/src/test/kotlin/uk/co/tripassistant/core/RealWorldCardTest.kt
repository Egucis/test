package uk.co.tripassistant.core

import uk.co.tripassistant.core.outcome.OutcomeDetector
import uk.co.tripassistant.core.outcome.PostOfferSignal
import uk.co.tripassistant.core.parser.ParserRegistry
import uk.co.tripassistant.core.pipeline.OfferAnalyzer
import uk.co.tripassistant.core.rules.DefaultProfiles
import uk.co.tripassistant.core.text.OcrText
import uk.co.tripassistant.core.model.Recommendation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regressions found by transcribing real UK Uber Driver cards.
 *
 * Everything here failed, or was one OCR quirk away from failing, against the layouts guessed
 * before real cards were available. Each test names the trap it exists to catch.
 */
class RealWorldCardTest {

    private val analyzer = OfferAnalyzer()
    private val registry = ParserRegistry()

    private fun textOf(id: String) =
        OcrText.ofLines(*ParserSamples.byId(id).lines.toTypedArray())

    @Test
    fun `the holiday pay breakdown line is not mistaken for the fare`() {
        // Real cards carry "£8.85 + est. holiday pay of £0.19" under the fare. Both amounts are
        // components of the £9.04 headline; either one taken as the fare understates the trip.
        val candidate = assertNotNull(registry.parse(textOf("UK_2026_UBERX_EXCLUSIVE_LONG_PICKUP")))
        assertEquals(9.04, candidate.offer.fareGbp)
        assertTrue(candidate.offer.notes.isEmpty(), "a clean card should need no assumptions")
    }

    @Test
    fun `a rating-shaped fare is never read as a rating`() {
        // "£4.62" matches the shape of a rating exactly. Without the currency guard this card
        // would report a 4.62 rider rating that was never on screen.
        val text = OcrText.ofLines(
            "UberX",
            "£4.62",
            "5 mins (1.7 mi) away",
            "7 mins (2.3 mi) trip",
            "Confirm"
        )
        val candidate = assertNotNull(registry.parse(text))
        assertEquals(4.62, candidate.offer.fareGbp)
        assertNull(candidate.offer.riderRating, "there is no rating on this card")
    }

    @Test
    fun `a rating survives OCR mangling the star glyph`() {
        // ML Kit's Latin recogniser has no obligation to return "★". If a lost glyph downgraded
        // the read, every offer would be capped at BORDERLINE and the app would look broken.
        listOf("★ 4.88", "4.88", "* 4.88", "A 4.88", "☆4.88").forEach { ratingLine ->
            val text = OcrText.ofLines(
                "UberX",
                "£9.04",
                ratingLine,
                "12 mins (4.3 mi) away",
                "10 mins (4.0 mi) trip"
            )
            val candidate = assertNotNull(registry.parse(text), "failed on: $ratingLine")
            assertEquals(4.88, candidate.offer.riderRating, "failed on: $ratingLine")
            assertTrue(candidate.offer.notes.isEmpty(), "should be a confident read: $ratingLine")
        }
    }

    @Test
    fun `the fast-charger line does not disturb the legs`() {
        // "1 mi from fast charger" sits below the trip line and is a perfectly good distance.
        val candidate = assertNotNull(registry.parse(textOf("UK_2026_UBERX_EXCLUSIVE_SHORT_PICKUP")))
        assertEquals(0.5, candidate.offer.pickupMiles)
        assertEquals(5.0, candidate.offer.tripMiles)
    }

    @Test
    fun `addresses between the legs are ignored`() {
        val candidate = assertNotNull(registry.parse(textOf("UK_2026_UBERX_MATCHED")))
        assertEquals(1.7, candidate.offer.pickupMiles)
        assertEquals(5.0, candidate.offer.pickupMinutes)
        assertEquals(2.3, candidate.offer.tripMiles)
        assertEquals(7.0, candidate.offer.tripMinutes)
    }

    @Test
    fun `the Matched screen is evidence the trip was accepted`() {
        // Accepting replaces "Confirm" with a "Matched" heading and a "Let's go" button, keeping
        // the same card — so this screen still parses as an offer and the accepted signal has to
        // be checked independently of parsing (spec section 30).
        assertEquals(PostOfferSignal.TRIP_ACCEPTED, OutcomeDetector.signal(textOf("UK_2026_UBERX_MATCHED")))
    }

    @Test
    fun `an open offer is not evidence of acceptance`() {
        assertEquals(
            PostOfferSignal.NONE,
            OutcomeDetector.signal(textOf("UK_2026_UBERX_EXCLUSIVE_LONG_PICKUP"))
        )
    }

    // --- what a driver on the Normal profile would actually have been told ---------------------

    @Test
    fun `a 4 point 3 mile pickup for a 4 mile trip is POOR, and says why`() {
        val result = analyzer.analyze(
            textOf("UK_2026_UBERX_EXCLUSIVE_LONG_PICKUP"),
            DefaultProfiles.normal(id = 1L)
        )
        val evaluation = assertNotNull(result.evaluation)
        val metrics = assertNotNull(evaluation.metrics)

        assertEquals(8.3, metrics.totalMiles, 1e-9)
        assertEquals(1.089, metrics.poundsPerMile, 0.001)
        assertEquals(24.65, metrics.poundsPerHour!!, 0.01)
        assertEquals(51.8, metrics.pickupPercentage, 0.1)

        assertEquals(Recommendation.POOR, evaluation.recommendation)
        // More than half the driving is unpaid — that is the headline, not the £/mile.
        assertEquals("Pickup 52%", evaluation.primaryReason?.headline)
        assertEquals("Maximum 35%", evaluation.primaryReason?.detail)
    }

    @Test
    fun `a close pickup on a thin fare is BORDERLINE on the mileage rate`() {
        val result = analyzer.analyze(
            textOf("UK_2026_UBERX_EXCLUSIVE_SHORT_PICKUP"),
            DefaultProfiles.normal(id = 1L)
        )
        val evaluation = assertNotNull(result.evaluation)
        assertEquals(Recommendation.BORDERLINE, evaluation.recommendation)
        assertEquals("£1.23/mi", evaluation.primaryReason?.headline)
        assertEquals("Below £1.50 target", evaluation.primaryReason?.detail)
    }

    @Test
    fun `the accepted card scores the same as the offer it came from`() {
        val result = analyzer.analyze(textOf("UK_2026_UBERX_MATCHED"), DefaultProfiles.normal(id = 1L))
        val evaluation = assertNotNull(result.evaluation)
        val metrics = assertNotNull(evaluation.metrics)
        assertEquals(4.0, metrics.totalMiles, 1e-9)
        assertEquals(1.27, metrics.poundsPerMile, 0.001)
        assertEquals(Recommendation.POOR, evaluation.recommendation)
    }
}
