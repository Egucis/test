package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.InspectionResultEntity

@Dao
interface InspectionResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<InspectionResultEntity>)

    @Query("SELECT * FROM inspection_results WHERE inspectionId = :inspectionId ORDER BY displayOrderSnapshot ASC")
    fun observeForInspection(inspectionId: String): Flow<List<InspectionResultEntity>>

    @Query("SELECT * FROM inspection_results WHERE inspectionId = :inspectionId ORDER BY displayOrderSnapshot ASC")
    suspend fun getForInspection(inspectionId: String): List<InspectionResultEntity>

    @Query("DELETE FROM inspection_results WHERE inspectionId = :inspectionId")
    suspend fun deleteForInspection(inspectionId: String)

    @Query("SELECT * FROM inspection_results")
    suspend fun getAll(): List<InspectionResultEntity>

    @Query("DELETE FROM inspection_results")
    suspend fun deleteAll()
}
