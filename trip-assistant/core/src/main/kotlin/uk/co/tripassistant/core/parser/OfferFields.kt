package uk.co.tripassistant.core.parser

import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.text.OcrText
import uk.co.tripassistant.core.text.TextLine
import uk.co.tripassistant.core.text.TextNormalizer

/** A distance found in a line, always normalised to miles. */
internal data class DistanceMatch(
    val miles: Double,
    val corrected: Boolean,
    val convertedFromKm: Boolean,
    val start: Int,
    val end: Int
)

/** A duration found in a line, always normalised to minutes. */
internal data class DurationMatch(
    val minutes: Double,
    val corrected: Boolean,
    val start: Int,
    val end: Int
)

/**
 * Field-level extraction shared by every parser version.
 *
 * Keeping these here rather than in a single parser is what makes an Uber redesign cheap: a new
 * layout usually only changes how fields are *associated* with pickup and trip, not how a fare or
 * a distance looks (spec sections 13 and 62).
 */
internal object OfferFields {

    private const val KM_TO_MILES = 0.621371
    private const val MIN_PLAUSIBLE_FARE = 0.50
    private const val MAX_PLAUSIBLE_FARE = 1_000.0

    /** All distances in a line, left to right. */
    fun distancesIn(line: String): List<DistanceMatch> =
        Patterns.DISTANCE.findAll(line).mapNotNull { match ->
            val parsed = TextNormalizer.parseNumber(match.groupValues[1]) ?: return@mapNotNull null
            val unit = match.groupValues[2].lowercase()
            val isKm = unit.startsWith("k")
            DistanceMatch(
                miles = if (isKm) parsed.value * KM_TO_MILES else parsed.value,
                corrected = parsed.corrected,
                convertedFromKm = isKm,
                start = match.range.first,
                end = match.range.last
            )
        }.toList()

    /** All durations in a line, left to right. */
    fun durationsIn(line: String): List<DurationMatch> =
        Patterns.DURATION.findAll(line).mapNotNull { match ->
            val parsed = TextNormalizer.parseNumber(match.groupValues[1]) ?: return@mapNotNull null
            val unit = match.groupValues[2].lowercase()
            val isHours = unit.startsWith("h")
            DurationMatch(
                minutes = if (isHours) parsed.value * 60.0 else parsed.value,
                corrected = parsed.corrected,
                start = match.range.first,
                end = match.range.last
            )
        }.toList()

    /**
     * The offered fare (spec section 14).
     *
     * Uber puts the fare in the largest text on the card, so prominence decides. When a second,
     * differently valued amount is nearly as prominent the choice is genuinely ambiguous, and that
     * is recorded as a note — which downgrades the offer to PARTIAL confidence rather than risking
     * an evaluation built on a promotion figure.
     */
    fun findFare(text: OcrText): FieldResult<Double> {
        data class Candidate(val value: Double, val prominence: Float, val corrected: Boolean)

        val candidates = mutableListOf<Candidate>()
        for (line in text.lines) {
            if (Patterns.containsAny(line.text, Patterns.FARE_EXCLUSION_KEYWORDS)) continue
            for (match in Patterns.CURRENCY.findAll(line.text)) {
                val parsed = TextNormalizer.parseNumber(match.groupValues[1]) ?: continue
                if (parsed.value < MIN_PLAUSIBLE_FARE || parsed.value > MAX_PLAUSIBLE_FARE) continue
                candidates += Candidate(parsed.value, line.prominence, parsed.corrected)
            }
        }
        if (candidates.isEmpty()) return FieldResult(null, emptySet())

        val best = candidates.maxWith(compareBy({ it.prominence }, { it.value }))
        val notes = mutableSetOf<ParseNote>()
        if (best.corrected) notes += ParseNote.OCR_DIGIT_CORRECTED

        // Ambiguous only if another amount of a *different* value is comparably prominent.
        val rival = candidates.any { it.value != best.value && it.prominence > best.prominence * 0.8f }
        if (rival) notes += ParseNote.FARE_CHOSEN_BY_PROMINENCE

        return FieldResult(best.value, notes)
    }

    /**
     * The rider rating (spec section 14).
     *
     * Contextual matching matters here more than anywhere else: spec section 16 specifically calls
     * out that a rating must never be mistaken for mileage. A number only becomes a rating if it
     * sits on a line of its own, or next to a star, or on a line carrying no units at all.
     */
    fun findRating(text: OcrText): FieldResult<Double> {
        var weak: Pair<Double, TextLine>? = null

        for (line in text.readingOrder()) {
            val body = line.text.trim()

            Patterns.RATING_ONLY_LINE.find(body)?.let { match ->
                val value = ratingValue(match.groupValues[1]) ?: return@let
                return FieldResult(value, emptySet())
            }

            if (Patterns.STAR.containsMatchIn(body)) {
                Patterns.RATING_INLINE.find(body)?.let { match ->
                    val value = ratingValue(match.groupValues[1]) ?: return@let
                    return FieldResult(value, emptySet())
                }
            }

            if (weak == null && !hasUnitToken(body)) {
                Patterns.RATING_INLINE.find(body)?.let { match ->
                    val value = ratingValue(match.groupValues[1]) ?: return@let
                    weak = value to line
                }
            }
        }

        val fallback = weak ?: return FieldResult(null, emptySet())
        return FieldResult(fallback.first, setOf(ParseNote.RATING_WITHOUT_STAR_ANCHOR))
    }

    private fun ratingValue(token: String): Double? =
        token.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..5.0 }

    private fun hasUnitToken(line: String): Boolean =
        line.contains('£') ||
            Patterns.DISTANCE.containsMatchIn(line) ||
            Patterns.DURATION.containsMatchIn(line) ||
            line.contains('%')

    /** A parsed field plus anything the parser had to assume to get it. */
    data class FieldResult<T>(val value: T?, val notes: Set<ParseNote>)
}
