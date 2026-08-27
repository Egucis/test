package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.InspectionEntity

@Dao
interface InspectionDao {
    @Upsert
    suspend fun upsert(inspection: InspectionEntity)

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: String): InspectionEntity?

    @Query("SELECT * FROM inspections WHERE id = :id")
    fun observeById(id: String): Flow<InspectionEntity?>

    /** Used for duplicate-check protection: is there already a completed check today for this vehicle? */
    @Query(
        """SELECT * FROM inspections
           WHERE vehicleId = :vehicleId AND inspectionDate = :dayStart AND completedAt IS NOT NULL
           ORDER BY completedAt DESC LIMIT 1"""
    )
    suspend fun getCompletedForVehicleOnDay(vehicleId: String, dayStart: Long): InspectionEntity?

    @Query(
        """SELECT * FROM inspections
           WHERE vehicleId = :vehicleId AND completedAt IS NOT NULL
           ORDER BY completedAt DESC LIMIT 1"""
    )
    suspend fun getLatestCompletedForVehicle(vehicleId: String): InspectionEntity?

    @Query(
        """SELECT * FROM inspections
           WHERE completedAt IS NOT NULL
             AND (:vehicleId IS NULL OR vehicleId = :vehicleId)
             AND (:fromDate IS NULL OR inspectionDate >= :fromDate)
             AND (:toDate IS NULL OR inspectionDate <= :toDate)
           ORDER BY inspectionDate DESC, completedAt DESC"""
    )
    fun observeHistory(vehicleId: String?, fromDate: Long?, toDate: Long?): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections")
    suspend fun getAll(): List<InspectionEntity>

    @Query("DELETE FROM inspections")
    suspend fun deleteAll()
}
