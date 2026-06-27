package com.taut.service

import com.taut.db.Jdbc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * TransactionService — waste deposit transaction business logic.
 * Uses raw JDBC SQL via Jdbc helper for reliable compilation.
 */
object TransactionService {

    private val log = LoggerFactory.getLogger(TransactionService::class.java)
    private val clock = Clock.systemUTC()
    private const val FRAUD_WEIGHT_THRESHOLD_GRAMS = 10_000L

    fun createTransaction(request: Map<String, Any?>): Map<String, Any?> {
        val nowL = nowLdt()

        val bankSampahIdStr = request["bank_sampah_id"] as? String
            ?: return mapOf("error" to "MISSING_FIELD", "message" to "Field bank_sampah_id wajib diisi.")
        val operatorIdStr = request["operator_id"] as? String
            ?: return mapOf("error" to "MISSING_FIELD", "message" to "Field operator_id wajib diisi.")
        val customerIdStr = request["customer_id"] as? String
            ?: return mapOf("error" to "MISSING_FIELD", "message" to "Field customer_id wajib diisi.")
        val idempotencyKey = request["idempotency_key"] as? String ?: ""
        val deviceTimestamp = request["device_timestamp"] as? String
        val weightPhotoUrl = request["weight_photo_url"] as? String
        val syncId = request["sync_id"] as? String
        val isOfflineCreated = request["is_offline_created"] as? Boolean ?: true
        val lamportTimestamp = (request["lamport_timestamp"] as? Number)?.toLong()
        val hmacSignature = request["hmac_signature"] as? String
        val deviceIdStr = request["device_id"] as? String

        @Suppress("UNCHECKED_CAST")
        val itemsRaw = request["items"] as? List<Map<String, Any?>>
            ?: return mapOf("error" to "MISSING_FIELD", "message" to "Field items wajib diisi.")
        if (itemsRaw.isEmpty()) return mapOf("error" to "EMPTY_ITEMS", "message" to "Transaksi harus memiliki minimal 1 item.")

        val bankSampahId = try { UUID.fromString(bankSampahIdStr) } catch (_: Exception) { return mapOf("error" to "INVALID_ID", "message" to "Format bank_sampah_id tidak valid.") }
        val operatorId = try { UUID.fromString(operatorIdStr) } catch (_: Exception) { return mapOf("error" to "INVALID_ID", "message" to "Format operator_id tidak valid.") }
        val customerId = try { UUID.fromString(customerIdStr) } catch (_: Exception) { return mapOf("error" to "INVALID_ID", "message" to "Format customer_id tidak valid.") }

        // ── HMAC-SHA256 signature verification (MAJOR-5 fix) ──
        if (hmacSignature != null && deviceIdStr != null) {
            val deviceUuid = try { UUID.fromString(deviceIdStr) } catch (_: Exception) { null }
            if (deviceUuid == null) {
                return mapOf("error" to "UNAUTHORIZED", "message" to "Device ID tidak valid.")
            }

            // Look up device's public key from the devices table
            val deviceRow = Jdbc.withConn { conn ->
                Jdbc.querySingle(conn, "SELECT device_pub_key FROM devices WHERE id = ? AND is_active = true", listOf(deviceUuid))
            }
            if (deviceRow == null) {
                return mapOf("error" to "UNAUTHORIZED", "message" to "Device tidak ditemukan atau tidak aktif.")
            }

            val devicePubKey = deviceRow["device_pub_key"] as? String
            if (devicePubKey.isNullOrBlank()) {
                return mapOf("error" to "UNAUTHORIZED", "message" to "Device public key tidak ditemukan.")
            }

            // Verify HMAC-SHA256 signature
            try {
                // Build the message payload that was signed (canonical form)
                val messagePayload = buildHmacPayload(
                    bankSampahIdStr, operatorIdStr, customerIdStr, itemsRaw,
                    deviceTimestamp ?: "", idempotencyKey, devicePubKey
                )
                val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                val secretKey = javax.crypto.spec.SecretKeySpec(devicePubKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
                mac.init(secretKey)
                val computedSignature = java.util.Base64.getEncoder().encodeToString(mac.doFinal(messagePayload.toByteArray(Charsets.UTF_8)))

                if (computedSignature != hmacSignature) {
                    log.warn("HMAC signature mismatch for device {}: computed={}, provided={}", deviceIdStr, computedSignature, hmacSignature)
                    return mapOf("error" to "UNAUTHORIZED", "message" to "HMAC signature tidak valid.")
                }
                log.debug("HMAC signature verified successfully for device {}", deviceIdStr)
            } catch (e: Exception) {
                log.error("HMAC verification error: {}", e.message, e)
                return mapOf("error" to "UNAUTHORIZED", "message" to "HMAC signature verification gagal.")
            }
        } else if (hmacSignature != null && deviceIdStr == null) {
            // Signature provided but no device_id — reject
            return mapOf("error" to "UNAUTHORIZED", "message" to "Device ID diperlukan untuk verifikasi HMAC.")
        }
        // If no hmac_signature is provided (offline-created transactions), skip verification
        // ── End HMAC verification ──

        // Idempotency check
        if (idempotencyKey.isNotBlank()) {
            val existing = Jdbc.withConn { conn ->
                Jdbc.querySingle(conn, "SELECT * FROM processed_ids WHERE idempotency_key = ?", listOf(idempotencyKey))
            }
            if (existing != null) return mapOf("error" to "DUPLICATE", "message" to "Transaksi sudah pernah diproses.", "transaction_id" to existing["result_id"])
        }

        // Validate categories
        val categoryIds = itemsRaw.mapNotNull { (it["category_id"] as? String) }.distinct()
            .mapNotNull { try { UUID.fromString(it) } catch (_: Exception) { null } }
        if (categoryIds.size != itemsRaw.size) return mapOf("error" to "INVALID_CATEGORY", "message" to "Format category_id tidak valid.")

        // Fetch active categories with their prices
        val placeholders = categoryIds.joinToString(",") { "?" }
        val catRows = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn,
                "SELECT id, name_id, unit_price, category_group FROM waste_categories WHERE id IN ($placeholders) AND is_active = true",
                categoryIds.map { it as Any? })
        }
        val categoryMap = catRows.associate { row ->
            (row["id"] as UUID) to Pair(row["name_id"] as? String ?: "", (row["unit_price"] as? Number)?.toLong() ?: 0L)
        }

