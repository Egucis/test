package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DefectStatus

@Dao
interface DefectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(defects: List<DefectEntity>)

    @Upsert
    suspend fun upsert(defect: DefectEntity)

    @Query("SELECT * FROM defects WHERE id = :id")
    suspend fun getById(id: String): DefectEntity?

    @Query("SELECT * FROM defects WHERE id = :id")
    fun observeById(id: String): Flow<DefectEntity?>

    @Query(
        """SELECT * FROM defects
           WHERE (:status IS NULL OR status = :status)
             AND (:vehicleId IS NULL OR vehicleId = :vehicleId)
           ORDER BY reportedAt DESC"""
    )
    fun observeFiltered(status: DefectStatus?, vehicleId: String?): Flow<List<DefectEntity>>

    @Query("SELECT * FROM defects WHERE vehicleId = :vehicleId AND status = 'OPEN' ORDER BY reportedAt DESC")
    fun observeOpenForVehicle(vehicleId: String): Flow<List<DefectEntity>>

    @Query("SELECT COUNT(*) FROM defects WHERE vehicleId = :vehicleId AND status = 'OPEN'")
    suspend fun countOpenForVehicle(vehicleId: String): Int

    @Query("SELECT COUNT(*) FROM defects WHERE status = 'OPEN'")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM defects WHERE inspectionId = :inspectionId")
    suspend fun getForInspection(inspectionId: String): List<DefectEntity>

    @Query("SELECT * FROM defects")
    suspend fun getAll(): List<DefectEntity>

    @Query("DELETE FROM defects")
    suspend fun deleteAll()
}
