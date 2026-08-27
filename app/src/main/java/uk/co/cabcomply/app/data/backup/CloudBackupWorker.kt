package uk.co.cabcomply.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uk.co.cabcomply.app.util.AppClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_RETAINED_BACKUPS = 14
private val FILE_NAME_REGEX = Regex("^CabComply_Backup_\\d{4}-\\d{2}-\\d{2}\\.zip$")

/**
 * Runs once daily when the driver has turned on automatic backup, writing a dated zip into the
 * folder they granted access to via Storage Access Framework - the same format and content as a
 * manual backup, just unattended. Prunes older automatic backups so the folder doesn't grow
 * without bound.
 */
@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val cloudBackupPrefs: CloudBackupPrefs,
    private val clock: AppClock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = cloudBackupPrefs.current()
        if (!settings.enabled) return Result.success()
        val treeUriString = settings.treeUri ?: return Result.success()
        val treeUri = Uri.parse(treeUriString)
        val treeDoc = DocumentFile.fromTreeUri(applicationContext, treeUri)
            ?: return failWith("Could not access the selected backup folder.")
        if (!treeDoc.isDirectory || !treeDoc.canWrite()) {
            return failWith("CabComply no longer has permission to write to the selected backup folder.")
        }

        val fileName = "CabComply_Backup_${SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date(clock.nowMillis()))}.zip"
        treeDoc.findFile(fileName)?.delete()
        val target = treeDoc.createFile("application/zip", fileName)
            ?: return failWith("Could not create a new backup file in the selected folder.")

        return when (val result = backupManager.createBackup(target.uri)) {
            is BackupResult.Success -> {
                pruneOldBackups(treeDoc)
                cloudBackupPrefs.recordSuccess(clock.nowMillis())
                Result.success()
            }
            is BackupResult.Failure -> failWith(result.reason)
        }
    }

    private suspend fun failWith(message: String): Result {
        cloudBackupPrefs.recordError(message)
        return Result.retry()
    }

    private fun pruneOldBackups(treeDoc: DocumentFile) {
        treeDoc.listFiles()
            .filter { it.name?.matches(FILE_NAME_REGEX) == true }
            .sortedByDescending { it.name }
            .drop(MAX_RETAINED_BACKUPS)
            .forEach { it.delete() }
    }
}
