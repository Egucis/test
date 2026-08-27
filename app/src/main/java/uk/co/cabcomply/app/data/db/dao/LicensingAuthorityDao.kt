package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.LicensingAuthorityEntity

@Dao
interface LicensingAuthorityDao {
    @Query("SELECT * FROM licensing_authorities WHERE isActive = 1 ORDER BY isCustom ASC, name ASC")
    fun observeActive(): Flow<List<LicensingAuthorityEntity>>

    @Query("SELECT * FROM licensing_authorities WHERE id = :id")
    suspend fun getById(id: String): LicensingAuthorityEntity?

    @Query("SELECT COUNT(*) FROM licensing_authorities")
    suspend fun count(): Int

    @Query("SELECT * FROM licensing_authorities")
    suspend fun getAll(): List<LicensingAuthorityEntity>

    @Query("DELETE FROM licensing_authorities")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(authorities: List<LicensingAuthorityEntity>)

    @Upsert
    suspend fun upsert(authority: LicensingAuthorityEntity)
}
