package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.dao.LicensingAuthorityDao
import uk.co.cabcomply.app.data.db.entity.LicensingAuthorityEntity
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthorityRepository @Inject constructor(
    private val dao: LicensingAuthorityDao
) {
    fun observeAuthorities(): Flow<List<LicensingAuthorityEntity>> = dao.observeActive()

    suspend fun getById(id: String): LicensingAuthorityEntity? = dao.getById(id)

    /** A user-entered "Custom / Other" authority name becomes its own row, clearly flagged as custom. */
    suspend fun createCustomAuthority(name: String): LicensingAuthorityEntity {
        val authority = LicensingAuthorityEntity(
            id = "custom_${Ids.newId()}",
            name = name.trim(),
            region = null,
            isCustom = true,
            isActive = true
        )
        dao.upsert(authority)
        return authority
    }
}
