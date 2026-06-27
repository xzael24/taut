package com.taut.service

import com.taut.db.Jdbc
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * DeviceService — device registration and management.
 *
 * device_secret is returned only ONCE during initial registration (ceo-device-secret).
 */
object DeviceService {

    private val log = LoggerFactory.getLogger(DeviceService::class.java)
    private val clock = Clock.systemUTC()

    fun registerDevice(bankSampahId: String, deviceName: String?, devicePhoneNumber: String?, devicePubKey: String): Map<String, Any> {
        val now = Instant.now(clock)
        val nowL = nowLdt(now)
        val bankId = try { UUID.fromString(bankSampahId) } catch (_: Exception) {
            return mapOf("error" to "INVALID_ID", "message" to "Format bank_sampah_id tidak valid.")
        }
        val cleanedPubKey = devicePubKey.replace(Regex("\\s+"), "")
        if (cleanedPubKey.length < 32 || cleanedPubKey.length > 256) {
            return mapOf("error" to "INVALID_PUBKEY", "message" to "Format public key tidak valid.")
        }
        try { Base64.getDecoder().decode(cleanedPubKey) } catch (_: Exception) {
            return mapOf("error" to "INVALID_PUBKEY", "message" to "Public key tidak valid.")
        }

        val bankExists = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT id FROM waste_banks WHERE id = ? AND is_active = true", listOf(bankId))
        } ?: return mapOf("error" to "BANK_NOT_FOUND", "message" to "Bank Sampah tidak ditemukan.")

        // Check for existing device — never return device_secret again (ceo-device-secret)
        val existingDev = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT id, is_wiped FROM devices WHERE device_pub_key = ? AND is_active = true", listOf(cleanedPubKey))
        }
        if (existingDev != null) {
            val deviceId = (existingDev["id"] as UUID).toString()
            log.warn("Device re-registration attempt: device_id={}, bank_sampah_id={}", deviceId, bankSampahId)
            return mapOf(
                "device_id" to deviceId,
                "message" to "Device sudah terdaftar.",
                "device_fingerprint" to ""
            )
        }

        val deviceId = UUID.randomUUID()
        val fp = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(cleanedPubKey.toByteArray()))
        val secretBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val deviceSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)

        Jdbc.withConn { conn ->
            Jdbc.exec(conn, """
                INSERT INTO devices (id, bank_sampah_id, device_name, device_phone_number, device_pub_key, device_fingerprint,
                                     registered_at, last_seen_at, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(), listOf(deviceId, bankId, deviceName, devicePhoneNumber, cleanedPubKey, fp, nowL, nowL, true, nowL, nowL))
        }

        logAudit("system", "device:register", "device", deviceId.toString(), "Device registered for bank $bankSampahId")

        // Return device_secret ONLY on first registration (ceo-device-secret)
        return mapOf(
            "device_id" to deviceId.toString(),
            "device_secret" to deviceSecret,
            "device_fingerprint" to fp
        )
    }

    fun listDevices(bankSampahId: String): Map<String, Any> {
        val bankId = try { UUID.fromString(bankSampahId) } catch (_: Exception) {
            return mapOf("data" to emptyList<Any>(), "error" to "INVALID_ID")
        }
        val rows = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn,
                "SELECT * FROM devices WHERE bank_sampah_id = ? AND is_active = true ORDER BY registered_at DESC",
                listOf(bankId))
        }
        return mapOf("data" to rows.map { row ->
            mapOf(
                "id" to (row["id"] as UUID).toString(),
                "bank_sampah_id" to (row["bank_sampah_id"] as UUID).toString(),
                "device_name" to row["device_name"],
                "device_phone_number" to row["device_phone_number"],
                "device_pub_key" to row["device_pub_key"],
                "device_fingerprint" to row["device_fingerprint"],
                "registered_at" to row["registered_at"]?.toString(),
                "last_seen_at" to row["last_seen_at"]?.toString(),
                "is_active" to row["is_active"],
                "app_version" to row["app_version"],
                "is_wiped" to row["is_wiped"]
                // device_secret is intentionally NOT returned in any list endpoint (ceo-device-secret)
            )
        })
    }

    fun initiateRemoteWipe(deviceId: String): Map<String, Any> {
        val devId = try { UUID.fromString(deviceId) } catch (_: Exception) {
            return mapOf("error" to "INVALID_ID", "message" to "Format device ID tidak valid.")
        }
        val nowL = nowLdt(Instant.now(clock))

        val device = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM devices WHERE id = ?", listOf(devId))
        } ?: return mapOf("error" to "DEVICE_NOT_FOUND", "message" to "Device tidak ditemukan.")

        if (device["is_active"] == false) return mapOf("error" to "INACTIVE_DEVICE", "message" to "Device sudah tidak aktif.")
        if (device["is_wiped"] == true) return mapOf("error" to "ALREADY_WIPED", "message" to "Device sudah pernah di-wipe.")

        Jdbc.withConn { conn ->
            Jdbc.exec(conn, "UPDATE devices SET is_wiped = true, wiped_at = ?, updated_at = ? WHERE id = ?",
                listOf(nowL, nowL, devId))
        }

        logAudit("system", "device:wipe", "device", deviceId, "Remote wipe initiated")
        return mapOf("success" to true, "message" to "Remote wipe initiated for device $deviceId", "wiped_at" to nowL.toString())
    }

    private fun logAudit(actorId: String, action: String, resourceType: String, resourceId: String, details: String) {
        try {
            Jdbc.withConn { conn ->
                Jdbc.exec(conn, "INSERT INTO audit_logs (id, actor_id, action, resource_type, resource_id, details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), try { UUID.fromString(actorId) } catch (_: Exception) { null }, action, resourceType, resourceId, details, nowLdt(Instant.now(clock))))
            }
        } catch (e: Exception) {
            log.error("Audit log write failed: {}", e.message, e)
        }
    }

    private fun nowLdt(instant: Instant): LocalDateTime = instant.atOffset(ZoneOffset.UTC).toLocalDateTime()
}