        val missingCategories = categoryIds.filter { it !in categoryMap }
        if (missingCategories.isNotEmpty()) return mapOf("error" to "INVALID_CATEGORY", "message" to "Kategori tidak valid: $missingCategories")

        // Calculate totals
        var totalWeight = 0L
        var totalValue = 0L

        for (item in itemsRaw) {
            val catId = UUID.fromString(item["category_id"] as String)
            val weight = (item["weight"] as? Number)?.toLong() ?: return mapOf("error" to "INVALID_WEIGHT")
            if (weight <= 0) return mapOf("error" to "INVALID_WEIGHT", "message" to "Weight harus > 0")
            val pricePerUnit = categoryMap[catId]!!.second
            val itemValue = calculateItemValue(weight, pricePerUnit)
            totalWeight += weight; totalValue += itemValue
        }

        // Fraud check
        var fraudFlag = false
        var fraudReason: String? = null
        if (totalWeight > FRAUD_WEIGHT_THRESHOLD_GRAMS && weightPhotoUrl.isNullOrBlank()) {
            fraudFlag = true; fraudReason = "Berat >10kg tanpa foto"
        }
        val status = if (isOfflineCreated) "PENDING_SYNC" else "CONFIRMED"

        // Insert using raw SQL
        val transactionId = UUID.randomUUID()

