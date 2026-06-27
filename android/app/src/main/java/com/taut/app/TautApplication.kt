package com.taut.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.taut.app.util.SyncScheduler
import com.taut.app.util.NotificationHelper
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TAUT Application — Platform Daur Ulang Digital
 *
 * @HiltAndroidApp enables Hilt dependency injection for the application.
 * TTS is managed exclusively by AudioManager.TtsManager — see util/AudioManager.kt.
 * Implements Configuration.Provider for Hilt WorkManager integration.
 */
@HiltAndroidApp
class TautApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncSchedulerProvider: Lazy<SyncScheduler>

    @Inject
    lateinit var notificationHelper: NotificationHelper

    /** Application-scoped coroutine scope for init tasks. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provide the WorkManager configuration with Hilt worker factory.
     * This enables @HiltWorker injection in workers.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Create notification channels for push notifications
        try {
            notificationHelper.createNotificationChannels()
        } catch (_: Exception) {
            // Channel creation may fail on some OEMs — non-critical
        }

        // Schedule periodic sync on app start (after WorkManager initializes)
        try {
            WorkManager.getInstance(this) // Force init if needed
        } catch (_: IllegalStateException) {
            // WorkManager not initialized yet — skip
        }
        appScope.launch {
            try {
                syncSchedulerProvider.get().schedulePeriodicSync()
            } catch (e: Exception) {
                // Sync scheduling will happen on next app launch
            }
        }
    }
}
