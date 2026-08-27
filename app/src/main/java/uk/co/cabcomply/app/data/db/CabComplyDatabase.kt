package uk.co.cabcomply.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uk.co.cabcomply.app.data.db.dao.AttachmentDao
import uk.co.cabcomply.app.data.db.dao.ChecklistDao
import uk.co.cabcomply.app.data.db.dao.DefectDao
import uk.co.cabcomply.app.data.db.dao.DocumentDao
import uk.co.cabcomply.app.data.db.dao.DriverProfileDao
import uk.co.cabcomply.app.data.db.dao.InspectionDao
import uk.co.cabcomply.app.data.db.dao.InspectionResultDao
import uk.co.cabcomply.app.data.db.dao.LicensingAuthorityDao
import uk.co.cabcomply.app.data.db.dao.MileageDao
import uk.co.cabcomply.app.data.db.dao.VehicleDao
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

/**
 * Room schema, version 1. Future authority/checklist requirement changes must ship as additive
 * migrations (Migration objects registered on the builder) — never destructive fallback, since
 * this database holds years of a driver's compliance evidence (product spec section 51).
 */
@Database(
    entities = [
        DriverProfileEntity::class,
        LicensingAuthorityEntity::class,
        VehicleEntity::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        InspectionEntity::class,
        InspectionResultEntity::class,
        DefectEntity::class,
        AttachmentEntity::class,
        MileageEntryEntity::class,
        DocumentEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CabComplyDatabase : RoomDatabase() {
    abstract fun driverProfileDao(): DriverProfileDao
    abstract fun licensingAuthorityDao(): LicensingAuthorityDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun inspectionResultDao(): InspectionResultDao
    abstract fun defectDao(): DefectDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun mileageDao(): MileageDao
    abstract fun documentDao(): DocumentDao

    companion object {
        const val DATABASE_NAME = "cabcomply.db"
    }
}
