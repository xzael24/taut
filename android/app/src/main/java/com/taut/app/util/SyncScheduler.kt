package com.taut.app.util

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.taut.app.data.sync.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WorkManager scheduling for background sync.
 *
 * Schedules:
 * - Periodic sync every 15 minutes (minimum interval) with network constraint
 * - On-demand "Sync Now" via one-time work request
 *
 * Uses [ExistingPeriodicWorkPolicy.UPDATE] so that new constraints (network, battery)
 * take effect immediately on app update without requiring manual intervention.
 *
 * Connectivity is enforced via [NetworkType.CONNECTED] constraint on the WorkRequest
 * itself — WorkManager will only run the worker when a network is available.
 *
 * Uses HiltWorkerFactory for dependency injection in workers.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workerFactory: HiltWorkerFactory
) {

    companion object {
        private const val PERIODIC_SYNC_NAME = "taut_periodic_sync"
        private const val ONE_TIME_SYNC_NAME = "taut_sync_now"

        /** Minimum periodic sync interval (15 minutes). */
        private const val SYNC_INTERVAL_MINUTES = 15L
    }

    /**
     * Schedule periodic background sync.
     *
     * Constraints:
     * - Network required (any type — WiFi or cellular)
     * - Battery not low
     * - Device idle (optional, for battery friendliness)
     *
     * Uses [ExistingPeriodicWorkPolicy.UPDATE] to ensure the latest constraints
     * are applied on every app launch.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(PERIODIC_SYNC_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_SYNC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
    }

    /**
     * Trigger an immediate one-time sync.
     *
     * This is called from SyncViewModel for the "Sync Now" button.
     * If a sync is already running, it will be enqueued as a new attempt.
     */
    fun triggerSyncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(ONE_TIME_SYNC_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueue(syncRequest)
    }

    /**
     * Cancel all pending sync work.
     */
    fun cancelAllSync() {
        WorkManager.getInstance(context).cancelAllWorkByTag(PERIODIC_SYNC_NAME)
        WorkManager.getInstance(context).cancelAllWorkByTag(ONE_TIME_SYNC_NAME)
    }
}
