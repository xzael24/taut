package com.taut.service

import com.taut.core.v1.*
import com.taut.core.v1.SyncServiceGrpcKt.SyncServiceCoroutineImplBase
import com.taut.db.Jdbc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * gRPC SyncService implementation.
 *
 * Handles bidirectional streaming sync for offline-first transaction management.
 * Uses [CatalogService] and [TransactionService] for data operations.
 *
 * Proto: taut.core.v1.SyncService
 *   - Sync(stream SyncBatch) returns (stream SyncResponse)
 *   - SyncUnary(SyncBatch) returns (SyncResponse)
 *   - GetSyncStatus(SyncStatusRequest) returns (SyncStatusResponse)
 *   - PushTransaction(Transaction) returns (AckEntry)
 */
class SyncServiceImpl : SyncServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(SyncServiceImpl::class.java)
    private val clock = Clock.systemUTC()

    /**
     * Bidirectional streaming sync — the primary sync channel.
     *
     * Client opens the stream with a [SyncBatch]; server processes each batch
     * and responds with [SyncResponse] messages containing acknowledgments,
     * catalog updates, and new lamport timestamps.
     */
    override fun sync(requests: Flow<SyncBatch>): Flow<SyncResponse> = flow {
        log.info("[gRPC Sync] Client opened bidirectional sync stream")

        var latestLamport = 0L
        var latestCursor: String? = null

        requests.collect { batch ->
            val metadata = batch.metadata
            log.info(
                "[gRPC Sync] Received batch from device={}, txCount={}, lamport={}",
                metadata?.deviceId ?: "unknown",
                batch.transactionsCount,
                metadata?.lamportTimestamp ?: 0L
            )

            // Update lamport clock from client metadata
            if (metadata != null) {
                latestLamport = maxOf(latestLamport, metadata.lamportTimestamp)
            }

            // Process each transaction in the batch
            val acks = mutableListOf<AckEntry>()
            for (tx in batch.transactionsList) {
                val ack = processTransaction(tx)
                acks.add(ack)
            }

            // Include catalog data in sync response
            val catalogData = fetchCatalogForSync()

            // Build response
            val response = SyncResponse.newBuilder()
                .addAllAcks(acks)
                .addAllCatalogUpdates(catalogData.categories)
                .setCatalogVersion(catalogData.version)
                .setNewLamportTimestamp(latestLamport + 1)
                .setNewCursor(latestCursor ?: UUID.randomUUID().toString())
                .setServerTime(
                    Timestamp.newBuilder()
                        .setSeconds(clock.instant().epochSecond)
                        .setNanos(clock.instant().nano)
                        .build()
                )
                .build()

            emit(response)
        }

        log.info("[gRPC Sync] Client closed bidirectional sync stream")
    }

    /**
     * One-shot sync for small payloads or initial connection.
     */
    override suspend fun syncUnary(request: SyncBatch): SyncResponse {
        log.info(
            "[gRPC SyncUnary] device={}, txCount={}",
            request.metadata?.deviceId ?: "unknown",
            request.transactionsCount
        )

        val acks = mutableListOf<AckEntry>()
        for (tx in request.transactionsList) {
            val ack = processTransaction(tx)
            acks.add(ack)
        }

        val lamport = request.metadata?.lamportTimestamp?.let { it + 1 } ?: 1L

        // Include catalog data in sync response
        val catalogData = fetchCatalogForSync()

        return SyncResponse.newBuilder()
            .addAllAcks(acks)
            .addAllCatalogUpdates(catalogData.categories)
            .setCatalogVersion(catalogData.version)
            .setNewLamportTimestamp(lamport)
            .setNewCursor(UUID.randomUUID().toString())
            .setServerTime(
                Timestamp.newBuilder()
                    .setSeconds(clock.instant().epochSecond)
                    .setNanos(clock.instant().nano)
                    .build()
            )
            .build()
    }

    /**
     * Get current sync status for a device.
     */
    override suspend fun getSyncStatus(request: SyncStatusRequest): SyncStatusResponse {
        log.info("[gRPC GetSyncStatus] device_id={}", request.deviceId)

        val deviceId = request.deviceId

        // Query the database for pending and failed transactions
        val pendingCount: Int = if (deviceId.isNotBlank()) {
            try {
                Jdbc.withConn { conn ->
                    Jdbc.count(conn,
                        "SELECT COUNT(*) FROM transactions WHERE status = 'PENDING_SYNC' AND sync_id IS NOT NULL"
                    ).toInt()
                }
            } catch (e: Exception) {
                log.warn("[gRPC GetSyncStatus] Could not query pending count: {}", e.message)
                0
            }
        } else 0

        val failedCount: Int = if (deviceId.isNotBlank()) {
            try {
                Jdbc.withConn { conn ->
                    Jdbc.count(conn,
                        "SELECT COUNT(*) FROM transactions WHERE status = 'FAILED' AND sync_id IS NOT NULL"
                    ).toInt()
                }
            } catch (e: Exception) {
                log.warn("[gRPC GetSyncStatus] Could not query failed count: {}", e.message)
                0
            }
        } else 0

        return SyncStatusResponse.newBuilder()
            .setLastLamportTimestamp(0)
            .setLastCursor("")
            .setLastSyncAt(
                Timestamp.newBuilder()
                    .setSeconds(clock.instant().epochSecond)
                    .setNanos(clock.instant().nano)
                    .build()
            )
            .setPendingCount(pendingCount)
            .setFailedCount(failedCount)
            .build()
    }

    /**
     * Force-push a specific transaction (admin/operator override).
     */
    override suspend fun pushTransaction(request: Transaction): AckEntry {
        log.info("[gRPC PushTransaction] tx_id={}", request.id.value)

        val requestMap = transactionToMap(request)
        val result = TransactionService.createTransaction(requestMap)

        val errorMessage = if (result.containsKey("error")) {
            (result["message"] as? String) ?: (result["error"] as? String) ?: "UNKNOWN"
        } else null

        return AckEntry.newBuilder()
            .setLocalId(request.id)
            .setServerId(
                Uuid.newBuilder()
                    .setValue(result["transaction_id"] as? String ?: UUID.randomUUID().toString())
                    .build()
            )
            .setStatus(
                if (errorMessage == null) SyncStatus.CONFIRMED
                else SyncStatus.FAILED
            )
            .setErrorMessage(errorMessage ?: "")
            .build()
    }

    // ── Private helpers ──

    /**
     * Process a single [Transaction] from a sync batch, delegating to [TransactionService].
     */
    private fun processTransaction(tx: Transaction): AckEntry {
        val requestMap = transactionToMap(tx)
        val result = TransactionService.createTransaction(requestMap)

        val errorMessage = if (result.containsKey("error")) {
            (result["message"] as? String) ?: (result["error"] as? String) ?: "UNKNOWN"
        } else null

        return AckEntry.newBuilder()
            .setLocalId(tx.id)
            .setServerId(
                Uuid.newBuilder()
                    .setValue(result["transaction_id"] as? String ?: UUID.randomUUID().toString())
                    .build()
            )
            .setStatus(
                if (errorMessage == null) SyncStatus.CONFIRMED
                else SyncStatus.FAILED
            )
            .setErrorMessage(errorMessage ?: "")
            .build()
    }

    /**
     * Convert a protobuf [Transaction] to the [Map] format expected by [TransactionService].
     */
    private fun transactionToMap(tx: Transaction): Map<String, Any?> {
        val items = tx.itemsList.map { item ->
            mapOf(
                "category_id" to item.categoryId.value,
                "weight" to item.weightGrams
            )
        }

        return mapOf(
            "bank_sampah_id" to tx.bankSampahId.value,
            "operator_id" to tx.operatorId.value,
            "customer_id" to tx.customerId.value,
            "items" to items,
            "device_timestamp" to tx.deviceTimestamp?.let { ts ->
                Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()).toString()
            },
            "weight_photo_url" to (tx.weightPhotoUrl.ifEmpty { null }),
            "sync_id" to (tx.syncId.value.ifEmpty { null }),
            "is_offline_created" to tx.isOfflineCreated,
            "lamport_timestamp" to tx.lamportTimestamp,
            "hmac_signature" to (tx.hmacSignature.ifEmpty { null }),
            "idempotency_key" to "grpc-sync-${tx.id.value}"
        )
    }

    /**
     * Fetch catalog data (categories + version) from [CatalogService] for sync responses.
     */
    private data class CatalogSyncData(
        val categories: List<WasteCategory>,
        val version: Int
    )

    private fun fetchCatalogForSync(): CatalogSyncData {
        return try {
            val catalogResult = CatalogService.listCategories()
            @Suppress("UNCHECKED_CAST")
            val rawCategories = catalogResult["data"] as? List<Map<String, Any?>> ?: emptyList()
            val catalogVersion = catalogResult["version"] as? Int ?: 1

            val categories = rawCategories.mapNotNull { row -> rowToWasteCategory(row) }

            CatalogSyncData(categories, catalogVersion)
        } catch (e: Exception) {
            log.warn("[gRPC Sync] Failed to fetch catalog: {}", e.message)
            CatalogSyncData(emptyList(), 0)
        }
    }

    /**
     * Convert a catalog row [Map] to a protobuf [WasteCategory].
     */
    private fun rowToWasteCategory(row: Map<String, Any?>): WasteCategory? {
        return try {
            WasteCategory.newBuilder()
                .setId(
                    Uuid.newBuilder().setValue(row["id"] as? String ?: "").build()
                )
                .setCode(row["code"] as? String ?: "")
                .setNameId(row["name_id"] as? String ?: "")
                .setNameEn(row["name_en"] as? String ?: "")
                .setCategoryGroup(row["category_group"] as? String ?: "")
                .setUnitType(row["unit_type"] as? String ?: "kg")
                .setUnitPrice((row["unit_price"] as? Number)?.toLong() ?: 0L)
                .setPhotoUrl(row["photo_url"] as? String ?: "")
                .setSortOrder((row["sort_order"] as? Number)?.toInt() ?: 0)
                .setIsActive(true)
                .setCreatedAt(parseTimestamp(row["created_at"] as? String))
                .setUpdatedAt(parseTimestamp(row["updated_at"] as? String))
                .build()
        } catch (e: Exception) {
            log.warn("[gRPC Sync] Failed to convert catalog row: {}", e.message)
            null
        }
    }

    /**
     * Parse an ISO timestamp string to a protobuf [Timestamp].
     */
    private fun parseTimestamp(isoString: String?): Timestamp {
        return try {
            val instant = if (!isoString.isNullOrBlank()) {
                Instant.parse(isoString)
            } else {
                clock.instant()
            }
            Timestamp.newBuilder()
                .setSeconds(instant.epochSecond)
                .setNanos(instant.nano)
                .build()
        } catch (e: Exception) {
            val instant = clock.instant()
            Timestamp.newBuilder()
                .setSeconds(instant.epochSecond)
                .setNanos(instant.nano)
                .build()
        }
    }
}
