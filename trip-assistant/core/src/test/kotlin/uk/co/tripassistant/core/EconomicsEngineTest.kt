package uk.co.tripassistant.core

import uk.co.tripassistant.core.economics.EconomicsEngine
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.ValidatedOffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The worked arithmetic of spec section 17. */
class EconomicsEngineTest {

    private fun offer(
        fare: Double = 18.0,
        pickupMiles: Double = 2.0,
        tripMiles: Double = 8.0,
        pickupMinutes: Double? = 6.0,
        tripMinutes: Double? = 24.0,
        rating: Double? = 4.91
    ) = ValidatedOffer(
        fareGbp = fare,
        pickupMiles = pickupMiles,
        tripMiles = tripMiles,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        riderRating = rating,
        parserVersion = "TEST",
        confidence = OfferConfidence.HIGH,
        notes = emptyList()
    )

    @Test
    fun `total distance is pickup plus trip`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer()))
        assertEquals(10.0, metrics.totalMiles, 1e-9)
    }

    @Test
    fun `total time is pickup plus trip`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer()))
        assertEquals(30.0, metrics.totalMinutes!!, 1e-9)
    }

    @Test
    fun `effective pounds per mile uses total distance`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer()))
        assertEquals(1.80, metrics.poundsPerMile, 1e-9)
    }

    @Test
    fun `passenger pounds per mile uses trip distance only`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer()))
        assertEquals(2.25, metrics.passengerPoundsPerMile, 1e-9)
    }

    @Test
    fun `pounds per hour uses total time`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer()))
        assertEquals(36.0, metrics.poundsPerHour!!, 1e-9)
    }

    @Test
    fun `pickup proportion matches the spec example`() {
        // Spec section 17.6: a 4 mile pickup on a 6 mile trip is 40% of the driving.
        val metrics = assertNotNull(
            EconomicsEngine.calculate(offer(pickupMiles = 4.0, tripMiles = 6.0))
        )
        assertEquals(40.0, metrics.pickupPercentage, 1e-9)
    }

    @Test
    fun `pounds per hour is null when Uber did not show both times`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer(tripMinutes = null)))
        assertNull(metrics.poundsPerHour)
        assertNull(metrics.totalMinutes)
    }

    @Test
    fun `zero trip distance produces no metrics rather than infinity`() {
        assertNull(EconomicsEngine.calculate(offer(pickupMiles = 0.0, tripMiles = 0.0)))
    }

    @Test
    fun `zero total time does not divide by zero`() {
        val metrics = assertNotNull(
            EconomicsEngine.calculate(offer(pickupMinutes = 0.0, tripMinutes = 0.0))
        )
        assertNull(metrics.poundsPerHour)
    }

    @Test
    fun `a rider standing at the car is a valid zero mile pickup`() {
        val metrics = assertNotNull(EconomicsEngine.calculate(offer(pickupMiles = 0.0)))
        assertEquals(0.0, metrics.pickupPercentage, 1e-9)
        assertEquals(2.25, metrics.poundsPerMile, 1e-9)
    }
}
