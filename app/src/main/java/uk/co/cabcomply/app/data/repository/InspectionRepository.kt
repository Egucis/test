package uk.co.cabcomply.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import uk.co.cabcomply.app.data.db.CabComplyDatabase
import uk.co.cabcomply.app.data.db.dao.AttachmentDao
import uk.co.cabcomply.app.data.db.dao.DefectDao
import uk.co.cabcomply.app.data.db.dao.InspectionDao
import uk.co.cabcomply.app.data.db.dao.InspectionResultDao
import uk.co.cabcomply.app.data.db.dao.VehicleDao
import uk.co.cabcomply.app.data.db.entity.AttachmentEntity
import uk.co.cabcomply.app.data.db.entity.AttachmentOwnerType
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultStatus
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

/** One checklist item's outcome plus, if defective, its description and photo files, ready to persist. */
data class ChecklistItemOutcome(
    val checklistItemId: String,
    val itemName: String,
    val category: String,
    val displayOrder: Int,
    val status: InspectionResultStatus,
    val defectDescription: String? = null,
    val defectPhotoRelativePaths: List<String> = emptyList(),
    val defectThumbnailRelativePaths: List<String> = emptyList()
)

@Singleton
class InspectionRepository @Inject constructor(
    private val database: CabComplyDatabase,
    private val inspectionDao: InspectionDao,
    private val resultDao: InspectionResultDao,
    private val defectDao: DefectDao,
    private val attachmentDao: AttachmentDao,
    private val vehicleDao: VehicleDao,
    private val clock: AppClock
) {
    suspend fun getCompletedToday(vehicleId: String): InspectionEntity? =
        inspectionDao.getCompletedForVehicleOnDay(vehicleId, clock.startOfDay())

    suspend fun getLatestCompleted(vehicleId: String): InspectionEntity? =
        inspectionDao.getLatestCompletedForVehicle(vehicleId)

    /** The best available "last known" odometer reading to pre-fill a new check for this vehicle only. */
    suspend fun getLastKnownOdometer(vehicleId: String): Int? = vehicleDao.getById(vehicleId)?.currentOdometer

    fun observeHistory(vehicleId: String?, fromDate: Long?, toDate: Long?): Flow<List<InspectionEntity>> =
        inspectionDao.observeHistory(vehicleId, fromDate, toDate)

    suspend fun getHistorySnapshot(vehicleId: String?, fromDate: Long?, toDate: Long?): List<InspectionEntity> =
        observeHistory(vehicleId, fromDate, toDate).first()

    suspend fun getById(id: String): InspectionEntity? = inspectionDao.getById(id)
    fun observeById(id: String): Flow<InspectionEntity?> = inspectionDao.observeById(id)

    suspend fun getResults(inspectionId: String): List<InspectionResultEntity> = resultDao.getForInspection(inspectionId)
    fun observeResults(inspectionId: String): Flow<List<InspectionResultEntity>> = resultDao.observeForInspection(inspectionId)
    suspend fun getDefects(inspectionId: String): List<DefectEntity> = defectDao.getForInspection(inspectionId)

    /**
     * Persists a completed inspection with its checklist results, any new defects, and defect
     * photos in a single transaction, then advances the vehicle's known odometer. If any part
     * fails, nothing is written (product spec section 51).
     */
    suspend fun completeInspection(
        inspection: InspectionEntity,
        outcomes: List<ChecklistItemOutcome>
    ) {
        require(inspection.driverConfirmed) { "Inspection cannot be saved without driver confirmation." }
        database.withTransaction {
            inspectionDao.upsert(inspection)

            val results = outcomes.map {
                InspectionResultEntity(
                    id = Ids.newId(),
                    inspectionId = inspection.id,
                    checklistItemId = it.checklistItemId,
                    itemNameSnapshot = it.itemName,
                    categorySnapshot = it.category,
                    displayOrderSnapshot = it.displayOrder,
                    status = it.status
                )
            }
            resultDao.insertAll(results)

            val defectiveOutcomes = outcomes.filter { it.status == InspectionResultStatus.DEFECT }
            val defects = defectiveOutcomes.map { outcome ->
                val result = results.first { it.checklistItemId == outcome.checklistItemId }
                DefectEntity(
                    id = Ids.newId(),
                    inspectionId = inspection.id,
                    inspectionResultId = result.id,
                    vehicleId = inspection.vehicleId,
                    checklistItemNameSnapshot = outcome.itemName,
                    description = outcome.defectDescription.orEmpty(),
                    status = DefectStatus.OPEN,
                    reportedAt = inspection.completedAt ?: clock.nowMillis(),
                    resolvedAt = null,
                    resolutionNote = null
                ).also { defect ->
                    val attachments = outcome.defectPhotoRelativePaths.mapIndexed { photoIndex, path ->
                        AttachmentEntity(
                            id = Ids.newId(),
                            ownerType = AttachmentOwnerType.DEFECT,
                            ownerId = defect.id,
                            filePath = path,
                            thumbnailPath = outcome.defectThumbnailRelativePaths.getOrNull(photoIndex),
                            createdAt = clock.nowMillis()
                        )
                    }
                    if (attachments.isNotEmpty()) attachmentDao.insertAll(attachments)
                }
            }
            if (defects.isNotEmpty()) defectDao.insertAll(defects)

            vehicleDao.getById(inspection.vehicleId)?.let { vehicle ->
                vehicleDao.upsert(vehicle.copy(currentOdometer = inspection.odometer, updatedAt = clock.nowMillis()))
            }
        }
    }

    /**
     * Sensitive path: amends an already-completed inspection's notes/confirmation record.
     * Preserves the original id, vehicle association and checklist version; only stamps
     * [InspectionEntity.modifiedAt] and [InspectionEntity.modificationReason] — never rewrites
     * silently (product spec section 37).
     */
    suspend fun amendCompletedInspection(inspectionId: String, notes: String?, reason: String) {
        database.withTransaction {
            val existing = inspectionDao.getById(inspectionId) ?: error("Inspection not found.")
            check(existing.completedAt != null) { "Only completed inspections can be amended." }
            inspectionDao.upsert(
                existing.copy(
                    notes = notes,
                    modifiedAt = clock.nowMillis(),
                    modificationReason = reason
                )
            )
        }
    }
}
