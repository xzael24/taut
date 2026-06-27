package com.taut.app.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FcmService — Firebase Cloud Messaging service for TAUT.
 *
 * Receives push notifications from the backend:
 * - New transactions from remote operators
 * - Sync triggers
 * - System alerts
 *
 * NOTE: Requires google-services.json in the android/app/ directory
 * and the Google Services plugin for actual FCM registration.
 * Without those, this service class compiles but does not auto-register.
 *
 * @HiltAndroidEntryPoint enables field injection for NotificationHelper.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        // Ensure notification channels exist before any message arrives
        notificationHelper.createNotificationChannels()
    }

    /**
     * Called when a new FCM registration token is generated.
     * Send this token to the backend server for push targeting.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(16)}...")
        // TODO: Send token to backend sync service for push notification targeting
        // syncMetadataStore.setFcmToken(token)
    }

    /**
     * Called when a push message is received while app is in foreground.
     * For background messages, the system tray shows the notification
     * defined in the Firebase Console / backend payload.
     *
     * Payload format expected:
     *   { "title": "...", "body": "...", "type": "sync|transaction|alert" }
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "TAUT"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Ada pembaruan dari server."

        val type = message.data["type"] ?: "general"

        when (type) {
            "sync" -> {
                notificationHelper.showSyncComplete(
                    message.data["count"]?.toIntOrNull() ?: 0
                )
            }
            "transaction" -> {
                notificationHelper.showNotification(
                    channelId = NotificationHelper.CHANNEL_SYNC,
                    notificationId = NotificationHelper.NOTIFY_TRANSACTION,
                    title = title,
                    body = body
                )
            }
            else -> {
                notificationHelper.showNotification(
                    channelId = NotificationHelper.CHANNEL_SYNC,
                    notificationId = System.currentTimeMillis().toInt(),
                    title = title,
                    body = body
                )
            }
        }
    }

    companion object {
        private const val TAG = "FcmService"
    }
}
