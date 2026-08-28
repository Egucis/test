package uk.co.tripassistant.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.tripassistant.app.data.db.dao.ProfileDao
import uk.co.tripassistant.app.data.db.entity.RuleProfileEntity
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.rules.DefaultProfiles
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule profiles (spec section 18).
 *
 * The starter set is seeded once, on first launch, and then belongs entirely to the driver: it is
 * never re-seeded or "corrected" on a later launch, because that would silently undo their edits.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao
) {

    fun observeProfiles(): Flow<List<RuleProfile>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeActiveProfile(): Flow<RuleProfile?> =
        dao.observeActive().map { it?.toDomain() }

    /**
     * The profile the live pipeline scores against. Falls back to the first stored profile, and
     * finally to the built-in Normal profile, so evaluation can never fail for lack of a profile.
     */
    suspend fun activeProfile(): RuleProfile {
        dao.activeProfile()?.let { return it.toDomain() }
        val first = dao.observeAll().first().firstOrNull()
        if (first != null) {
            dao.setActive(first.id, System.currentTimeMillis())
            return first.toDomain().copy(isActive = true)
        }
        return DefaultProfiles.normal(id = 0L, now = System.currentTimeMillis())
    }

    suspend fun byId(id: Long): RuleProfile? = dao.byId(id)?.toDomain()

    suspend fun seedIfEmpty(now: Long = System.currentTimeMillis()) {
        if (dao.count() > 0) return
        DefaultProfiles.starterSet(now).forEach { profile ->
            dao.insert(RuleProfileEntity.fromDomain(profile).copy(id = 0L))
        }
    }

    suspend fun create(name: String, now: Long = System.currentTimeMillis()): Long =
        dao.insert(RuleProfileEntity.fromDomain(DefaultProfiles.custom(name, now)).copy(id = 0L))

    suspend fun save(profile: RuleProfile, now: Long = System.currentTimeMillis()) {
        dao.update(RuleProfileEntity.fromDomain(profile.copy(updatedAt = now)))
    }

    suspend fun setActive(id: Long, now: Long = System.currentTimeMillis()) = dao.setActive(id, now)

    /**
     * Deleting the active profile hands the active flag to whatever is left, so the app is never
     * left with no active profile. The last remaining profile cannot be deleted.
     */
    suspend fun delete(id: Long, now: Long = System.currentTimeMillis()): Boolean {
        val all = dao.observeAll().first()
        if (all.size <= 1) return false
        val wasActive = all.firstOrNull { it.id == id }?.isActive == true
        dao.delete(id)
        if (wasActive) {
            all.firstOrNull { it.id != id }?.let { dao.setActive(it.id, now) }
        }
        return true
    }
}
