package uk.co.cabcomply.app.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val WORK_NAME = "cloud_backup_daily"

/**
 * Schedules [CloudBackupWorker] to run once a day. Called both when the driver turns automatic
 * backup on and on every app launch (see AppRootViewModel) so an OEM that clears WorkManager's
 * schedule on reboot doesn't silently and permanently disable a driver's backups.
 */
@Singleton
class CloudBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Runs one backup immediately, e.g. for a "Back up now" button - independent of the daily schedule. */
    fun runOnce() {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<CloudBackupWorker>().build())
    }
}
