package com.taut.routes

import com.taut.db.Jdbc
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.util.UUID

private val complianceLog = LoggerFactory.getLogger("com.taut.routes.ComplianceRoutes")

/**
 * Compliance routes (UU PDP) — data export, right to be forgotten, consent management.
 * Spec: docs/api-spec.md §4.6 (REST Compliance).
 *
 * All endpoints require JWT authentication (configured in Routing.kt authenticate block).
 */
fun Route.complianceRoutes() {

    // GET /v1/users/{id}/export — Export all PII for a user (Pasal 26)
    get("/users/{id}/export") {
        val userId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "User ID diperlukan."))
            return@get
        }
        val principal = call.principal<JWTPrincipal>()
        val actorId = principal?.getClaim("sub", String::class) ?: "unknown"
        val uid = try { UUID.fromString(userId) } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_ID", "message" to "User ID tidak valid."))
            return@get
        }

        // Query user data
        val userRow = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT id, phone_number, role, name, kyc_status, created_at, updated_at FROM users WHERE id = ?", listOf(uid))
        }
        if (userRow == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "NOT_FOUND", "message" to "User tidak ditemukan."))
            return@get
        }

        // Query consent log for this user
        val consents = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn, """
                SELECT consent_type, consent_version, granted, created_at 
                FROM user_consent_log 
                WHERE user_id = ?
                ORDER BY created_at DESC
            """.trimIndent(), listOf(uid))
        }

        // Log audit
        Jdbc.withConn { conn ->
            Jdbc.exec(conn, """
                INSERT INTO audit_logs (id, actor_id, action, resource_type, resource_id, details, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(), listOf(
                UUID.randomUUID(), try { UUID.fromString(actorId) } catch (_: Exception) { null },
                "user_export", "user", userId.toString(), "Data export requested"
            ))
        }

        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "user_id" to userId.toString(),
                "phone_number" to (userRow["phone_number"] ?: ""),
                "role" to (userRow["role"] ?: ""),
                "name" to (userRow["name"] ?: ""),
                "kyc_status" to (userRow["kyc_status"] ?: ""),
                "created_at" to (userRow["created_at"]?.toString() ?: ""),
                "updated_at" to (userRow["updated_at"]?.toString() ?: ""),
                "consents" to consents,
                "export_timestamp" to Jdbc.nowLdt().toString(),
                "request_id" to UUID.randomUUID().toString()
            )
        )
    }

    // POST /v1/users/{id}/forget — Anonymize user data (right to be forgotten)
    post("/users/{id}/forget") {
        val userId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "User ID diperlukan."))
            return@post
        }
        val principal = call.principal<JWTPrincipal>()
        val actorId = principal?.getClaim("sub", String::class) ?: "unknown"
        val uid = try { UUID.fromString(userId) } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_ID", "message" to "User ID tidak valid."))
            return@post
        }

        Jdbc.withTransaction { conn ->
            // Delete consent log entries
            Jdbc.exec(conn, "DELETE FROM user_consent_log WHERE user_id = ?", listOf(uid))

            // Anonymize user data in users table (keep id but clear sensitive data)
            Jdbc.exec(conn, """
                UPDATE users 
                SET name = NULL, phone_number = CONCAT('anonymized_', id::text), 
                    role = NULL, village_id = NULL, 
                    kyc_status = 'anonymized', pin_hash = NULL, pin_salt = NULL, 
                    failed_pin_attempts = 0, is_active = false, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """.trimIndent(), listOf(uid))

            // Log audit trail (before deleting audit logs)
            Jdbc.exec(conn, """
                INSERT INTO audit_logs (id, actor_id, action, resource_type, resource_id, details, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(), listOf(
                UUID.randomUUID(), try { UUID.fromString(actorId) } catch (_: Exception) { null },
                "user_forget", "user", userId.toString(), "User data anonymized per right to be forgotten"
            ))
        }

        complianceLog.info("User {} anonymized by {}", userId, actorId)
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "user_id" to userId.toString(),
                "anonymized_at" to Jdbc.nowLdt().toString(),
                "request_id" to UUID.randomUUID().toString()
            )
        )
    }

    // GET /v1/consent — Get current consent status for authenticated user
    get("/consent") {
        val principal = call.principal<JWTPrincipal>()
        val actorId = principal?.getClaim("sub", String::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "UNAUTHORIZED", "message" to "Token tidak valid."))
            return@get
        }
        val uid = try { UUID.fromString(actorId) } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_USER", "message" to "User ID tidak valid."))
            return@get
        }

        val consents = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn, """
                SELECT consent_type, consent_version, granted, ip_address, created_at 
                FROM user_consent_log 
                WHERE user_id = ?
                ORDER BY created_at DESC
            """.trimIndent(), listOf(uid))
        }

        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "user_id" to actorId,
                "consents" to consents,
                "request_id" to UUID.randomUUID().toString()
            )
        )
    }

    // POST /v1/consent — Update consent preference
    post("/consent") {
        val principal = call.principal<JWTPrincipal>()
        val actorId = principal?.getClaim("sub", String::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "UNAUTHORIZED", "message" to "Token tidak valid."))
            return@post
        }
        val uid = try { UUID.fromString(actorId) } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_USER", "message" to "User ID tidak valid."))
            return@post
        }

        val body = try {
            call.receive<Map<String, String>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid."))
            return@post
        }

        val consentType = body["consent_type"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_FIELD", "message" to "Field consent_type wajib diisi."))
            return@post
        }
        val consentVersion = body["consent_version"] ?: "1"
        val granted = body["granted"]?.let {
            when (it) {
                is String -> it.lowercase() == "true" || it == "1"
                is Boolean -> it
                else -> true
            }
        } ?: true
        val ipAddress = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.host()

        Jdbc.withTransaction { conn ->
            Jdbc.exec(conn, """
                INSERT INTO user_consent_log (id, user_id, consent_type, consent_version, granted, ip_address, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(), listOf(
                UUID.randomUUID(),
                uid,
                consentType,
                consentVersion,
                granted,
                ipAddress
            ))

            // Log audit
            Jdbc.exec(conn, """
                INSERT INTO audit_logs (id, actor_id, action, resource_type, resource_id, details, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(), listOf(
                UUID.randomUUID(), uid,
                "user_export", "consent", consentType,
                "Consent recorded: $consentType version=$consentVersion granted=$granted"
            ))
        }

        complianceLog.info("Consent recorded for user {}: {} version={} granted={}", actorId, consentType, consentVersion, granted)
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "user_id" to actorId,
                "consent_type" to consentType,
                "consent_version" to consentVersion,
                "granted" to granted,
                "updated_at" to Jdbc.nowLdt().toString(),
                "request_id" to UUID.randomUUID().toString()
            )
        )
    }
}
