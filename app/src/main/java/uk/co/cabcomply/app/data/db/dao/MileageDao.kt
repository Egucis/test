package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity

@Dao
interface MileageDao {
    @Upsert
    suspend fun upsert(entry: MileageEntryEntity)

    @Query("SELECT * FROM mileage_entries WHERE id = :id")
    suspend fun getById(id: String): MileageEntryEntity?

    @Query("SELECT * FROM mileage_entries WHERE id = :id")
    fun observeById(id: String): Flow<MileageEntryEntity?>

    @Query(
        """SELECT * FROM mileage_entries
           WHERE (:vehicleId IS NULL OR vehicleId = :vehicleId)
             AND (:fromDate IS NULL OR entryDate >= :fromDate)
             AND (:toDate IS NULL OR entryDate <= :toDate)
           ORDER BY entryDate DESC, startedAt DESC"""
    )
    fun observeFiltered(vehicleId: String?, fromDate: Long?, toDate: Long?): Flow<List<MileageEntryEntity>>

    @Query(
        """SELECT * FROM mileage_entries WHERE vehicleId = :vehicleId AND endMileage IS NOT NULL
           ORDER BY entryDate DESC, startedAt DESC LIMIT 1"""
    )
    suspend fun getLatestCompletedForVehicle(vehicleId: String): MileageEntryEntity?

    @Query("SELECT * FROM mileage_entries WHERE vehicleId = :vehicleId ORDER BY entryDate DESC, startedAt DESC LIMIT 1")
    suspend fun getLatestForVehicle(vehicleId: String): MileageEntryEntity?

    @Query("SELECT * FROM mileage_entries WHERE isFlagged = 1 ORDER BY entryDate DESC")
    fun observeFlagged(): Flow<List<MileageEntryEntity>>

    @Query("SELECT * FROM mileage_entries")
    suspend fun getAll(): List<MileageEntryEntity>

    @Query("DELETE FROM mileage_entries")
    suspend fun deleteAll()
}
