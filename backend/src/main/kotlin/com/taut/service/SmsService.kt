package com.taut.service

import com.taut.db.Jdbc
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * SmsService — SMS message queue and delivery.
 */
object SmsService {

    private val log = LoggerFactory.getLogger(SmsService::class.java)
    private val clock = Clock.systemUTC()
    private const val MAX_MSG_LENGTH = 160

    fun queueSms(phoneTo: String, message: String, transactionId: String? = null): String {
        val normalized = normalizePhone(phoneTo) ?: return ""
        val truncated = if (message.length > MAX_MSG_LENGTH) message.substring(0, MAX_MSG_LENGTH - 3) + "..."
        else message

        val now = nowLdt()
        val smsId = UUID.randomUUID()

        Jdbc.withConn { conn ->
            Jdbc.exec(conn, """
                INSERT INTO sms_queue (id, transaction_id, phone_to, message, sms_status, retry_count, max_retries, created_at)
                VALUES (?, ?, ?, ?, 'pending', 0, 3, ?)
            """.trimIndent(), listOf(smsId, transactionId?.let { try { UUID.fromString(it) } catch (_: Exception) { null } }, normalized, truncated, now))
        }

        try {
            attemptDelivery(smsId, normalized, truncated)
        } catch (e: Exception) {
            log.error("SMS delivery attempt failed for smsId={}: {}", smsId, e.message, e)
        }
        return smsId.toString()
    }

    fun sendOtpSms(phoneTo: String, otpCode: String): String =
        queueSms(phoneTo, "Kode OTP TAUT Anda: $otpCode. Berlaku 5 menit. Jangan bagikan kode ini kepada siapa pun.")

    fun sendTransactionReceipt(phoneTo: String, customerName: String?, totalValue: Long, bankName: String): String {
        val rupiah = totalValue / 100; val sen = totalValue % 100
        val formatted = "Rp${rupiah},${String.format("%02d", sen)}"
        val namePart = if (!customerName.isNullOrBlank()) " a/n $customerName" else ""
        return queueSms(phoneTo, "Transaksi$namePart di $bankName: $formatted telah tercatat. Terima kasih telah mendaur ulang!")
    }

    fun listQueue(status: String? = null, bankSampahId: String? = null, page: Int = 1, pageSize: Int = 20): Map<String, Any> {
        val offset = ((page - 1) * pageSize).toLong()
        val conditions = mutableListOf("1=1")
        val params = mutableListOf<Any?>()

        if (status != null) { conditions.add("sq.sms_status = ?"); params.add(status) }
        if (bankSampahId != null) {
            val bankId = try { UUID.fromString(bankSampahId) } catch (_: Exception) { return mapOf("data" to emptyList<Any>()) }
            conditions.add("t.bank_sampah_id = ?"); params.add(bankId)
        }

        val joinClause = if (bankSampahId != null) "JOIN transactions t ON sq.transaction_id = t.id" else ""
        val where = conditions.joinToString(" AND ")

        val totalCount = Jdbc.withConn { conn ->
            Jdbc.count(conn, "SELECT COUNT(*) FROM sms_queue sq $joinClause WHERE $where", params)
        }

        val sql = "SELECT sq.* FROM sms_queue sq $joinClause WHERE $where ORDER BY sq.created_at DESC LIMIT $pageSize OFFSET $offset"
        val rows = Jdbc.withConn { conn -> Jdbc.queryMap(conn, sql, params) }

        return mapOf(
            "data" to rows.map { rowToSms(it) },
            "pagination" to mapOf("page" to page, "page_size" to pageSize, "total_count" to totalCount, "has_more" to (offset + rows.size < totalCount))
        )
    }

    fun retrySms(smsId: String): Map<String, Any> {
        val sid = try { UUID.fromString(smsId) } catch (_: Exception) {
            return mapOf("error" to "INVALID_ID", "message" to "Format SMS ID tidak valid.")
        }
        return Jdbc.withConn { conn ->
            val sms = Jdbc.querySingle(conn, "SELECT * FROM sms_queue WHERE id = ?", listOf(sid))
                ?: return@withConn mapOf("error" to "NOT_FOUND", "message" to "SMS tidak ditemukan.")

            val currentStatus = sms["sms_status"] as? String ?: ""
            val retryCount = (sms["retry_count"] as? Number)?.toInt() ?: 0
            val maxRetries = (sms["max_retries"] as? Number)?.toInt() ?: 3

            if (currentStatus != "failed") return@withConn mapOf("error" to "WRONG_STATUS", "message" to "Hanya SMS failed yang bisa di-retry. Status: $currentStatus")
            if (retryCount >= maxRetries) {
                Jdbc.exec(conn, "UPDATE sms_queue SET sms_status = 'bounced', last_error = 'Max retries exceeded', retry_count = retry_count + 1 WHERE id = ?", listOf(sid))
                return@withConn mapOf("error" to "MAX_RETRIES", "message" to "Batas maksimal retry ($maxRetries) tercapai.")
            }

            Jdbc.exec(conn, "UPDATE sms_queue SET sms_status = 'pending', last_error = NULL, retry_count = retry_count + 1 WHERE id = ?", listOf(sid))
            mapOf("success" to true, "message" to "SMS akan dicoba kirim ulang.", "retry_count" to (retryCount + 1))
        }
    }

    private fun attemptDelivery(smsId: UUID, phoneTo: String, message: String) {
        // TODO: Integrate with actual Twilio SMS API.
        // Currently marks as 'pending' — a background worker (or Twilio callback)
        // will update the status to 'sent' or 'failed' once delivery is confirmed.
        Jdbc.withConn { conn ->
            Jdbc.exec(conn, "UPDATE sms_queue SET sms_status = 'pending', last_error = NULL WHERE id = ?",
                listOf(smsId))
        }
    }

    private fun normalizePhone(phone: String): String? {
        val cleaned = phone.replace(Regex("\\s+"), "").replace("-", "")
        return when {
            cleaned.matches(Regex("^08\\d{8,11}$")) -> cleaned
            cleaned.matches(Regex("^628\\d{8,11}$")) -> "0${cleaned.substring(2)}"
            cleaned.matches(Regex("^\\+628\\d{8,11}$")) -> "0${cleaned.substring(3)}"
            else -> null
        }
    }

    private fun rowToSms(r: Map<String, Any?>) = mapOf(
        "id" to (r["id"] as UUID).toString(),
        "transaction_id" to (r["transaction_id"] as? UUID)?.toString(),
        "phone_to" to (r["phone_to"] ?: ""),
        "message" to r["message"],
        "status" to (r["sms_status"] ?: "pending"),
        "provider" to r["provider"],
        "provider_message_id" to r["provider_message_id"],
        "cost" to r["cost"],
        "retry_count" to ((r["retry_count"] as? Number)?.toInt() ?: 0),
        "max_retries" to ((r["max_retries"] as? Number)?.toInt() ?: 3),
        "last_error" to r["last_error"],
        "sent_at" to r["sent_at"]?.toString(),
        "created_at" to r["created_at"]?.toString()
    )

    private fun nowLdt(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
}
