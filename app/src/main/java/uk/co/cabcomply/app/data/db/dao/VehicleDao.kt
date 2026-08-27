package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.VehicleEntity

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE isArchived = 0 ORDER BY registration ASC")
    fun observeActiveVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE isArchived = 1 ORDER BY registration ASC")
    fun observeArchivedVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY registration ASC")
    fun observeAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 AND isArchived = 0 LIMIT 1")
    fun observeActiveVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 AND isArchived = 0 LIMIT 1")
    suspend fun getActiveVehicle(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: String): Flow<VehicleEntity?>

    @Query("SELECT COUNT(*) FROM vehicles WHERE isArchived = 0")
    suspend fun countActiveVehicles(): Int

    @Query("SELECT * FROM vehicles")
    suspend fun getAll(): List<VehicleEntity>

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActiveFlag()

    @Query("UPDATE vehicles SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: String)

    @Transaction
    suspend fun setActiveVehicle(id: String) {
        clearActiveFlag()
        markActive(id)
    }

    @Query("UPDATE vehicles SET isArchived = 1, isActive = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun archive(id: String, timestamp: Long)
}