        Jdbc.withTransaction { conn ->
            Jdbc.exec(conn, """
                INSERT INTO transactions (id, bank_sampah_id, operator_id, customer_id, transaction_type, status,
                    total_weight, total_value, device_timestamp, server_timestamp, sync_id, is_offline_created,
                    lamport_timestamp, hmac_signature, weight_photo_url, fraud_flag, fraud_reason, sms_sent,
                    price_snapshot, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(), listOf(
                transactionId, bankSampahId, operatorId, customerId, "DEPOSIT", status,
                totalWeight, totalValue,
                deviceTimestamp?.let { try { Instant.parse(it).atOffset(ZoneOffset.UTC).toLocalDateTime() } catch (_: Exception) { null } },
                nowL, syncId, isOfflineCreated,
                lamportTimestamp, hmacSignature, weightPhotoUrl, fraudFlag, fraudReason, false,
                Json.encodeToString(JsonObject.serializer(), JsonObject(categoryIds.associate {
                    it.toString() to JsonPrimitive(categoryMap[it]?.second ?: 0)
                })),
                nowL, nowL
            ))

            // Insert items
            for (item in itemsRaw) {
                val catId = UUID.fromString(item["category_id"] as String)
                val wgt = (item["weight"] as Number).toLong()
                val ppu = categoryMap[catId]!!.second
                val ival = calculateItemValue(wgt, ppu)
                Jdbc.exec(conn, """
                    INSERT INTO transaction_items (id, transaction_id, category_id, weight, price_per_unit, total_value, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(), listOf(UUID.randomUUID(), transactionId, catId, wgt, ppu, ival, nowL))
            }

            // Idempotency record
            if (idempotencyKey.isNotBlank()) {
                Jdbc.exec(conn, "INSERT INTO processed_ids (id, idempotency_key, result_type, result_id, created_at) VALUES (?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), idempotencyKey, "transaction", transactionId.toString(), nowL))
            }
        }

        // Queue SMS receipt
        try {
            val userRow = Jdbc.withConn { conn ->
                Jdbc.querySingle(conn, "SELECT * FROM users WHERE id = ?", listOf(customerId))
            }
            if (userRow != null) {
                val bankRow = Jdbc.withConn { conn ->
                    Jdbc.querySingle(conn, "SELECT name FROM waste_banks WHERE id = ?", listOf(bankSampahId))
                }
                val phone = userRow["phone_number"] as? String ?: ""
                val name = userRow["name"] as? String ?: ""
                val bName = bankRow?.get("name") as? String ?: "Bank Sampah"
                if (phone.isNotBlank()) SmsService.sendTransactionReceipt(phone, name, totalValue, bName)
            }
        } catch (e: Exception) {
            log.error("Failed to send transaction receipt SMS: {}", e.message, e)
        }

        logAudit(operatorIdStr, "transaction:create", "transaction", transactionId.toString(), "Deposit created, weight=${totalWeight}g, value=$totalValue")
        return mapOf("transaction_id" to transactionId.toString(), "status" to status, "total_weight" to totalWeight, "total_value" to totalValue, "item_count" to itemsRaw.size, "fraud_flag" to fraudFlag)
    }

    fun getTransaction(transactionId: String): Map<String, Any?>? {
        val txId = try { UUID.fromString(transactionId) } catch (_: Exception) { return null }

        return Jdbc.withConn { conn ->
            val txRow = Jdbc.querySingle(conn, "SELECT * FROM transactions WHERE id = ?", listOf(txId))
                ?: return@withConn null

            val items = Jdbc.queryMap(conn, "SELECT * FROM transaction_items WHERE transaction_id = ?", listOf(txId)).map { row ->
                mapOf("id" to (row["id"] as UUID).toString(), "category_id" to (row["category_id"] as UUID).toString(),
                    "weight" to ((row["weight"] as? Number)?.toLong() ?: 0L),
                    "price_per_unit" to ((row["price_per_unit"] as? Number)?.toLong() ?: 0L),
                    "total_value" to ((row["total_value"] as? Number)?.toLong() ?: 0L))
            }

            val bankName = Jdbc.querySingle(conn, "SELECT name FROM waste_banks WHERE id = ?", listOf(txRow["bank_sampah_id"]))?.get("name") as? String ?: ""
            val customerName = Jdbc.querySingle(conn, "SELECT name FROM users WHERE id = ?", listOf(txRow["customer_id"]))?.get("name") as? String ?: ""

            mapOf("id" to txRow["id"].toString(), "bank_sampah_id" to txRow["bank_sampah_id"].toString(),
                "bank_name" to bankName, "customer_name" to customerName,
                "status" to txRow["status"], "total_weight" to txRow["total_weight"],
                "total_value" to txRow["total_value"], "created_at" to txRow["created_at"]?.toString(),
                "updated_at" to txRow["updated_at"]?.toString(), "items" to items)
        }
    }

