package uk.co.tripassistant.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.tripassistant.app.data.db.dao.OfferDao
import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity
import uk.co.tripassistant.app.data.prefs.SettingsRepository
import uk.co.tripassistant.core.dedupe.OfferFingerprint
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.OfferOutcome
import uk.co.tripassistant.core.model.Recommendation
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to an attempt to record an offer. */
sealed interface RecordResult {
    data class Inserted(val id: Long) : RecordResult
    data class UpdatedExisting(val id: Long) : RecordResult
    data object Skipped : RecordResult
}

/**
 * Local offer history (spec sections 29 to 34).
 *
 * Two rules shape this class:
 *  * OCR sees the same card many times a second, so recording is idempotent within a short window
 *    (spec section 29);
 *  * history is the driver's data. It survives a lapsed subscription, and only an explicit
 *    "delete all" or the retention setting removes it (spec sections 3 and 34).
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val dao: OfferDao,
    private val settings: SettingsRepository
) {

    fun observeBetween(from: Long, to: Long): Flow<List<EvaluatedOfferEntity>> = dao.observeBetween(from, to)

    fun observeById(id: Long): Flow<EvaluatedOfferEntity?> = dao.observeById(id)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeStatsBetween(from: Long, to: Long): Flow<OfferStats> =
        observeBetween(from, to).map { OfferStats.from(it) }

    /**
     * Records an evaluated offer, or folds it into the one already recorded moments ago
     * (spec section 29).
     *
     * Returns [RecordResult.Skipped] when history is switched off, or when the evaluation has no
     * economics to store — an UNKNOWN that never got as far as a number is a diagnostics event,
     * not a trip.
     */
    suspend fun record(evaluation: OfferEvaluation, now: Long): RecordResult {
        if (!settings.current().historyEnabled) return RecordResult.Skipped
        val metrics = evaluation.metrics ?: return RecordResult.Skipped
        val row = evaluation.toEntity(now) ?: return RecordResult.Skipped

        val since = now - OfferFingerprint.DEFAULT_MATCH_WINDOW_MILLIS

        // Fast path: the quantised key already matches something recent.
        val exact = dao.recentByFingerprint(row.fingerprint, since)
        // Slow path: OCR jitter landed either side of a quantisation boundary, so compare the
        // recent rows field by field with tolerance.
        val existing = exact ?: dao.recent(since).firstOrNull {
            OfferFingerprint.isSameOffer(it.toMetrics(), metrics)
        }

        return if (existing == null) {
            RecordResult.Inserted(dao.insert(row))
        } else {
            // Keep the original timestamp and any outcome already detected: this is the same
            // offer, read again, not a new one.
            dao.update(row.copy(id = existing.id, timestamp = existing.timestamp, outcome = existing.outcome))
            RecordResult.UpdatedExisting(existing.id)
        }
    }

    /** Spec section 30: only ever called with real evidence, never on an offer simply vanishing. */
    suspend fun markOutcome(id: Long, outcome: OfferOutcome) = dao.setOutcome(id, outcome.name)

    suspend fun mostRecent(): EvaluatedOfferEntity? = dao.mostRecent()

    /** Applies the retention setting (spec section 34). Cheap enough to run at every app start. */
    suspend fun applyRetention(now: Long = System.currentTimeMillis()): Int {
        val retention = settings.current().retention
        val days = retention.days ?: return 0
        return dao.deleteOlderThan(now - days * MILLIS_PER_DAY)
    }

    /** Spec section 34: deleting history must not touch settings or subscription state. */
    suspend fun deleteAll() = dao.deleteAll()

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

/**
 * Daily analytics (spec section 33).
 *
 * [acceptedDetected] and [outcomesKnown] are reported next to [evaluated] rather than turned into
 * an acceptance rate: the outcome of an offer often genuinely cannot be determined, and a
 * confident-looking percentage built on that would be a lie (spec sections 30 and 33).
 */
data class OfferStats(
    val evaluated: Int = 0,
    val good: Int = 0,
    val borderline: Int = 0,
    val poor: Int = 0,
    val unknown: Int = 0,
    val averageFare: Double? = null,
    val averagePoundsPerMile: Double? = null,
    val averagePoundsPerHour: Double? = null,
    val averagePickupMiles: Double? = null,
    val acceptedDetected: Int = 0,
    val outcomesKnown: Int = 0
) {
    companion object {
        fun from(offers: List<EvaluatedOfferEntity>): OfferStats {
            if (offers.isEmpty()) return OfferStats()
            val withHours = offers.mapNotNull { it.poundsPerHour }
            return OfferStats(
                evaluated = offers.size,
                good = offers.count { it.recommendation == Recommendation.GOOD },
                borderline = offers.count { it.recommendation == Recommendation.BORDERLINE },
                poor = offers.count { it.recommendation == Recommendation.POOR },
                unknown = offers.count { it.recommendation == Recommendation.UNKNOWN },
                averageFare = offers.map { it.fare }.average(),
                averagePoundsPerMile = offers.map { it.poundsPerMile }.average(),
                averagePoundsPerHour = withHours.takeIf { it.isNotEmpty() }?.average(),
                averagePickupMiles = offers.map { it.pickupMiles }.average(),
                acceptedDetected = offers.count { it.outcome == OfferOutcome.ACCEPTED },
                outcomesKnown = offers.count { it.outcome != OfferOutcome.UNKNOWN_OUTCOME && it.outcome != OfferOutcome.SEEN }
            )
        }
    }
}
