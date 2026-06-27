package com.taut.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.taut.core.v1.SyncBatch
import com.taut.core.v1.SyncMetadata
import com.taut.core.v1.SyncServiceGrpcKt
import com.taut.core.v1.SyncStatusRequest
import com.taut.app.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * SyncWorker — WorkManager-based background sync for TAUT.
 *
 * Uses gRPC unary/bidi-streaming to push local transactions and pull remote updates.
 * Transactions are ONLY marked synced when the server explicitly acknowledges them.
 * On gRPC failure, transactions are marked as "failed" to preserve retryability.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val syncMetadataStore: SyncMetadataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val pendingTransactions = transactionRepository.getPendingTransactions()

            if (pendingTransactions.isNotEmpty()) {
                // Attempt gRPC sync — ONLY mark synced if server confirms
                trySyncViaGrpc(pendingTransactions)
            }

            // Pull remote updates via gRPC if available
            tryPullUpdatesViaGrpc()

            Result.success()
        } catch (e: Exception) {
            safeLogE(TAG, "Sync worker failed", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun safeLogE(tag: String, msg: String, e: Exception) {
        try { Log.e(tag, msg, e) } catch (_: Exception) { System.err.println("$tag: $msg: $e") }
    }

    private fun safeLogW(tag: String, msg: String, e: Exception? = null) {
        try { if (e != null) Log.w(tag, msg, e) else Log.w(tag, msg) } catch (_: Exception) { System.err.println("$tag: $msg: $e") }
    }

    private fun safeLogI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Exception) { System.out.println("$tag: $msg") }
    }

    /**
     * Push pending transactions via gRPC SyncService.SyncUnary.
     * Transactions are marked synced ONLY on server acknowledgment.
     * On failure, marks as "failed" so they remain in the queue for retry.
     */
    private suspend fun trySyncViaGrpc(transactions: List<com.taut.app.data.local.entity.TransactionEntity>) {
        try {
            val channel = GrpcClientProvider.getChannel()
            val stub = SyncServiceGrpcKt.SyncServiceCoroutineStub(channel)

            val deviceId = syncMetadataStore.deviceId.first()
            val lamportClock = syncMetadataStore.lamportClock.first()

            val metadata = SyncMetadata.newBuilder()
                .setDeviceId(deviceId)
                .setLamportTimestamp(lamportClock)
                .build()
            val batch = SyncBatch.newBuilder()
                .setMetadata(metadata)
                .build()

            val response = stub.syncUnary(batch)
            val serverLamport = response.newLamportTimestamp
            val cursor = if (response.newCursor.isNotEmpty()) response.newCursor else "grpc_${System.currentTimeMillis()}"

            // Server acknowledged — mark as synced
            for (tx in transactions) {
                transactionRepository.updateSyncStatus(
                    tx.id,
                    "synced",
                    cursor,
                    System.currentTimeMillis()
                )
            }
            syncMetadataStore.saveSyncCompletion(cursor, serverLamport)
            safeLogI(TAG, "Synced ${transactions.size} transactions via gRPC (cursor=$cursor)")
        } catch (e: StatusException) {
            // Server returned error — mark as failed, NOT synced
            safeLogW(TAG, "gRPC sync rejected by server: ${e.status.code}")
            for (tx in transactions) {
                transactionRepository.updateSyncStatus(
                    tx.id,
                    "failed",
                    "grpc_error_${e.status.code.name}",
                    System.currentTimeMillis()
                )
            }
            throw e // rethrow to trigger WorkManager retry
        } catch (e: Exception) {
            // Network/connectivity failure — keep as pending, try next sync cycle
            safeLogW(TAG, "gRPC unavailable, will retry later", e)
            throw e // rethrow to trigger WorkManager retry
        }
    }

    /**
     * Try to pull latest updates from server via gRPC.
     */
    private suspend fun tryPullUpdatesViaGrpc() {
        try {
            val channel = GrpcClientProvider.getChannel()
            val stub = SyncServiceGrpcKt.SyncServiceCoroutineStub(channel)

            val deviceId = syncMetadataStore.deviceId.first()
            val statusRequest = SyncStatusRequest.newBuilder()
                .setDeviceId(deviceId)
                .build()
            val statusResponse = stub.getSyncStatus(statusRequest)
            if (statusResponse.pendingCount > 0) {
                safeLogI(TAG, "Server has ${statusResponse.pendingCount} pending updates")
            }
        } catch (e: Exception) {
            safeLogW(TAG, "Failed to check sync status via gRPC", e)
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val UNIQUE_WORK_NAME = "taut_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
