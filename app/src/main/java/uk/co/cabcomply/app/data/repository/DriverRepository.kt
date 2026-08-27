package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.dao.DriverProfileDao
import uk.co.cabcomply.app.data.db.entity.DriverProfileEntity
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverRepository @Inject constructor(
    private val dao: DriverProfileDao,
    private val clock: AppClock
) {
    fun observeProfile(): Flow<DriverProfileEntity?> = dao.observeProfile()

    suspend fun getProfile(): DriverProfileEntity? = dao.getProfile()

    suspend fun saveProfile(name: String, licensingAuthorityId: String?, badgeNumber: String?): DriverProfileEntity {
        val existing = dao.getProfile()
        val now = clock.nowMillis()
        val profile = existing?.copy(
            name = name,
            licensingAuthorityId = licensingAuthorityId,
            badgeNumber = badgeNumber,
            updatedAt = now
        ) ?: DriverProfileEntity(
            id = Ids.newId(),
            name = name,
            licensingAuthorityId = licensingAuthorityId,
            badgeNumber = badgeNumber,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(profile)
        return profile
    }
}