    fun listTransactions(
        bankSampahId: String,
        fromDate: String? = null,
        toDate: String? = null,
        type: String? = null,
        status: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Map<String, Any?> {
        val bankId = try { UUID.fromString(bankSampahId) } catch (_: Exception) {
            return mapOf("data" to emptyList<Any>(), "pagination" to mapOf("total_count" to 0))
        }
        val offs = ((page - 1) * pageSize)
        val conditions = mutableListOf("bank_sampah_id = ?")
        val params = mutableListOf<Any?>(bankId)

        if (type != null) { conditions.add("transaction_type = ?"); params.add(type) }
        if (status != null) { conditions.add("status = ?"); params.add(status) }
        if (fromDate != null) {
            val f = try { Instant.parse(fromDate).atOffset(ZoneOffset.UTC).toLocalDateTime() } catch (_: Exception) { null }
            if (f != null) { conditions.add("created_at >= ?"); params.add(f) }
        }
        if (toDate != null) {
            val t = try { Instant.parse(toDate).atOffset(ZoneOffset.UTC).toLocalDateTime() } catch (_: Exception) { null }
            if (t != null) { conditions.add("created_at <= ?"); params.add(t) }
        }
        val where = conditions.joinToString(" AND ")

        val totalCount = Jdbc.withConn { conn ->
            Jdbc.count(conn, "SELECT COUNT(*) FROM transactions WHERE $where", params)
        }
        val rows = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn,
                "SELECT * FROM transactions WHERE $where ORDER BY created_at DESC LIMIT ? OFFSET ?",
                params + listOf(pageSize, offs))
        }

