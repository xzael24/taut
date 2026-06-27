package com.taut.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.db.Jdbc
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * AuthService — handles OTP generation/verification, JWT token management, PIN hashing.
 * Uses raw JDBC SQL via Jdbc helper for database operations.
 */
object AuthService {

    private val log = LoggerFactory.getLogger(AuthService::class.java)
    private val clock = Clock.systemUTC()
    private const val OTP_TTL_SECONDS = 300L
    private const val MAX_FAILED_PIN_ATTEMPTS = 10
    private var bcryptCost = 12  // Configurable via AppConfig (ceo-bcrypt-cost)
    private val secureRandom = SecureRandom()

    // ── Rate limiting constants (OTP endpoints) ──
    private const val OTP_REQUEST_MAX = 3            // Max OTP requests per phone per window
    private const val OTP_REQUEST_WINDOW = 60L        // 60-second window
    private const val OTP_DEVICE_MAX = 5              // Max OTP requests per device per window
    private const val OTP_DEVICE_WINDOW = 600L         // 10-minute window
    private const val OTP_VERIFY_MAX = 5              // Max OTP verify attempts per phone per window
    private const val OTP_VERIFY_WINDOW = 300L         // 5-minute window

    // JWT configuration — initialized at startup from AppConfig
    private lateinit var jwtSecret: String
    private lateinit var jwtIssuer: String
    private lateinit var jwtAudience: String
    private var accessTokenExpiry: Long = 900L
    private var refreshTokenExpiry: Long = 2_592_000L

    fun init(config: com.taut.config.JwtConfig) {
        jwtSecret = config.secret
        jwtIssuer = config.issuer
        jwtAudience = config.audience
        accessTokenExpiry = config.accessTokenExpirySeconds
        refreshTokenExpiry = config.refreshTokenExpirySeconds
        if (config.bcryptCost > 0) {
            bcryptCost = config.bcryptCost
        }
    }

    fun requestOtp(phoneNumber: String, deviceId: String, bankSampahId: String? = null): Map<String, Any> {
        val normalized = normalizePhone(phoneNumber)
            ?: return mapOf("error" to "INVALID_PHONE", "message" to "Format nomor telepon tidak valid.")

        val now = Instant.now(clock)

        // Phone-based rate limit: max OTP_REQUEST_MAX per OTP_REQUEST_WINDOW seconds
        val phoneRate = checkPhoneRateLimit(normalized, OTP_REQUEST_MAX, OTP_REQUEST_WINDOW, now)
        if (!phoneRate.allowed) {
            return mapOf(
                "error" to "RATE_LIMITED",
                "message" to "Terlalu banyak permintaan. Coba lagi dalam ${phoneRate.retryAfter} detik.",
                "retry_after" to phoneRate.retryAfter
            )
        }

        // Device-based rate limit: max OTP_DEVICE_MAX per OTP_DEVICE_WINDOW seconds
        if (deviceId.isNotBlank()) {
            val deviceRate = checkDeviceRateLimit(deviceId, OTP_DEVICE_MAX, OTP_DEVICE_WINDOW, now)
            if (!deviceRate.allowed) {
                return mapOf(
                    "error" to "RATE_LIMITED",
                    "message" to "Terlalu banyak permintaan dari perangkat ini. Coba lagi dalam ${deviceRate.retryAfter} detik.",
                    "retry_after" to deviceRate.retryAfter
                )
            }
        }

        val otpCode = String.format("%06d", secureRandom.nextInt(1_000_000))
        val otpHash = BCrypt.withDefaults().hashToString(bcryptCost, otpCode.toCharArray())
        val sessionId = UUID.randomUUID()
        val expiresAt = LocalDateTime.ofInstant(now.plusSeconds(OTP_TTL_SECONDS), ZoneOffset.UTC)

        val bankId = bankSampahId?.let {
            try { UUID.fromString(it) } catch (_: Exception) { null }
        }

        Jdbc.withTransaction { conn ->
            Jdbc.exec(conn,
                "INSERT INTO otp_sessions (id, phone_number, otp_hash, device_id, bank_sampah_id, expires_at, verified, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                listOf(sessionId, normalized, otpHash, deviceId, bankId, expiresAt, false, nowLdt(now))
            )
        }

        try {
            SmsService.queueSms(normalized, "Kode OTP TAUT Anda: $otpCode. Berlaku 5 menit. Jangan bagikan kode ini kepada siapa pun.")
        } catch (e: Exception) {
            log.error("Failed to queue OTP SMS: {}", e.message, e)
        }

        return mapOf(
            "session_id" to sessionId.toString(),
            "expires_seconds" to OTP_TTL_SECONDS.toInt(),
            "masked_phone" to maskPhone(normalized)
        )
    }

