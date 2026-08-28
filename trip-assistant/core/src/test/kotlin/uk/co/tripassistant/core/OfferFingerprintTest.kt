package uk.co.tripassistant.core

import uk.co.tripassistant.core.dedupe.OfferFingerprint
import uk.co.tripassistant.core.model.OfferMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Spec section 29 — the same offer read forty times is still one offer. */
class OfferFingerprintTest {

    private fun metrics(
        fare: Double = 18.50,
        pickupMiles: Double = 2.4,
        tripMiles: Double = 9.1,
        pickupMinutes: Double? = 7.0,
        tripMinutes: Double? = 26.0,
        rating: Double? = 4.91
    ): OfferMetrics {
        val total = pickupMiles + tripMiles
        return OfferMetrics(
            fareGbp = fare,
            pickupMiles = pickupMiles,
            tripMiles = tripMiles,
            totalMiles = total,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else null,
            riderRating = rating,
            poundsPerMile = fare / total,
            passengerPoundsPerMile = fare / tripMiles,
            poundsPerHour = null,
            pickupPercentage = pickupMiles / total * 100.0
        )
    }

    @Test
    fun `the same offer produces the same fingerprint`() {
        assertEquals(OfferFingerprint.of(metrics()), OfferFingerprint.of(metrics()))
    }

    @Test
    fun `a different fare produces a different fingerprint`() {
        assertNotEquals(OfferFingerprint.of(metrics()), OfferFingerprint.of(metrics(fare = 19.50)))
    }

    @Test
    fun `tiny OCR jitter still counts as the same offer`() {
        assertTrue(OfferFingerprint.isSameOffer(metrics(), metrics(pickupMiles = 2.44, tripMiles = 9.06)))
    }

    @Test
    fun `a genuinely different offer is not the same offer`() {
        assertFalse(OfferFingerprint.isSameOffer(metrics(), metrics(tripMiles = 14.0)))
    }

    @Test
    fun `a field lost between frames does not break the match`() {
        assertTrue(OfferFingerprint.isSameOffer(metrics(), metrics(rating = null)))
    }

    @Test
    fun `the canonical key is readable for diagnostics`() {
        assertEquals("f18.50|p2.4|t9.1|pm7|tm26|r4.91", OfferFingerprint.canonical(metrics()))
    }
}
