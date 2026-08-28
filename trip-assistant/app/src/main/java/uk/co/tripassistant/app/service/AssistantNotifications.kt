package uk.co.tripassistant.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import uk.co.tripassistant.app.MainActivity
import uk.co.tripassistant.app.R

/**
 * The persistent notice Android requires while a projection is running (spec section 9).
 *
 * It is also the second place the assistant can be stopped from (spec section 37), because the
 * driver is usually looking at Uber, not at this app, when they want it to stop.
 */
object AssistantNotifications {

    const val CHANNEL_ID = "assistant_status"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, AssistantService::class.java).setAction(AssistantService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
