package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.dao.ChecklistDao
import uk.co.cabcomply.app.data.db.entity.ChecklistEntity
import uk.co.cabcomply.app.data.db.entity.ChecklistItemEntity
import uk.co.cabcomply.app.data.seed.ChecklistSeedData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChecklistRepository @Inject constructor(
    private val dao: ChecklistDao
) {
    /** The checklist version currently in force for [authorityId], falling back to the generic default. */
    suspend fun getActiveChecklist(authorityId: String?): ChecklistEntity {
        val forAuthority = authorityId?.let { dao.getActiveChecklistForAuthority(it) }
        return forAuthority
            ?: dao.getDefaultActiveChecklist()
            ?: dao.getById(ChecklistSeedData.DEFAULT_CHECKLIST_ID)
            ?: error("No default checklist available — database seeding did not run.")
    }

    suspend fun getById(id: String): ChecklistEntity? = dao.getById(id)

    fun observeItems(checklistId: String): Flow<List<ChecklistItemEntity>> = dao.observeItems(checklistId)

    suspend fun getItems(checklistId: String): List<ChecklistItemEntity> = dao.getItems(checklistId)

    fun observeAllChecklists(): Flow<List<ChecklistEntity>> = dao.observeAllChecklists()
}
