package uk.co.tripassistant.core.parser

import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.text.OcrText

/**
 * One way of reading an Uber offer card.
 *
 * Parsers are versioned (spec section 13) so that an Uber redesign is handled by adding a new
 * implementation rather than editing the old one — the historical layouts keep working, and the
 * test samples recorded against them keep passing (spec section 55).
 */
interface OfferParser {

    /** Stable identifier stored on every history row, e.g. "UBER_UK_STANDARD_V1". */
    val version: String

    /** Returns null when this parser does not recognise the layout. */
    fun parse(text: OcrText): RawOffer?
}

/** Which half of the journey a group of numbers describes. */
internal enum class Leg { PICKUP, TRIP }
