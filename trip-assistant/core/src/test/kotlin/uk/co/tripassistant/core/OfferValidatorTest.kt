package uk.co.tripassistant.core

import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.model.UnreadableReason
import uk.co.tripassistant.core.validation.OfferValidator
import uk.co.tripassistant.core.validation.ValidationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Spec section 16 — nothing gets scored until it makes sense. */
class OfferValidatorTest {

    private fun raw(
        fare: Double? = 18.0,
        pickupMiles: Double? = 2.0,
        tripMiles: Double? = 8.0,
        pickupMinutes: Double? = 6.0,
        tripMinutes: Double? = 24.0,
        rating: Double? = 4.91,
        notes: List<ParseNote> = emptyList()
    ) = RawOffer(
        fareGbp = fare,
        pickupMiles = pickupMiles,
        tripMiles = tripMiles,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        riderRating = rating,
        parserVersion = "TEST",
        notes = notes
    )

    private fun reasonFor(offer: RawOffer): UnreadableReason =
        assertIs<ValidationOutcome.Invalid>(OfferValidator.validate(offer)).reason

    @Test
    fun `a complete sensible offer validates`() {
        val outcome = assertIs<ValidationOutcome.Valid>(OfferValidator.validate(raw()))
        assertEquals(18.0, outcome.offer.fareGbp)
        assertEquals(OfferConfidence.HIGH, outcome.offer.confidence)
    }

    @Test
    fun `a missing fare is fatal`() {
        assertEquals(UnreadableReason.MISSING_FARE, reasonFor(raw(fare = null)))
    }

    @Test
    fun `a missing trip distance is fatal`() {
        assertEquals(UnreadableReason.MISSING_DISTANCE, reasonFor(raw(tripMiles = null)))
    }

    @Test
    fun `a missing pickup distance is fatal`() {
        assertEquals(UnreadableReason.MISSING_DISTANCE, reasonFor(raw(pickupMiles = null)))
    }

    @Test
    fun `missing times are allowed — not every offer shows them`() {
        assertIs<ValidationOutcome.Valid>(
            OfferValidator.validate(raw(pickupMinutes = null, tripMinutes = null))
        )
    }

    @Test
    fun `a missing rating is allowed`() {
        assertIs<ValidationOutcome.Valid>(OfferValidator.validate(raw(rating = null)))
    }

    @Test
    fun `a negative fare is rejected`() {
        assertEquals(UnreadableReason.IMPLAUSIBLE_VALUES, reasonFor(raw(fare = -4.0)))
    }

    @Test
    fun `an absurd fare is rejected`() {
        assertEquals(UnreadableReason.IMPLAUSIBLE_VALUES, reasonFor(raw(fare = 7_412.0)))
    }

    @Test
    fun `a rating above five is rejected`() {
        assertEquals(UnreadableReason.IMPLAUSIBLE_VALUES, reasonFor(raw(rating = 6.2)))
    }

    @Test
    fun `zero trip distance is rejected rather than divided by`() {
        assertEquals(UnreadableReason.IMPLAUSIBLE_VALUES, reasonFor(raw(tripMiles = 0.0)))
    }

    @Test
    fun `a zero mile pickup is fine`() {
        assertIs<ValidationOutcome.Valid>(OfferValidator.validate(raw(pickupMiles = 0.0)))
    }

    @Test
    fun `an impossible pounds per mile means the values contradict each other`() {
        // £18 over 300 miles is £0.06/mile: one of the two numbers was misread.
        assertEquals(
            UnreadableReason.CONTRADICTORY_VALUES,
            reasonFor(raw(pickupMiles = 2.0, tripMiles = 298.0, tripMinutes = 500.0))
        )
    }

    @Test
    fun `an impossible average speed means the values contradict each other`() {
        // 60 miles in 20 minutes is 180mph — the time or the distance was misread.
        assertEquals(
            UnreadableReason.CONTRADICTORY_VALUES,
            reasonFor(raw(fare = 90.0, pickupMiles = 2.0, tripMiles = 58.0, pickupMinutes = 4.0, tripMinutes = 16.0))
        )
    }

    @Test
    fun `an assumption in the parse lowers confidence to partial`() {
        val outcome = assertIs<ValidationOutcome.Valid>(
            OfferValidator.validate(raw(notes = listOf(ParseNote.PICKUP_TRIP_ORDER_ASSUMED)))
        )
        assertEquals(OfferConfidence.PARTIAL, outcome.offer.confidence)
    }

    @Test
    fun `a safe digit repair does not lower confidence`() {
        val outcome = assertIs<ValidationOutcome.Valid>(
            OfferValidator.validate(raw(notes = listOf(ParseNote.OCR_DIGIT_CORRECTED)))
        )
        assertEquals(OfferConfidence.HIGH, outcome.offer.confidence)
    }
}
