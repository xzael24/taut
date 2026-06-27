package com.taut.routes

import com.taut.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Auth routes — OTP request, OTP verification, token refresh, PIN change/verify.
 */
fun Route.authRoutes() {

    // POST /v1/auth/otp/request — Request SMS OTP for phone verification
    post("/otp/request") {
        val body = try {
            call.receive<Map<String, Any>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid.").toSerializableJsonObject())
            return@post
        }

        val phoneNumber = body["phone_number"] as? String ?: ""
        val deviceId = body["device_id"] as? String ?: ""
        val bankSampahId = body["bank_sampah_id"] as? String

        val result = AuthService.requestOtp(phoneNumber, deviceId, bankSampahId)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "RATE_LIMITED" -> HttpStatusCode.TooManyRequests
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        }
    }

    // POST /v1/auth/otp/verify — Verify OTP and return JWT tokens
    post("/otp/verify") {
        val body = try {
            call.receive<Map<String, String>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid."))
            return@post
        }

        val sessionId = body["session_id"] ?: ""
        val otpCode = body["otp_code"] ?: ""
        val deviceId = body["device_id"] ?: ""
        val devicePubKey = body["device_pub_key"] ?: ""

        val result = AuthService.verifyOtp(sessionId, otpCode, deviceId, devicePubKey)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "OTP_EXPIRED", "OTP_ALREADY_USED" -> HttpStatusCode.Gone
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        }
    }

    // POST /v1/auth/token/refresh — Refresh access token
    post("/token/refresh") {
        val body = try {
            call.receive<Map<String, Any>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid.").toSerializableJsonObject())
            return@post
        }

        val refreshToken = call.request.headers["X-Refresh-Token"] ?: (body["refresh_token"] as? String ?: "")
        val deviceId = (body["device_id"] as? String) ?: call.request.headers["X-Device-ID"] ?: ""
        val deviceSignature = (body["device_signature"] as? String) ?: call.request.headers["X-Device-Signature"] ?: ""

        val result = AuthService.refreshToken(refreshToken, deviceId, deviceSignature)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "TOKEN_EXPIRED" -> HttpStatusCode.Unauthorized
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        }
    }

    // POST /v1/auth/pin/change — Change operator PIN
    post("/pin/change") {
        val body = try {
            call.receive<Map<String, Any>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid.").toSerializableJsonObject())
            return@post
        }

        val userId = body["user_id"] as? String ?: ""
        val oldPin = body["old_pin"] as? String ?: ""
        val newPin = body["new_pin"] as? String ?: ""
        val idempotencyKey = (body["idempotency_key"] as? String) ?: call.request.headers["X-Idempotency-Key"] ?: ""

        val result = AuthService.changePin(userId, oldPin, newPin, idempotencyKey)
        if (result.containsKey("error")) {
            call.respond(HttpStatusCode.BadRequest, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        }
    }

    // POST /v1/auth/pin/verify — Verify PIN server-side (with IP-based rate limiting)
    post("/pin/verify") {
        val body = try {
            call.receive<Map<String, Any>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid.").toSerializableJsonObject())
            return@post
        }

        val userId = body["user_id"] as? String ?: ""
        val pin = body["pin"] as? String ?: ""

        // IP-based rate limiting (MAJOR-7 fix)
        val ipAddress = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.host()
        val rateResult = AuthService.checkPinVerifyRateLimit(ipAddress)
        if (!rateResult.allowed) {
            call.respond(HttpStatusCode.TooManyRequests, JsonObject(mapOf(
                "error" to JsonPrimitive("RATE_LIMITED"),
                "message" to JsonPrimitive("Terlalu banyak percobaan verifikasi PIN. Coba lagi dalam ${rateResult.retryAfter} detik."),
                "retry_after" to JsonPrimitive(rateResult.retryAfter.toString())
            )))
            return@post
        }

        val result = AuthService.verifyPin(userId, pin)
        if (result.containsKey("error") && result["error"] != "INVALID_PIN") {
            val status = when (result["error"]) {
                "PIN_LOCKED" -> HttpStatusCode.Forbidden
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        }
    }
}
