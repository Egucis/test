package uk.co.cabcomply.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.cabcomply.app.data.db.CabComplyDatabase
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
import uk.co.cabcomply.app.util.AppClock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, driver-controlled backup and restore. A backup is a single zip file: a JSON manifest of
 * every record plus every referenced photo. Restore validates the whole archive before touching
 * the live database, and replaces its contents inside one transaction so a failure never leaves
 * a partially-restored state (product spec sections 47, 48, 51).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CabComplyDatabase,
    private val driverProfileDao: DriverProfileDao,
    private val licensingAuthorityDao: LicensingAuthorityDao,
    private val vehicleDao: VehicleDao,
    private val checklistDao: ChecklistDao,
    private val inspectionDao: InspectionDao,
    private val inspectionResultDao: InspectionResultDao,
    private val defectDao: DefectDao,
    private val attachmentDao: AttachmentDao,
    private val mileageDao: MileageDao,
    private val documentDao: DocumentDao,
    private val clock: AppClock
) {
    private val gson = GsonBuilder().create()

    suspend fun createBackup(destination: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val manifest = buildManifest()
            val outputStream = context.contentResolver.openOutputStream(destination)
                ?: return@withContext BackupResult.Failure("Could not open the selected location to write the backup.")

            ZipOutputStream(outputStream).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_MANIFEST_ENTRY_NAME))
                zip.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                manifest.attachments.forEach { attachment ->
                    val file = File(context.filesDir, attachment.filePath)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry(BACKUP_PHOTOS_ENTRY_PREFIX + attachment.filePath.removePrefix("photos/")))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            BackupResult.Success(BackupSummary.from(manifest), fileName = "CabComply backup")
        } catch (e: Exception) {
            BackupResult.Failure("The backup could not be created. Please try again.")
        }
    }

    suspend fun restoreBackup(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${clock.nowMillis()}")
        try {
            val inputStream = context.contentResolver.openInputStream(source)
                ?: return@withContext RestoreResult.Failure("Could not open the selected backup file.")

            var manifest: BackupManifest? = null
            stagingDir.mkdirs()

            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == BACKUP_MANIFEST_ENTRY_NAME -> {
                            val json = zip.readBytes().toString(Charsets.UTF_8)
                            manifest = gson.fromJson(json, BackupManifest::class.java)
                        }
                        entry.name.startsWith(BACKUP_PHOTOS_ENTRY_PREFIX) -> {
                            val target = File(stagingDir, entry.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val validManifest = manifest
                ?: return@withContext RestoreResult.Failure("This file is not a valid CabComply backup.")
            if (validManifest.schemaVersion > BACKUP_SCHEMA_VERSION) {
                return@withContext RestoreResult.Failure(
                    "This backup was made with a newer version of CabComply. Update the app before restoring it."
                )
            }

            applyManifest(validManifest, stagingDir)
            RestoreResult.Success(BackupSummary.from(validManifest))
        } catch (e: Exception) {
            RestoreResult.Failure("This backup could not be read — it may be corrupted or incomplete.")
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun buildManifest(): BackupManifest {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        return BackupManifest(
            appVersionName = versionName,
            exportedAtMillis = clock.nowMillis(),
            driverProfiles = driverProfileDao.getAll(),
            licensingAuthorities = licensingAuthorityDao.getAll(),
            vehicles = vehicleDao.getAll(),
            checklists = checklistDao.getAllChecklistsSnapshot(),
            checklistItems = checklistDao.getAllItemsSnapshot(),
            inspections = inspectionDao.getAll(),
            inspectionResults = inspectionResultDao.getAll(),
            defects = defectDao.getAll(),
            attachments = attachmentDao.getAll(),
            mileageEntries = mileageDao.getAll(),
            documents = documentDao.getAll()
        )
    }

    /** Replaces all live data with the manifest's contents, and only then moves staged photos into place. */
    private suspend fun applyManifest(manifest: BackupManifest, stagingDir: File) {
        database.withTransaction {
            inspectionResultDao.deleteAll()
            defectDao.deleteAll()
            attachmentDao.deleteAll()
            inspectionDao.deleteAll()
            mileageDao.deleteAll()
            documentDao.deleteAll()
            checklistDao.deleteAllItems()
            checklistDao.deleteAllChecklists()
            vehicleDao.deleteAll()
            licensingAuthorityDao.deleteAll()
            driverProfileDao.deleteAll()

            manifest.driverProfiles.forEach { driverProfileDao.upsert(it) }
            manifest.licensingAuthorities.forEach { licensingAuthorityDao.upsert(it) }
            manifest.vehicles.forEach { vehicleDao.upsert(it) }
            checklistDao.insertChecklists(manifest.checklists)
            checklistDao.insertItems(manifest.checklistItems)
            manifest.inspections.forEach { inspectionDao.upsert(it) }
            inspectionResultDao.insertAll(manifest.inspectionResults)
            defectDao.insertAll(manifest.defects)
            attachmentDao.insertAll(manifest.attachments)
            manifest.mileageEntries.forEach { mileageDao.upsert(it) }
            manifest.documents.forEach { documentDao.upsert(it) }
        }

        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val stagedPhotosDir = File(stagingDir, BACKUP_PHOTOS_ENTRY_PREFIX)
        if (stagedPhotosDir.exists()) {
            stagedPhotosDir.listFiles()?.forEach { staged ->
                staged.copyTo(File(photosDir, staged.name), overwrite = true)
            }
        }
    }
}
