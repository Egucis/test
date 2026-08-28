package uk.co.tripassistant.core

import uk.co.tripassistant.core.outcome.OutcomeDetector
import uk.co.tripassistant.core.outcome.PostOfferSignal
import uk.co.tripassistant.core.text.OcrText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Spec section 30 — an outcome is only recorded on real evidence. */
class OutcomeDetectorTest {

    @Test
    fun `an in-trip screen is strong evidence of acceptance`() {
        val text = OcrText.ofLines("Navigate to pickup", "3 min", "Cancel trip")
        assertEquals(PostOfferSignal.TRIP_ACCEPTED, OutcomeDetector.signal(text))
    }

    @Test
    fun `an offer card is not evidence of acceptance`() {
        val text = OcrText.ofLines(*ParserSamples.byId("UK_V1_INLINE_STANDARD").lines.toTypedArray())
        assertEquals(PostOfferSignal.NONE, OutcomeDetector.signal(text))
    }

    @Test
    fun `an offer simply disappearing is never an outcome`() {
        // The map screen after an offer times out. Nothing here may become "declined".
        val text = OcrText.ofLines("You're online", "Looking for trips")
        assertEquals(PostOfferSignal.NONE, OutcomeDetector.signal(text))
    }

    @Test
    fun `the word trip on its own proves nothing`() {
        val text = OcrText.ofLines("12 trips today", "Navigate")
        assertEquals(PostOfferSignal.NONE, OutcomeDetector.signal(text))
    }
}
