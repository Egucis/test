package uk.co.tripassistant.core

import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.parser.ParserRegistry
import uk.co.tripassistant.core.parser.ScreenClassifier
import uk.co.tripassistant.core.parser.ScreenType
import uk.co.tripassistant.core.text.OcrText
import uk.co.tripassistant.core.text.Rect01
import uk.co.tripassistant.core.text.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the whole sample library of spec section 55 against the parsers. */
class UberParserTest {

    private val registry = ParserRegistry()

    private fun textOf(sample: ParserSample) = OcrText.ofLines(*sample.lines.toTypedArray())

    @Test
    fun `every sample is classified as expected`() {
        for (sample in ParserSamples.all) {
            val classification = ScreenClassifier.classify(textOf(sample))
            assertEquals(
                sample.expectedScreenType,
                classification.type,
                "${sample.id}: ${sample.description} (score ${classification.score}, ${classification.signals})"
            )
        }
    }

    @Test
    fun `every offer sample parses to its expected values`() {
        val offers = ParserSamples.all.filter { it.expectedScreenType != ScreenType.NOT_OFFER }
        assertTrue(offers.isNotEmpty())

        for (sample in offers) {
            val candidate = assertNotNull(registry.parse(textOf(sample)), "${sample.id} did not parse")
            val offer = candidate.offer

            assertEquals(sample.expectedParser, candidate.parserVersion, "${sample.id}: parser version")
            assertEqualsNullable(sample.expectedFare, offer.fareGbp, "${sample.id}: fare")
            assertEqualsNullable(sample.expectedPickupMiles, offer.pickupMiles, "${sample.id}: pickup miles")
            assertEqualsNullable(sample.expectedPickupMinutes, offer.pickupMinutes, "${sample.id}: pickup minutes")
            assertEqualsNullable(sample.expectedTripMiles, offer.tripMiles, "${sample.id}: trip miles")
            assertEqualsNullable(sample.expectedTripMinutes, offer.tripMinutes, "${sample.id}: trip minutes")
            assertEqualsNullable(sample.expectedRating, offer.riderRating, "${sample.id}: rating")
            assertEquals(sample.expectedNotes, offer.notes.toSet(), "${sample.id}: parse notes")
        }
    }

    @Test
    fun `a screen with nothing offer-like on it does not parse`() {
        val sample = ParserSamples.byId("NOT_OFFER_WAITING")
        assertNull(registry.parse(textOf(sample)))
    }

    @Test
    fun `a mileage figure is never mistaken for a rider rating`() {
        // Spec section 16 calls this out by name: "4.3 mi" must not become a 4.3 rating.
        val candidate = assertNotNull(registry.parse(textOf(ParserSamples.byId("UK_V2_UNLABELLED_COMPACT"))))
        assertNull(candidate.offer.riderRating)
    }

    @Test
    fun `an assumed pickup-trip order is recorded rather than hidden`() {
        val candidate = assertNotNull(registry.parse(textOf(ParserSamples.byId("UK_V2_UNLABELLED_COMPACT"))))
        assertTrue(ParseNote.PICKUP_TRIP_ORDER_ASSUMED in candidate.offer.notes)
        assertTrue(ParseNote.PICKUP_TRIP_ORDER_ASSUMED.degradesConfidence)
    }

    @Test
    fun `a promotion line is not mistaken for the fare`() {
        val candidate = assertNotNull(registry.parse(textOf(ParserSamples.byId("UK_V1_WITH_PROMOTION"))))
        assertEquals(11.00, candidate.offer.fareGbp)
        assertTrue(ParseNote.FARE_CHOSEN_BY_PROMINENCE !in candidate.offer.notes)
    }

    @Test
    fun `the overlay's own figures are excluded from the frame`() {
        // Spec section 27: a whole-screen capture contains our own overlay. Its expanded card
        // shows a fare in large text, so left in the frame it wins on prominence and the
        // assistant ends up scoring its own output.
        val offerLines = OcrText.ofLines(
            "£18.50",
            "★ 4.91",
            "7 mins (2.4 mi) away",
            "26 mins (9.1 mi) trip"
        ).lines
        val overlayRegion = Rect01(0.55f, 0.00f, 1.00f, 0.16f)
        val frame = OcrText(
            offerLines + listOf(
                TextLine("GOOD TRIP", Rect01(0.60f, 0.02f, 0.98f, 0.05f)),
                TextLine("£17.40", Rect01(0.60f, 0.06f, 0.98f, 0.15f))
            )
        )

        val readingItself = assertNotNull(registry.parse(frame))
        assertEquals(17.40, readingItself.offer.fareGbp, "without exclusion the overlay wins on prominence")

        val cleaned = assertNotNull(registry.parse(frame.excluding(listOf(overlayRegion))))
        assertEquals(18.50, cleaned.offer.fareGbp)
        assertEquals(2.4, cleaned.offer.pickupMiles)
        assertTrue(cleaned.offer.notes.isEmpty())
    }

    private fun assertEqualsNullable(expected: Double?, actual: Double?, message: String) {
        if (expected == null) {
            assertNull(actual, message)
        } else {
            assertNotNull(actual, message)
            assertEquals(expected, actual, 1e-6, message)
        }
    }
}
