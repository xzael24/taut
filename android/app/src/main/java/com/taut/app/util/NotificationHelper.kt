package com.taut.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.taut.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationHelper — manages notification channels and display for TAUT.
 *
 * Creates the default notification channel on first use (required for Android 8+).
 * Provides helper to show simple push notifications from FCM or local broadcasts.
 *
 * Notification permission (POST_NOTIFICATIONS on Android 13+) must be
 * requested separately via ActivityResultContracts.RequestPermission.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Primary notification channel for sync & system alerts. */
        const val CHANNEL_SYNC = "taut_sync"
        const val CHANNEL_SYNC_NAME = "Sinkronisasi"
        const val CHANNEL_SYNC_DESC = "Notifikasi status sinkronisasi data"

        /** Notification IDs for different types. */
        const val NOTIFY_SYNC_COMPLETE = 1001
        const val NOTIFY_SYNC_FAILED = 1002
        const val NOTIFY_TRANSACTION = 2001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Create (or re-create) all notification channels.
     * Safe to call multiple times — Android ignores duplicates.
     */
    fun createNotificationChannels() {
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            CHANNEL_SYNC_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_SYNC_DESC
            setShowBadge(true)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(syncChannel)
    }

    /**
     * Show a simple notification with title + body.
     */
    fun showNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        autoCancel: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // On Android 13+, check permission — skip silently if not granted
            if (notificationManager.areNotificationsEnabled().not()) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(autoCancel)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Show a sync-complete notification.
     */
    fun showSyncComplete(count: Int) {
        showNotification(
            channelId = CHANNEL_SYNC,
            notificationId = NOTIFY_SYNC_COMPLETE,
            title = "Sinkronisasi berhasil",
            body = "$count transaksi telah disinkronkan."
        )
    }

    /**
     * Show a sync-failed notification.
     */
    fun showSyncFailed(error: String) {
        showNotification(
            channelId = CHANNEL_SYNC,
            notificationId = NOTIFY_SYNC_FAILED,
            title = "Sinkronisasi gagal",
            body = error
        )
    }
}
