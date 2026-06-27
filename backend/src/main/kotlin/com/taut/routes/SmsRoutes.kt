package com.taut.routes

import com.taut.plugins.authorizeRole
import com.taut.service.SmsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * SMS routes — for admin monitoring of SMS queue.
 */
fun Route.smsRoutes() {

    // GET /v1/sms/queue — List pending/failed SMS messages
    get("/sms/queue") {
        if (!authorizeRole(call, "DLH_ADMIN")) return@get
        val status = call.request.queryParameters["status"]
        val bankSampahId = call.request.queryParameters["bank_sampah_id"]
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 20

        val result = SmsService.listQueue(status, bankSampahId, page, pageSize)
        call.respond(HttpStatusCode.OK, result)
    }

    // POST /v1/sms/queue/{id}/retry — Retry a failed SMS
    post("/sms/queue/{id}/retry") {
        if (!authorizeRole(call, "DLH_ADMIN")) return@post
        val smsId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "SMS ID diperlukan."))
            return@post
        }

        val result = SmsService.retrySms(smsId)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "NOT_FOUND" -> HttpStatusCode.NotFound
                "MAX_RETRIES" -> HttpStatusCode.Gone
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result)
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
