package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.DriverProfileEntity

@Dao
interface DriverProfileDao {
    @Query("SELECT * FROM driver_profiles LIMIT 1")
    fun observeProfile(): Flow<DriverProfileEntity?>

    @Query("SELECT * FROM driver_profiles LIMIT 1")
    suspend fun getProfile(): DriverProfileEntity?

    @Query("SELECT * FROM driver_profiles WHERE id = :id")
    suspend fun getById(id: String): DriverProfileEntity?

    @Query("SELECT * FROM driver_profiles")
    suspend fun getAll(): List<DriverProfileEntity>

    @Query("DELETE FROM driver_profiles")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(profile: DriverProfileEntity)
}
