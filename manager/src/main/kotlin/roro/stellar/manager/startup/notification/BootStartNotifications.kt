package roro.stellar.manager.startup.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import roro.stellar.manager.R

object BootStartNotifications {

    const val CHANNEL_ID = "boot_start"
    const val NOTIFICATION_ID = 1447

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.boot_start_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.boot_start_channel_description)
            setShowBadge(false)
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    fun buildStartingNotification(context: Context, message: String? = null): Notification {
        val retryIntent = Intent(context, BootStartActionReceiver::class.java).apply {
            action = BootStartActionReceiver.ACTION_RETRY
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            context, 0, retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, BootStartActionReceiver::class.java).apply {
            action = BootStartActionReceiver.ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stellar)
            .setContentTitle(context.getString(R.string.boot_start_notification_title))
            .setContentText(message ?: context.getString(R.string.boot_start_enabling_wireless_adb))
            .setOngoing(true)
            .addAction(
                0,
                context.getString(R.string.boot_start_retry),
                retryPendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.cancel),
                cancelPendingIntent
            )
            .build()
    }

    fun showNotification(context: Context, message: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildStartingNotification(context, message))
    }

    fun dismiss(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
    }
}
