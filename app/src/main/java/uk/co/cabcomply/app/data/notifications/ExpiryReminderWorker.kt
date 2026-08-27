package uk.co.cabcomply.app.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import uk.co.cabcomply.app.data.repository.DocumentRepository
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private val REMINDER_INTERVAL_DAYS = listOf(30L, 14L, 7L, 1L)

/**
 * Runs once daily. A document is reminded on the exact day it crosses one of the configured
 * intervals, which naturally rate-limits each document to at most one notification per day and
 * avoids repeated nagging (product spec section 34).
 */
@HiltWorker
class ExpiryReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val notificationPreferences: NotificationPreferences,
    private val notificationHelper: NotificationHelper,
    private val clock: AppClock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!notificationPreferences.remindersEnabled.first()) return Result.success()

        notificationHelper.ensureChannel()
        val today = clock.today()
        val horizon = clock.nowMillis() + TimeUnit.DAYS.toMillis(REMINDER_INTERVAL_DAYS.max())
        val candidates = documentRepository.getExpiringBefore(horizon).filter { it.remindersEnabled }

        candidates.forEach { document ->
            val expiryMillis = document.expiryDate ?: return@forEach
            val expiryDate = Instant.ofEpochMilli(expiryMillis).atZone(clock.zoneId()).toLocalDate()
            val daysRemaining = ChronoUnit.DAYS.between(today, expiryDate)
            if (daysRemaining in REMINDER_INTERVAL_DAYS) {
                val message = "${document.title} expires in $daysRemaining day" +
                    "${if (daysRemaining == 1L) "" else "s"}, on ${DateFormatting.formatDate(expiryMillis)}."
                notificationHelper.showExpiryReminder(
                    notificationId = document.id.hashCode(),
                    title = "Document expiring soon",
                    message = message
                )
            }
        }
        return Result.success()
    }
}