    fun verifyOtp(sessionId: String, otpCode: String, deviceId: String, devicePubKey: String): Map<String, Any> {
        val now = Instant.now(clock)
        val nowL = nowLdt(now)
        val sid = try { UUID.fromString(sessionId) } catch (_: Exception) { null }
            ?: return mapOf("error" to "INVALID_SESSION", "message" to "Session ID tidak valid.")

        val session = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM otp_sessions WHERE id = ?", listOf(sid))
        } ?: return mapOf("error" to "SESSION_NOT_FOUND", "message" to "Session tidak ditemukan.")

        val phoneNumber = session["phone_number"] as? String ?: ""

        // Verify rate limit: max OTP_VERIFY_MAX per OTP_VERIFY_WINDOW per phone
        // Uses "v:" prefix on phone number to create a distinct bucket from request rate limits
        val verifyRate = checkPhoneRateLimit("v:$phoneNumber", OTP_VERIFY_MAX, OTP_VERIFY_WINDOW, now)
        if (!verifyRate.allowed) {
            return mapOf(
                "error" to "RATE_LIMITED",
                "message" to "Terlalu banyak percobaan verifikasi. Coba lagi dalam ${verifyRate.retryAfter} detik.",
                "retry_after" to verifyRate.retryAfter
            )
        }

        val expiresAt = session["expires_at"] as? LocalDateTime ?: return mapOf("error" to "SESSION_INVALID")
        if (now.isAfter(expiresAt.toInstant(ZoneOffset.UTC))) {
            return mapOf("error" to "OTP_EXPIRED", "message" to "Kode OTP sudah kedaluwarsa.")
        }
        if (session["verified"] == true) {
            return mapOf("error" to "OTP_ALREADY_USED", "message" to "Kode OTP sudah pernah digunakan.")
        }

        val storedHash = session["otp_hash"] as? String ?: ""
        if (!BCrypt.verifyer().verify(otpCode.toCharArray(), storedHash).verified) {
            return mapOf("error" to "INVALID_OTP", "message" to "Kode OTP salah.")
        }

        Jdbc.withConn { conn ->
            Jdbc.exec(conn, "UPDATE otp_sessions SET verified = true WHERE id = ?", listOf(sid))
        }

