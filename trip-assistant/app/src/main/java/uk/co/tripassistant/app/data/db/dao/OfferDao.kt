package uk.co.tripassistant.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity

@Dao
interface OfferDao {

    @Query(
        """
        SELECT * FROM evaluated_offers
        WHERE timestamp BETWEEN :from AND :to
        ORDER BY timestamp DESC
        """
    )
    fun observeBetween(from: Long, to: Long): Flow<List<EvaluatedOfferEntity>>

    @Query("SELECT * FROM evaluated_offers WHERE id = :id")
    fun observeById(id: Long): Flow<EvaluatedOfferEntity?>

    /**
     * Candidates for duplicate detection (spec section 29): same fingerprint, seen a moment ago.
     * The time window is applied here rather than baked into the fingerprint so that the identical
     * offer seen again hours later is still recorded as its own trip.
     */
    @Query(
        """
        SELECT * FROM evaluated_offers
        WHERE fingerprint = :fingerprint AND timestamp >= :since
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun recentByFingerprint(fingerprint: String, since: Long): EvaluatedOfferEntity?

    @Query(
        """
        SELECT * FROM evaluated_offers
        WHERE timestamp >= :since
        ORDER BY timestamp DESC
        LIMIT 20
        """
    )
    suspend fun recent(since: Long): List<EvaluatedOfferEntity>

    @Query("SELECT * FROM evaluated_offers ORDER BY timestamp DESC LIMIT 1")
    suspend fun mostRecent(): EvaluatedOfferEntity?

    @Insert
    suspend fun insert(offer: EvaluatedOfferEntity): Long

    @Update
    suspend fun update(offer: EvaluatedOfferEntity)

    @Query("UPDATE evaluated_offers SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: String)

    @Query("DELETE FROM evaluated_offers WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM evaluated_offers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM evaluated_offers")
    fun observeCount(): Flow<Int>
}
