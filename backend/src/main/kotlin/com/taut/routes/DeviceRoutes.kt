package com.taut.routes

import com.taut.plugins.authorizeRole
import com.taut.service.DeviceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Device routes — device registration, listing, and remote wipe.
 */
fun Route.deviceRoutes() {

    // POST /v1/devices/register — Register a new device
    post("/devices/register") {
        val body = try {
            call.receive<Map<String, String>>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "INVALID_BODY", "message" to "Request body tidak valid."))
            return@post
        }

        val bankSampahId = body["bank_sampah_id"] ?: ""
        val deviceName = body["device_name"]
        val devicePhoneNumber = body["device_phone_number"]
        val devicePubKey = body["device_pub_key"] ?: ""
        val deviceFingerprint = body["device_fingerprint"]

        val result = DeviceService.registerDevice(bankSampahId, deviceName, devicePhoneNumber, devicePubKey)
        if (result.containsKey("error")) {
            call.respond(HttpStatusCode.BadRequest, result)
        } else {
            call.respond(HttpStatusCode.Created, result)
        }
    }

    // GET /v1/devices — List registered devices
    get("/devices") {
        val bankId = call.request.queryParameters["bank_sampah_id"]?.takeIf { it.isNotBlank() } ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "bank_sampah_id required."))
            return@get
        }

        val result = DeviceService.listDevices(bankId)
        call.respond(HttpStatusCode.OK, result)
    }

    // POST /v1/devices/{id}/wipe — Trigger remote wipe of device data
    post("/devices/{id}/wipe") {
        if (!authorizeRole(call, "OPERATOR")) return@post
        val deviceId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "Device ID diperlukan."))
            return@post
        }

        val result = DeviceService.initiateRemoteWipe(deviceId)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "DEVICE_NOT_FOUND" -> HttpStatusCode.NotFound
                "ALREADY_WIPED" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result)
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