        return mapOf("data" to rows.map { txRowToBrief(it) },
            "pagination" to mapOf("page" to page, "page_size" to pageSize, "total_count" to totalCount, "total_pages" to ((totalCount + pageSize - 1) / pageSize)))
    }

    fun voidTransaction(transactionId: String, reason: String, adminId: String, idempotencyKey: String): Map<String, Any?> {
        val nowL = nowLdt()
        val txId = try { UUID.fromString(transactionId) } catch (_: Exception) { return mapOf("error" to "INVALID_ID") }
        val adminUuid = try { UUID.fromString(adminId) } catch (_: Exception) { return mapOf("error" to "INVALID_ID") }

        if (idempotencyKey.isNotBlank()) {
            val existing = Jdbc.withConn { conn -> Jdbc.querySingle(conn, "SELECT * FROM processed_ids WHERE idempotency_key = ?", listOf(idempotencyKey)) }
            if (existing != null) return mapOf("success" to true, "message" to "Sudah diproses.", "transaction_id" to transactionId)
        }

        val txRow = Jdbc.withConn { conn -> Jdbc.querySingle(conn, "SELECT * FROM transactions WHERE id = ?", listOf(txId)) }
            ?: return mapOf("error" to "NOT_FOUND")
        val currentStatus = txRow["status"] as? String ?: ""
        if (currentStatus == "VOIDED") return mapOf("error" to "ALREADY_VOIDED")
        if (currentStatus == "FAILED") return mapOf("error" to "CANNOT_VOID")

        Jdbc.withTransaction { conn ->
            Jdbc.exec(conn, "UPDATE transactions SET status = 'VOIDED', voided_by = ?, void_reason = ?, voided_at = ?, updated_at = ? WHERE id = ?",
                listOf(adminUuid, reason, nowL, nowL, txId))
            if (idempotencyKey.isNotBlank()) {
                Jdbc.exec(conn, "INSERT INTO processed_ids (id, idempotency_key, result_type, result_id, created_at) VALUES (?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), idempotencyKey, "void_transaction", transactionId, nowL))
            }
        }
        logAudit(adminId, "transaction:void", "transaction", transactionId, "Voided: $reason")
        return mapOf("id" to transactionId, "status" to "VOIDED", "void_reason" to reason, "voided_at" to nowL.toString())
    }

    fun listCustomerTransactions(customerId: String, page: Int = 1, pageSize: Int = 20): Map<String, Any?> {
        val custId = try { UUID.fromString(customerId) } catch (_: Exception) { return mapOf("data" to emptyList<Any>()) }
        val offs = ((page - 1) * pageSize)
        val totalCount = Jdbc.withConn { conn -> Jdbc.count(conn, "SELECT COUNT(*) FROM transactions WHERE customer_id = ?", listOf(custId)) }
        val rows = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn, "SELECT id, bank_sampah_id, transaction_type, status, total_weight, total_value, created_at FROM transactions WHERE customer_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                listOf(custId, pageSize, offs))
        }
        return mapOf("data" to rows.map { txRowToBrief(it) },
            "pagination" to mapOf("page" to page, "page_size" to pageSize, "total_count" to totalCount))
    }

    private fun txRowToBrief(r: Map<String, Any?>) = mapOf(
        "id" to r["id"].toString(),
        "bank_sampah_id" to (r["bank_sampah_id"] as? UUID)?.toString(),
        "transaction_type" to r["transaction_type"],
        "status" to r["status"],
        "total_weight" to ((r["total_weight"] as? Number)?.toLong() ?: 0L),
        "total_value" to ((r["total_value"] as? Number)?.toLong() ?: 0L),
        "created_at" to r["created_at"]?.toString()
    )

    private fun logAudit(actorId: String, action: String, resourceType: String?, resourceId: String?, details: String?) {
        try {
            Jdbc.withConn { conn ->
                Jdbc.exec(conn, "INSERT INTO audit_logs (id, actor_id, action, resource_type, resource_id, details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), try { UUID.fromString(actorId) } catch (_: Exception) { null }, action, resourceType, resourceId, details, nowLdt()))
            }
        } catch (e: Exception) {
            log.error("Audit log write failed: {}", e.message, e)
        }
    }

    /**
     * Calculate item value from weight and price per unit.
     * For weights >= 1000g (1kg): (weight / 1000) * pricePerUnit
     * For weights < 1000g: proportional calculation with minimum value of 1.
     */
    internal fun calculateItemValue(weight: Long, pricePerUnit: Long): Long {
        return if (weight >= 1000) (weight / 1000) * pricePerUnit else {
            val prop = (weight * pricePerUnit) / 1000
            if (prop > 0) prop else if (weight > 0) 1 else 0
        }
    }

    private fun nowLdt(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    /**
     * Build canonical HMAC payload for signature verification.
     * The payload must match exactly what the device signed.
     */
    private fun buildHmacPayload(
        bankSampahId: String,
        operatorId: String,
        customerId: String,
        items: List<Map<String, Any?>>,
        deviceTimestamp: String,
        idempotencyKey: String,
        devicePubKey: String
    ): String {
        val itemsJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonArray.serializer(),
            kotlinx.serialization.json.JsonArray(items.map { item ->
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "category_id" to kotlinx.serialization.json.JsonPrimitive(item["category_id"] as? String ?: ""),
                        "weight" to kotlinx.serialization.json.JsonPrimitive((item["weight"] as? Number)?.toLong() ?: 0L)
                    )
                )
            })
        )

        // Canonical format: field1|field2|field3|... (pipe-separated, no spaces)
        return buildString {
            append(bankSampahId); append('|')
            append(operatorId); append('|')
            append(customerId); append('|')
            append(itemsJson); append('|')
            append(deviceTimestamp); append('|')
            append(idempotencyKey); append('|')
            append(devicePubKey)
        }
    }
}
