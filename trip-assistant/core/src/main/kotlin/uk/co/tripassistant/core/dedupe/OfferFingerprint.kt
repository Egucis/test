package uk.co.tripassistant.core.dedupe

import uk.co.tripassistant.core.model.OfferMetrics
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Duplicate protection, spec section 29.
 *
 * OCR looks at the same offer card several times a second, so the same offer arrives over and
 * over. Two things stop that becoming a pile of duplicate history rows:
 *
 *  * [of] quantises the offer's numbers into a stable key — small OCR jitter in the last decimal
 *    place still produces the same fingerprint;
 *  * [isSameOffer] is the tolerant comparison the history repository uses against recent rows,
 *    for the case where jitter lands either side of a quantisation boundary.
 *
 * The time window deliberately is *not* baked into the key. It is applied when looking a
 * candidate up, so that a genuinely identical offer seen again an hour later is still recorded as
 * its own trip rather than silently overwriting the earlier one.
 */
object OfferFingerprint {

    /** How close together two identical-looking reads must be to count as the same offer. */
    const val DEFAULT_MATCH_WINDOW_MILLIS = 3 * 60 * 1000L

    private const val FARE_TOLERANCE = 0.011
    private const val MILES_TOLERANCE = 0.16
    private const val MINUTES_TOLERANCE = 1.01
    private const val RATING_TOLERANCE = 0.011

    /** The human-readable key the diagnostics screen shows. */
    fun canonical(metrics: OfferMetrics): String = buildString {
        append("f").append(round(metrics.fareGbp, 2))
        append("|p").append(round(metrics.pickupMiles, 1))
        append("|t").append(round(metrics.tripMiles, 1))
        append("|pm").append(metrics.pickupMinutes?.roundToInt()?.toString() ?: "-")
        append("|tm").append(metrics.tripMinutes?.roundToInt()?.toString() ?: "-")
        append("|r").append(metrics.riderRating?.let { round(it, 2) } ?: "-")
    }

    /** Short, stable hash of [canonical] — this is what is stored on the history row. */
    fun of(metrics: OfferMetrics): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical(metrics).toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Tolerant comparison for "is this the same offer I already recorded a moment ago?".
     * A field that is present on one side and missing on the other is treated as a match, because
     * OCR routinely loses one line of a card between frames.
     */
    fun isSameOffer(a: OfferMetrics, b: OfferMetrics): Boolean =
        close(a.fareGbp, b.fareGbp, FARE_TOLERANCE) &&
            close(a.pickupMiles, b.pickupMiles, MILES_TOLERANCE) &&
            close(a.tripMiles, b.tripMiles, MILES_TOLERANCE) &&
            closeNullable(a.pickupMinutes, b.pickupMinutes, MINUTES_TOLERANCE) &&
            closeNullable(a.tripMinutes, b.tripMinutes, MINUTES_TOLERANCE) &&
            closeNullable(a.riderRating, b.riderRating, RATING_TOLERANCE)

    private fun close(a: Double, b: Double, tolerance: Double) = abs(a - b) <= tolerance

    private fun closeNullable(a: Double?, b: Double?, tolerance: Double): Boolean {
        if (a == null || b == null) return true
        return close(a, b, tolerance)
    }

    private fun round(value: Double, decimals: Int): String =
        String.format(Locale.UK, "%.${decimals}f", value)
}
