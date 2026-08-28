package uk.co.tripassistant.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.tripassistant.app.data.db.entity.RuleProfileEntity

@Dao
interface ProfileDao {

    @Query("SELECT * FROM rule_profiles ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<RuleProfileEntity>>

    @Query("SELECT * FROM rule_profiles WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<RuleProfileEntity?>

    @Query("SELECT * FROM rule_profiles WHERE is_active = 1 LIMIT 1")
    suspend fun activeProfile(): RuleProfileEntity?

    @Query("SELECT * FROM rule_profiles WHERE id = :id")
    suspend fun byId(id: Long): RuleProfileEntity?

    @Query("SELECT COUNT(*) FROM rule_profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: RuleProfileEntity): Long

    @Update
    suspend fun update(profile: RuleProfileEntity)

    @Query("DELETE FROM rule_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE rule_profiles SET is_active = 0")
    suspend fun clearActive()

    @Query("UPDATE rule_profiles SET is_active = 1, updated_at = :now WHERE id = :id")
    suspend fun markActive(id: Long, now: Long)

    /** Exactly one profile is active at a time (spec section 18). */
    @Transaction
    suspend fun setActive(id: Long, now: Long) {
        clearActive()
        markActive(id, now)
    }
}