        // Lookup or create user
        var user = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM users WHERE phone_number = ?", listOf(phoneNumber))
        }
        val isNewUser: Boolean

        if (user != null) {
            isNewUser = false
            val uid = user["id"] as UUID
            Jdbc.withConn { conn ->
                Jdbc.exec(conn, "UPDATE users SET updated_at = ? WHERE id = ?", listOf(nowL, uid))
            }
        } else {
            isNewUser = true
            val newId = UUID.randomUUID()
            Jdbc.withConn { conn ->
                Jdbc.exec(conn,
                    "INSERT INTO users (id, phone_number, role, kyc_status, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf(newId, phoneNumber, "CUSTOMER", "unverified", true, nowL, nowL)
                )
            }
            user = Jdbc.withConn { conn ->
                Jdbc.querySingle(conn, "SELECT * FROM users WHERE id = ?", listOf(newId))
            }
        }

        val userId = user!!["id"] as UUID
        val userRole = user["role"] as? String ?: "CUSTOMER"

        // Generate tokens with jti claim (ceo-jwt-claims)
        val accessToken = generateAccessToken(userId.toString(), userRole, phoneNumber)
        val (refreshTokenStr, refreshTokenHash) = generateRefreshToken()
        val refreshExpiresAt = nowLdt(now.plusSeconds(refreshTokenExpiry))

        Jdbc.withConn { conn ->
            Jdbc.exec(conn,
                "INSERT INTO refresh_tokens (id, user_id, token_hash, device_id, expires_at, is_revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                listOf(UUID.randomUUID(), userId, refreshTokenHash, deviceId, refreshExpiresAt, false, nowL)
            )
        }

        logAudit(userId.toString(), "auth:verify_otp", "user", userId.toString(), "Phone verified and logged in")

        return mapOf(
            "access_token" to accessToken,
            "refresh_token" to refreshTokenStr,
            "access_expires_in" to accessTokenExpiry.toInt(),
            "refresh_expires_in" to refreshTokenExpiry.toInt(),
            "user" to mapOf(
                "id" to userId.toString(),
                "phone_number" to phoneNumber,
                "role" to userRole,
                "name" to (user["name"] ?: ""),
                "kyc_status" to (user["kyc_status"] ?: "unverified"),
                "is_active" to (user["is_active"] ?: true)
            ),
            "is_new_user" to isNewUser
        )
    }

    fun refreshToken(refreshToken: String, deviceId: String, deviceSignature: String): Map<String, Any> {
        val now = Instant.now(clock)
        val tokenHash = hashToken(refreshToken)

        val stored = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM refresh_tokens WHERE token_hash = ? AND is_revoked = false", listOf(tokenHash))
        } ?: return mapOf("error" to "INVALID_TOKEN", "message" to "Refresh token tidak valid.")

        val expiresAt = stored["expires_at"] as? LocalDateTime
        if (expiresAt != null && now.isAfter(expiresAt.toInstant(ZoneOffset.UTC))) {
            return mapOf("error" to "TOKEN_EXPIRED", "message" to "Refresh token sudah kedaluwarsa.")
        }

        val userId = stored["user_id"] as UUID
        val user = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM users WHERE id = ?", listOf(userId))
        } ?: return mapOf("error" to "USER_NOT_FOUND", "message" to "User tidak ditemukan.")

        val userRole = user["role"] as? String ?: "CUSTOMER"
        val phoneNumber = user["phone_number"] as? String ?: ""

        val newAccessToken = generateAccessToken(userId.toString(), userRole, phoneNumber)

        var newRefreshTokenStr = ""
        if (deviceSignature.isNotEmpty()) {
            val storedId = stored["id"] as UUID
            Jdbc.withConn { conn ->
                Jdbc.exec(conn, "UPDATE refresh_tokens SET is_revoked = true WHERE id = ?", listOf(storedId))
            }
            val (rt, rh) = generateRefreshToken()
            newRefreshTokenStr = rt
            val newExp = nowLdt(now.plusSeconds(refreshTokenExpiry))
            Jdbc.withConn { conn ->
                Jdbc.exec(conn,
                    "INSERT INTO refresh_tokens (id, user_id, token_hash, device_id, expires_at, is_revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), userId, rh, deviceId, newExp, false, nowLdt(now)))
            }
        }

        logAudit(userId.toString(), "auth:refresh_token", "user", userId.toString(), "Token refreshed")
        return mapOf("access_token" to newAccessToken, "refresh_token" to newRefreshTokenStr, "expires_in" to accessTokenExpiry.toInt())
    }

    fun changePin(userId: String, oldPin: String, newPin: String, idempotencyKey: String): Map<String, Any> {
        val now = Instant.now(clock)
        val nowL = nowLdt(now)
        val uid = try { UUID.fromString(userId) } catch (_: Exception) { null }
            ?: return mapOf("error" to "INVALID_USER", "message" to "User ID tidak valid.")

        if (idempotencyKey.isNotBlank()) {
            val existing = Jdbc.withConn { conn ->
                Jdbc.querySingle(conn, "SELECT * FROM processed_ids WHERE idempotency_key = ?", listOf(idempotencyKey))
            }
            if (existing != null) return mapOf("success" to true, "message" to "Permintaan sudah diproses.")
        }

        val user = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM users WHERE id = ?", listOf(uid))
        } ?: return mapOf("error" to "USER_NOT_FOUND", "message" to "User tidak ditemukan.")

        val storedHash = user["pin_hash"] as? String
        if (storedHash != null && !BCrypt.verifyer().verify(oldPin.toCharArray(), storedHash).verified) {
            return mapOf("error" to "INVALID_PIN", "message" to "PIN lama salah.")
        }

        val newHash = BCrypt.withDefaults().hashToString(bcryptCost, newPin.toCharArray())
        Jdbc.withTransaction { conn ->
            Jdbc.exec(conn, "UPDATE users SET pin_hash = ?, pin_salt = ?, failed_pin_attempts = 0, updated_at = ? WHERE id = ?",
                listOf(newHash, generatePinSalt(), nowL, uid))
            if (idempotencyKey.isNotBlank()) {
                Jdbc.exec(conn, "INSERT INTO processed_ids (id, idempotency_key, result_type, result_id, created_at) VALUES (?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), idempotencyKey, "pin_change", userId, nowL))
            }
        }

        logAudit(userId, "auth:change_pin", "user", userId, "PIN changed")
        return mapOf("success" to true)
    }

    fun verifyPin(userId: String, pin: String): Map<String, Any> {
        val uid = try { UUID.fromString(userId) } catch (_: Exception) { null }
            ?: return mapOf("error" to "INVALID_USER", "message" to "User ID tidak valid.")

        val user = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT * FROM users WHERE id = ?", listOf(uid))
        } ?: return mapOf("error" to "USER_NOT_FOUND", "message" to "User tidak ditemukan.")

        val failedAttempts = (user["failed_pin_attempts"] as? Number)?.toInt() ?: 0
        if (failedAttempts >= MAX_FAILED_PIN_ATTEMPTS) {
            return mapOf("valid" to false, "remaining_attempts" to 0, "error" to "PIN_LOCKED", "message" to "Akun terkunci.")
        }

        val storedHash = user["pin_hash"] as? String
        if (storedHash == null) {
            return mapOf("valid" to false, "remaining_attempts" to 0, "error" to "PIN_NOT_SET", "message" to "PIN belum diatur.")
        }

        val verified = BCrypt.verifyer().verify(pin.toCharArray(), storedHash).verified
        if (verified) {
            Jdbc.withConn { conn ->
                Jdbc.exec(conn, "UPDATE users SET failed_pin_attempts = 0, updated_at = ? WHERE id = ?",
                    listOf(nowLdt(Instant.now(clock)), uid))
            }
            logAudit(userId, "auth:verify_pin", "user", userId, "PIN verified")
            return mapOf("valid" to true, "remaining_attempts" to MAX_FAILED_PIN_ATTEMPTS)
        } else {
            val newFailed = failedAttempts + 1
            Jdbc.withConn { conn ->
                Jdbc.exec(conn, "UPDATE users SET failed_pin_attempts = ?, updated_at = ? WHERE id = ?",
                    listOf(newFailed, nowLdt(Instant.now(clock)), uid))
            }
            return mapOf("valid" to false, "remaining_attempts" to maxOf(0, MAX_FAILED_PIN_ATTEMPTS - newFailed), "error" to "INVALID_PIN", "message" to "PIN salah.")
        }
    }

    // ── Internal helpers ──

    /**
     * Generate JWT access token with jti, iat, nbf claims (ceo-jwt-claims).
     */
    internal fun generateAccessToken(userId: String, role: String, phoneNumber: String): String {
        val now = Instant.now(clock)
        val jti = UUID.randomUUID().toString()
        val algorithm = Algorithm.HMAC256(jwtSecret)
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(userId)
            .withClaim("role", role)
            .withClaim("phone", phoneNumber)
            .withClaim("jti", jti)
            .withIssuedAt(Date.from(now))
            .withNotBefore(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(accessTokenExpiry)))
            .sign(algorithm)
    }

    private fun generateRefreshToken(): Pair<String, String> {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = hashToken(token)
        return Pair(token, hash)
    }

    private fun hashToken(token: String): String = BCrypt.withDefaults().hashToString(bcryptCost, token.toCharArray())

    private fun normalizePhone(phone: String): String? {
        val cleaned = phone.replace(Regex("\\s+"), "").replace("-", "")
        return when {
            cleaned.matches(Regex("^08\\d{8,11}$")) -> cleaned
            cleaned.matches(Regex("^628\\d{8,11}$")) -> "0${cleaned.substring(2)}"
            cleaned.matches(Regex("^\\+628\\d{8,11}$")) -> "0${cleaned.substring(3)}"
            else -> null
        }
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 6) return "${phone.first()}****${phone.last()}"
        return "${phone.substring(0, 4)}****${phone.substring(phone.length - 3)}"
    }

    // ── Rate limiting helpers ──

    /**
     * Result of a rate limit check.
     * @param allowed Whether the request is within the rate limit.
     * @param retryAfter Seconds until the client can retry (0 if allowed).
     */
    data class RateLimitResult(val allowed: Boolean, val retryAfter: Long = 0)

    /**
     * Phone-based rate limit check using PostgreSQL advisory locks + atomic read-then-write.
     *
     * The `phoneKey` parameter serves as both the primary key in the `rate_limits` table
     * and the argument to `pg_advisory_xact_lock(hashtext(...))`, which serializes all
     * concurrent rate-limit operations for the same key in transaction-level locks.
     *
     * Flow inside a single transaction:
     *  1. Acquire advisory lock on the phone key (blocks concurrent requests for the same number).
     *  2. Read existing row from rate_limits.
     *  3. If no row exists → INSERT with count=1.
     *  4. If the window has expired → UPDATE resetting count to 1 and advancing the window.
     *  5. If within window and under limit → UPDATE incrementing count.
     *  6. If within window and at/over limit → return [RateLimitResult(allowed=false)] with retry-after.
     *  7. Commit (advisory lock auto-released).
     *
     * Expired rows are deleted after the UPSERT to keep the table lean.
     */
    private fun checkPhoneRateLimit(phoneKey: String, maxRequests: Int, windowSeconds: Long, now: Instant): RateLimitResult {
        return Jdbc.withTransaction { conn ->
            // 1) Acquire transaction-level advisory lock — serializes per phone key
            Jdbc.querySingle(conn, "SELECT pg_advisory_xact_lock(hashtext(?)::bigint)", listOf(phoneKey))

            val nowL = nowLdt(now)
            val expiresL = nowL.plusSeconds(windowSeconds)

            // 2) Read current row
            val row = Jdbc.querySingle(conn,
                "SELECT request_count, expires_at FROM rate_limits WHERE phone_number = ?",
                listOf(phoneKey)
            )

            val (count, expiresAtLdt) = if (row == null) {
                // 3) No existing row — insert first request
                Jdbc.exec(conn,
                    """INSERT INTO rate_limits (phone_number, request_count, window_start, expires_at, updated_at)
                       VALUES (?, 1, ?, ?, ?)""",
                    listOf(phoneKey, nowL, expiresL, nowL)
                )
                Pair(1, expiresL)
            } else {
                val existingCount = (row["request_count"] as? Number)?.toInt() ?: 0
                val existingExpires = (row["expires_at"] as? LocalDateTime) ?: expiresL
                val existingExpiresInstant = existingExpires.toInstant(ZoneOffset.UTC)

                if (now.isAfter(existingExpiresInstant)) {
                    // 4) Window has expired — reset
                    Jdbc.exec(conn,
                        """UPDATE rate_limits
                           SET request_count = 1, window_start = ?, expires_at = ?, updated_at = ?
                           WHERE phone_number = ?""",
                        listOf(nowL, expiresL, nowL, phoneKey)
                    )
                    Pair(1, expiresL)
                } else if (existingCount >= maxRequests) {
                    // 6) Over limit — return early (no write)
                    val retryAfterExisting = existingExpiresInstant.epochSecond - now.epochSecond + 1
                    return@withTransaction RateLimitResult(allowed = false, retryAfter = retryAfterExisting)
                } else {
                    // 5) Within window, under limit — increment
                    val newCount = existingCount + 1
                    Jdbc.exec(conn,
                        "UPDATE rate_limits SET request_count = ?, updated_at = ? WHERE phone_number = ?",
                        listOf(newCount, nowL, phoneKey)
                    )
                    Pair(newCount, existingExpires)
                }
            }

            // Remove stale rows — anything with expires_at older than 2× the current window
            val staleCutoff = nowL.minusSeconds(windowSeconds * 2)
            Jdbc.exec(conn, "DELETE FROM rate_limits WHERE updated_at < ?", listOf(staleCutoff))

            RateLimitResult(allowed = true)
        }
    }

    /**
     * Device-based rate limit — identical pattern but on the `device_rate_limits` table,
     * keyed by device ID. Uses pg_advisory_xact_lock(hashtext(device_id)::bigint) for
     * per-device serialization.
     */
    private fun checkDeviceRateLimit(deviceId: String, maxRequests: Int, windowSeconds: Long, now: Instant): RateLimitResult {
        return Jdbc.withTransaction { conn ->
            Jdbc.querySingle(conn, "SELECT pg_advisory_xact_lock(hashtext(?)::bigint)", listOf(deviceId))

            val nowL = nowLdt(now)
            val expiresL = nowL.plusSeconds(windowSeconds)

            val row = Jdbc.querySingle(conn,
                "SELECT request_count, expires_at FROM device_rate_limits WHERE device_id = ?",
                listOf(deviceId)
            )

            val (count, expiresAtLdt) = if (row == null) {
                Jdbc.exec(conn,
                    """INSERT INTO device_rate_limits (device_id, request_count, window_start, expires_at, updated_at)
                       VALUES (?, 1, ?, ?, ?)""",
                    listOf(deviceId, nowL, expiresL, nowL)
                )
                Pair(1, expiresL)
            } else {
                val existingCount = (row["request_count"] as? Number)?.toInt() ?: 0
                val existingExpires = (row["expires_at"] as? LocalDateTime) ?: expiresL
                val existingExpiresInstant = existingExpires.toInstant(ZoneOffset.UTC)

                if (now.isAfter(existingExpiresInstant)) {
                    Jdbc.exec(conn,
                        """UPDATE device_rate_limits
                           SET request_count = 1, window_start = ?, expires_at = ?, updated_at = ?
                           WHERE device_id = ?""",
                        listOf(nowL, expiresL, nowL, deviceId)
                    )
                    Pair(1, expiresL)
                } else if (existingCount >= maxRequests) {
                    val retryAfterExisting = existingExpiresInstant.epochSecond - now.epochSecond + 1
                    return@withTransaction RateLimitResult(allowed = false, retryAfter = retryAfterExisting)
                } else {
                    val newCount = existingCount + 1
                    Jdbc.exec(conn,
                        "UPDATE device_rate_limits SET request_count = ?, updated_at = ? WHERE device_id = ?",
                        listOf(newCount, nowL, deviceId)
                    )
                    Pair(newCount, existingExpires)
                }
            }

            val staleCutoff = nowL.minusSeconds(windowSeconds * 2)
            Jdbc.exec(conn, "DELETE FROM device_rate_limits WHERE updated_at < ?", listOf(staleCutoff))

            RateLimitResult(allowed = true)
        }
    }

    private fun logAudit(actorId: String, action: String, resourceType: String?, resourceId: String?, details: String?) {
        try {
            Jdbc.withConn { conn ->
                Jdbc.exec(conn,
                    "INSERT INTO audit_logs (id, actor_id, action, resource_type, resource_id, details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID(), try { UUID.fromString(actorId) } catch (_: Exception) { null }, action, resourceType, resourceId, details, nowLdt(Instant.now(clock))))
            }
        } catch (e: Exception) {
            log.error("Audit log write failed: {}", e.message, e)
        }
    }

    private fun nowLdt(instant: Instant = Instant.now(clock)): LocalDateTime = instant.atOffset(ZoneOffset.UTC).toLocalDateTime()

    /**
     * Generate a cryptographically random salt for PIN storage.
     * BCrypt embeds the salt in the hash output, but pin_salt column 
     * exists in the schema — populate it with a 16-byte hex salt.
     */
    private fun generatePinSalt(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * IP-based rate limit check for PIN verification.
     * Uses the rate_limits table with key prefix "pin_ip:" to create a distinct bucket.
     * @return RateLimitResult — allowed=false if exceeded with retryAfter seconds.
     */
    internal fun checkPinVerifyRateLimit(ipAddress: String, maxRequests: Int = 10, windowSeconds: Long = 300, now: Instant = Instant.now(clock)): RateLimitResult {
        val ipKey = "pin_ip:$ipAddress"
        return checkPhoneRateLimit(ipKey, maxRequests, windowSeconds, now)
    }
}
