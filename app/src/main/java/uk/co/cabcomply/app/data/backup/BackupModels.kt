package uk.co.cabcomply.app.data.backup

import uk.co.cabcomply.app.data.db.entity.AttachmentEntity
import uk.co.cabcomply.app.data.db.entity.ChecklistEntity
import uk.co.cabcomply.app.data.db.entity.ChecklistItemEntity
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DocumentEntity
import uk.co.cabcomply.app.data.db.entity.DriverProfileEntity
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultEntity
import uk.co.cabcomply.app.data.db.entity.LicensingAuthorityEntity
import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity
import uk.co.cabcomply.app.data.db.entity.VehicleEntity

const val BACKUP_SCHEMA_VERSION = 1
const val BACKUP_MANIFEST_ENTRY_NAME = "manifest.json"
const val BACKUP_PHOTOS_ENTRY_PREFIX = "photos/"

/**
 * Everything a CabComply backup contains. Attachments themselves travel as separate zip entries
 * (see [BACKUP_PHOTOS_ENTRY_PREFIX]) — this manifest only records their metadata and relative
 * paths (product spec section 47).
 */
data class BackupManifest(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersionName: String,
    val exportedAtMillis: Long,
    val driverProfiles: List<DriverProfileEntity>,
    val licensingAuthorities: List<LicensingAuthorityEntity>,
    val vehicles: List<VehicleEntity>,
    val checklists: List<ChecklistEntity>,
    val checklistItems: List<ChecklistItemEntity>,
    val inspections: List<InspectionEntity>,
    val inspectionResults: List<InspectionResultEntity>,
    val defects: List<DefectEntity>,
    val attachments: List<AttachmentEntity>,
    val mileageEntries: List<MileageEntryEntity>,
    val documents: List<DocumentEntity>
)

data class BackupSummary(
    val vehicleCount: Int,
    val inspectionCount: Int,
    val defectCount: Int,
    val mileageCount: Int,
    val documentCount: Int,
    val photoCount: Int
) {
    companion object {
        fun from(manifest: BackupManifest) = BackupSummary(
            vehicleCount = manifest.vehicles.size,
            inspectionCount = manifest.inspections.size,
            defectCount = manifest.defects.size,
            mileageCount = manifest.mileageEntries.size,
            documentCount = manifest.documents.size,
            photoCount = manifest.attachments.size
        )
    }
}

sealed class BackupResult {
    data class Success(val summary: BackupSummary, val fileName: String) : BackupResult()
    data class Failure(val reason: String) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val summary: BackupSummary) : RestoreResult()
    data class Failure(val reason: String) : RestoreResult()
}
