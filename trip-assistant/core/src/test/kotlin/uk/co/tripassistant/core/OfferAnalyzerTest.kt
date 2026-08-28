package uk.co.tripassistant.core

import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.UnreadableReason
import uk.co.tripassistant.core.parser.ScreenType
import uk.co.tripassistant.core.pipeline.OfferAnalyzer
import uk.co.tripassistant.core.rules.DefaultProfiles
import uk.co.tripassistant.core.text.OcrText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End to end: recognised text in, recommendation out — the whole
 * Observe -> Read -> Validate -> Calculate -> Evaluate pipeline of spec section 63.
 */
class OfferAnalyzerTest {

    private val analyzer = OfferAnalyzer()

    private fun textOf(sample: ParserSample) = OcrText.ofLines(*sample.lines.toTypedArray())

    @Test
    fun `the spec section 22 offer produces the spec section 22 overlay`() {
        val text = OcrText.ofLines(
            "£18.00",
            "★ 4.91",
            "6 mins (2.0 mi) away",
            "24 mins (8.0 mi) trip"
        )
        val result = analyzer.analyze(text, TestProfiles.specSection22())
        val evaluation = assertNotNull(result.evaluation)
        val metrics = assertNotNull(evaluation.metrics)

        assertEquals(Recommendation.GOOD, evaluation.recommendation)
        assertEquals(10.0, metrics.totalMiles, 1e-9)
        assertEquals(30.0, metrics.totalMinutes!!, 1e-9)
        assertEquals(20.0, metrics.pickupPercentage, 1e-9)

        // "GOOD  £1.80/mi · £36/h  Pickup 2.0 mi · ★4.91"
        assertEquals("£1.80/mi", Formats.poundsPerMile(metrics.poundsPerMile))
        assertEquals("£36/h", Formats.poundsPerHour(metrics.poundsPerHour!!))
        assertEquals("2.0 mi", Formats.miles(metrics.pickupMiles))
        assertEquals("★4.91", Formats.rating(metrics.riderRating!!))
    }

    @Test
    fun `a long pickup on a small fare is POOR with the pickup named first`() {
        val result = analyzer.analyze(
            textOf(ParserSamples.byId("UK_V1_LONG_PICKUP")),
            DefaultProfiles.normal(id = 1L)
        )
        val evaluation = assertNotNull(result.evaluation)
        assertEquals(Recommendation.POOR, evaluation.recommendation)
        assertTrue(
            evaluation.reasons.first().headline.startsWith("Pickup"),
            "expected the pickup to be the headline problem, got ${evaluation.reasons}"
        )
    }

    @Test
    fun `a screen that is not an offer produces no recommendation at all`() {
        val result = analyzer.analyze(
            textOf(ParserSamples.byId("NOT_OFFER_WAITING")),
            DefaultProfiles.normal(id = 1L)
        )
        assertNull(result.evaluation, "the assistant must stay quiet, not flash UNKNOWN")
        assertEquals(ScreenType.NOT_OFFER, result.diagnostics.screenType)
    }

    @Test
    fun `an offer-shaped screen the parsers cannot read is UNKNOWN`() {
        // Currency, a distance and offer wording, but nothing a parser can associate.
        val text = OcrText.ofLines("Accept", "£14.00", "3.1 mi", "somewhere")
        val result = analyzer.analyze(text, DefaultProfiles.normal(id = 1L))
        val evaluation = assertNotNull(result.evaluation)
        assertEquals(Recommendation.UNKNOWN, evaluation.recommendation)
        assertNotNull(evaluation.unreadable)
    }

    @Test
    fun `an unreadable fare is UNKNOWN, never a guess`() {
        val text = OcrText.ofLines(
            "★ 4.91",
            "7 mins (2.4 mi) away",
            "26 mins (9.1 mi) trip"
        )
        val result = analyzer.analyze(text, DefaultProfiles.normal(id = 1L))
        val evaluation = assertNotNull(result.evaluation)
        assertEquals(Recommendation.UNKNOWN, evaluation.recommendation)
        assertEquals(UnreadableReason.MISSING_FARE, evaluation.unreadable)
    }

    @Test
    fun `an assumed layout can never come out GOOD`() {
        // The compact card has no away/trip wording, so the order is assumed. Even though the
        // economics are strong, spec section 49 forbids a green light on an incomplete read.
        val profile = TestProfiles.of(RuleId.MIN_POUNDS_PER_MILE to TestProfiles.soft(0.50))
        val result = analyzer.analyze(textOf(ParserSamples.byId("UK_V2_UNLABELLED_COMPACT")), profile)
        val evaluation = assertNotNull(result.evaluation)
        assertEquals(Recommendation.BORDERLINE, evaluation.recommendation)
    }

    @Test
    fun `diagnostics report every field, found or not`() {
        val result = analyzer.analyze(
            textOf(ParserSamples.byId("UK_V1_INLINE_NO_RATING")),
            DefaultProfiles.normal(id = 1L)
        )
        val fields = result.diagnostics.fields
        assertEquals(6, fields.size)
        assertEquals("UBER_UK_STANDARD_V1", result.diagnostics.parserVersion)
        assertTrue(fields.single { it.label == "Fare" }.found)
        assertTrue(!fields.single { it.label == "Rider rating" }.found)
        assertEquals("£7.20", fields.single { it.label == "Fare" }.value)
    }

    @Test
    fun `every sample in the library runs through the pipeline without throwing`() {
        for (sample in ParserSamples.all) {
            for (profile in DefaultProfiles.starterSet()) {
                analyzer.analyze(textOf(sample), profile)
            }
        }
    }
}
