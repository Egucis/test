package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.ChecklistEntity
import uk.co.cabcomply.app.data.db.entity.ChecklistItemEntity

@Dao
interface ChecklistDao {
    @Query("SELECT COUNT(*) FROM checklists")
    suspend fun count(): Int

    @Insert(onConflictStrategy = OnConflictStrategy.IGNORE)
    suspend fun insertChecklists(checklists: List<ChecklistEntity>)

    @Insert(onConflictStrategy = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Upsert
    suspend fun upsertChecklist(checklist: ChecklistEntity)

    @Upsert
    suspend fun upsertItems(items: List<ChecklistItemEntity>)

    /** The current active checklist version for a given authority, or the generic default when null. */
    @Query(
        """SELECT * FROM checklists
           WHERE isActive = 1 AND (licensingAuthorityId = :authorityId OR (:authorityId IS NULL AND licensingAuthorityId IS NULL))
           ORDER BY version DESC LIMIT 1"""
    )
    suspend fun getActiveChecklistForAuthority(authorityId: String?): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE licensingAuthorityId IS NULL AND isCustom = 0 AND isActive = 1 ORDER BY version DESC LIMIT 1")
    suspend fun getDefaultActiveChecklist(): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getById(id: String): ChecklistEntity?

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY displayOrder ASC")
    fun observeItems(checklistId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY displayOrder ASC")
    suspend fun getItems(checklistId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklists ORDER BY name ASC, version DESC")
    fun observeAllChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists")
    suspend fun getAllChecklistsSnapshot(): List<ChecklistEntity>

    @Query("SELECT * FROM checklist_items")
    suspend fun getAllItemsSnapshot(): List<ChecklistItemEntity>

    @Query("DELETE FROM checklists")
    suspend fun deleteAllChecklists()

    @Query("DELETE FROM checklist_items")
    suspend fun deleteAllItems()
}
