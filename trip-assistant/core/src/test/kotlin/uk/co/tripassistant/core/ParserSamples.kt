package uk.co.tripassistant.core

import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.parser.ScreenType

/**
 * The permanent sample library of spec section 55.
 *
 * Each entry is the *recognised text* of one sanitised Uber offer screen — which is exactly what
 * the parser consumes — together with the values that screen is known to contain. Screenshots
 * themselves are not committed: they would carry rider information, and the parser never sees a
 * pixel anyway (see SPEC_COMPLIANCE.md).
 *
 * When Uber changes its interface, ADD a sample. Never edit an existing one: the old layout is
 * still on thousands of phones, and a sample that quietly changes stops being a regression test.
 */
data class ParserSample(
    val id: String,
    val description: String,
    val lines: List<String>,
    val expectedScreenType: ScreenType,
    val expectedParser: String? = null,
    val expectedFare: Double? = null,
    val expectedPickupMiles: Double? = null,
    val expectedPickupMinutes: Double? = null,
    val expectedTripMiles: Double? = null,
    val expectedTripMinutes: Double? = null,
    val expectedRating: Double? = null,
    val expectedNotes: Set<ParseNote> = emptySet()
)

object ParserSamples {

    val all: List<ParserSample> = listOf(
        ParserSample(
            id = "UK_V1_INLINE_STANDARD",
            description = "Current UK offer card: fare, star rating, two labelled inline legs",
            lines = listOf(
                "Exclusive",
                "£18.50",
                "★ 4.91",
                "7 mins (2.4 mi) away",
                "26 mins (9.1 mi) trip",
                "Accept"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 18.50,
            expectedPickupMiles = 2.4,
            expectedPickupMinutes = 7.0,
            expectedTripMiles = 9.1,
            expectedTripMinutes = 26.0,
            expectedRating = 4.91
        ),
        ParserSample(
            id = "UK_V1_INLINE_NO_RATING",
            description = "New rider with no rating yet — a legitimately missing field",
            lines = listOf(
                "UberX",
                "£7.20",
                "4 mins (1.1 mi) away",
                "12 mins (3.4 mi) trip"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 7.20,
            expectedPickupMiles = 1.1,
            expectedPickupMinutes = 4.0,
            expectedTripMiles = 3.4,
            expectedTripMinutes = 12.0,
            expectedRating = null
        ),
        ParserSample(
            id = "UK_V2_STACKED_LABELS",
            description = "Label above the numbers, each value on its own line",
            lines = listOf(
                "Uber Comfort",
                "£12.40",
                "4.88",
                "Pickup",
                "1.2 mi",
                "4 min",
                "Dropoff",
                "5.6 mi",
                "17 min"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V2",
            expectedFare = 12.40,
            expectedPickupMiles = 1.2,
            expectedPickupMinutes = 4.0,
            expectedTripMiles = 5.6,
            expectedTripMinutes = 17.0,
            expectedRating = 4.88
        ),
        ParserSample(
            id = "UK_V2_UNLABELLED_COMPACT",
            description = "Compact card with no away/trip wording — order has to be assumed",
            lines = listOf(
                "£9.60",
                "2.0 mi",
                "6 min",
                "4.3 mi",
                "13 min"
            ),
            expectedScreenType = ScreenType.POSSIBLE_OFFER,
            expectedParser = "UBER_UK_STANDARD_V2",
            expectedFare = 9.60,
            expectedPickupMiles = 2.0,
            expectedPickupMinutes = 6.0,
            expectedTripMiles = 4.3,
            expectedTripMinutes = 13.0,
            expectedRating = null,
            expectedNotes = setOf(ParseNote.PICKUP_TRIP_ORDER_ASSUMED)
        ),
        ParserSample(
            id = "UK_V1_KILOMETRES",
            description = "Distances shown in kilometres — converted, never rejected",
            lines = listOf(
                "Uber Green",
                "£15.00",
                "★ 4.95",
                "5 min (3.2 km) away",
                "20 min (12.0 km) trip"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 15.00,
            expectedPickupMiles = 3.2 * 0.621371,
            expectedPickupMinutes = 5.0,
            expectedTripMiles = 12.0 * 0.621371,
            expectedTripMinutes = 20.0,
            expectedRating = 4.95,
            expectedNotes = setOf(ParseNote.KILOMETRES_CONVERTED)
        ),
        ParserSample(
            id = "UK_V1_OCR_DAMAGED",
            description = "Letters recognised in place of digits — repaired, then re-validated",
            lines = listOf(
                "£1O.5O",
                "★ 4.90",
                "6 mins (2.O mi) away",
                "2O mins (7.5 mi) trip"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 10.50,
            expectedPickupMiles = 2.0,
            expectedPickupMinutes = 6.0,
            expectedTripMiles = 7.5,
            expectedTripMinutes = 20.0,
            expectedRating = 4.90,
            expectedNotes = setOf(ParseNote.OCR_DIGIT_CORRECTED)
        ),
        ParserSample(
            id = "UK_V1_WITH_PROMOTION",
            description = "A promotion line carries a second amount that is not the fare",
            lines = listOf(
                "UberX",
                "£11.00",
                "Includes £2.50 promotion",
                "★ 4.72",
                "5 mins (1.8 mi) away",
                "19 mins (6.9 mi) trip"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 11.00,
            expectedPickupMiles = 1.8,
            expectedPickupMinutes = 5.0,
            expectedTripMiles = 6.9,
            expectedTripMinutes = 19.0,
            expectedRating = 4.72
        ),
        ParserSample(
            id = "UK_V1_RATING_WITHOUT_STAR",
            description = "Rating recognised without the star glyph surviving OCR",
            lines = listOf(
                "UberXL",
                "£20.10",
                "Rider 4.65",
                "8 mins (2.9 mi) away",
                "22 mins (8.8 mi) trip"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 20.10,
            expectedPickupMiles = 2.9,
            expectedPickupMinutes = 8.0,
            expectedTripMiles = 8.8,
            expectedTripMinutes = 22.0,
            expectedRating = 4.65,
            expectedNotes = setOf(ParseNote.RATING_WITHOUT_STAR_ANCHOR)
        ),
        ParserSample(
            id = "UK_V1_LONG_PICKUP",
            description = "A long pickup on a modest fare — the case the pickup rules exist for",
            lines = listOf(
                "UberX",
                "£8.40",
                "★ 4.80",
                "14 mins (6.2 mi) away",
                "11 mins (3.1 mi) trip"
            ),
            expectedScreenType = ScreenType.OFFER,
            expectedParser = "UBER_UK_STANDARD_V1",
            expectedFare = 8.40,
            expectedPickupMiles = 6.2,
            expectedPickupMinutes = 14.0,
            expectedTripMiles = 3.1,
            expectedTripMinutes = 11.0,
            expectedRating = 4.80
        ),
        ParserSample(
            id = "NOT_OFFER_WAITING",
            description = "Driver is online with no offer on screen — the assistant must stay quiet",
            lines = listOf("You're online", "Looking for trips"),
            expectedScreenType = ScreenType.NOT_OFFER
        ),
        ParserSample(
            id = "NOT_OFFER_EARNINGS",
            description = "Earnings screen: money on screen, but nothing to evaluate",
            lines = listOf("Earnings", "£124.50", "Today", "12 trips"),
            expectedScreenType = ScreenType.NOT_OFFER
        )
    )

    fun byId(id: String): ParserSample = all.single { it.id == id }
}
