package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.dao.AttachmentDao
import uk.co.cabcomply.app.data.db.dao.DefectDao
import uk.co.cabcomply.app.data.db.entity.AttachmentEntity
import uk.co.cabcomply.app.data.db.entity.AttachmentOwnerType
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefectRepository @Inject constructor(
    private val defectDao: DefectDao,
    private val attachmentDao: AttachmentDao,
    private val clock: AppClock
) {
    fun observeFiltered(status: DefectStatus?, vehicleId: String?): Flow<List<DefectEntity>> =
        defectDao.observeFiltered(status, vehicleId)

    fun observeOpenForVehicle(vehicleId: String): Flow<List<DefectEntity>> = defectDao.observeOpenForVehicle(vehicleId)
    fun observeForInspection(inspectionId: String): Flow<List<DefectEntity>> = defectDao.observeForInspection(inspectionId)
    suspend fun countOpenForVehicle(vehicleId: String): Int = defectDao.countOpenForVehicle(vehicleId)
    fun observeOpenCount(): Flow<Int> = defectDao.observeOpenCount()
    suspend fun getById(id: String): DefectEntity? = defectDao.getById(id)
    fun observeById(id: String): Flow<DefectEntity?> = defectDao.observeById(id)

    fun observeAttachments(ownerType: AttachmentOwnerType, ownerId: String): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForOwner(ownerType, ownerId)

    /** Resolving never deletes the original defect or its evidence photos — resolution fields are added alongside them. */
    suspend fun resolveDefect(id: String, resolutionNote: String?, resolutionPhotoRelativePaths: List<String>) {
        val defect = defectDao.getById(id) ?: return
        val now = clock.nowMillis()
        defectDao.upsert(
            defect.copy(status = DefectStatus.RESOLVED, resolvedAt = now, resolutionNote = resolutionNote)
        )
        if (resolutionPhotoRelativePaths.isNotEmpty()) {
            attachmentDao.insertAll(
                resolutionPhotoRelativePaths.map { path ->
                    AttachmentEntity(
                        id = Ids.newId(),
                        ownerType = AttachmentOwnerType.DEFECT_RESOLUTION,
                        ownerId = id,
                        filePath = path,
                        thumbnailPath = null,
                        createdAt = now
                    )
                }
            )
        }
    }
}
