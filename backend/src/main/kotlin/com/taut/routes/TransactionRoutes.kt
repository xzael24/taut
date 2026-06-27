package com.taut.routes

import com.taut.plugins.authorizeRole
import com.taut.service.TransactionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

/**
 * Transaction routes — CRUD operations for transactions.
 */
fun Route.transactionRoutes() {

    // GET /v1/banks/{bank_id}/transactions — List transactions for a bank sampah
    get("/banks/{bank_id}/transactions") {
        val bankId = call.parameters["bank_id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("MISSING_PARAM"), "message" to JsonPrimitive("bank_id diperlukan."))))
            return@get
        }
        val fromDate = call.request.queryParameters["from_date"]
        val toDate = call.request.queryParameters["to_date"]
        val type = call.request.queryParameters["type"]
        val status = call.request.queryParameters["status"]
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 20

        val result = TransactionService.listTransactions(bankId, fromDate, toDate, type, status, page, pageSize)
        call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
    }

    // GET /v1/transactions/{id} — Get a single transaction with items
    get("/transactions/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("MISSING_PARAM"), "message" to JsonPrimitive("ID transaksi diperlukan."))))
            return@get
        }

        val result = TransactionService.getTransaction(id)
        if (result != null) {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.NotFound, JsonObject(mapOf("error" to JsonPrimitive("NOT_FOUND"), "message" to JsonPrimitive("Transaksi tidak ditemukan."))))
        }
    }

    // POST /v1/transactions — Create a transaction (online path)
    post("/transactions") {
        val idempotencyKey = call.request.headers["X-Idempotency-Key"] ?: ""

        val body = try {
            call.receive<JsonObject>().toMutableMap()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("INVALID_BODY"), "message" to JsonPrimitive("Request body tidak valid."))))
            return@post
        }

        // Add idempotency key to request
        if (idempotencyKey.isNotBlank()) {
            body["idempotency_key"] = idempotencyKey
        }

        val result = TransactionService.createTransaction(body)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "DUPLICATE" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.Created, result.toSerializableJsonObject())
        }
    }

    // POST /v1/transactions/{id}/void — Void a transaction (admin only)
    post("/transactions/{id}/void") {
        if (!authorizeRole(call, "OPERATOR", "DLH_ADMIN")) return@post
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("MISSING_PARAM"), "message" to JsonPrimitive("ID transaksi diperlukan."))))
            return@post
        }
        val idempotencyKey = call.request.headers["X-Idempotency-Key"] ?: ""

        val body = try {
            call.receive<JsonObject>()
        } catch (_: Exception) {
            JsonObject(mapOf())
        }

        val reason = body["reason"]?.let { 
            if (it is JsonPrimitive && it.isString) it.content else null
        } ?: body["void_reason"]?.let { 
            if (it is JsonPrimitive && it.isString) it.content else null
        } ?: "Admin void"
        val adminId = body["admin_id"]?.let { 
            if (it is JsonPrimitive && it.isString) it.content else null
        } ?: body["actor_id"]?.let { 
            if (it is JsonPrimitive && it.isString) it.content else null
        } ?: ""

        val result = TransactionService.voidTransaction(id, reason, adminId, idempotencyKey)
        if (result.containsKey("error")) {
            val status = when (result["error"]) {
                "NOT_FOUND" -> HttpStatusCode.NotFound
                "ALREADY_VOIDED" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, result.toSerializableJsonObject())
        } else {
            call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
        }
    }

    // GET /v1/customers/{id}/transactions — Customer transaction history
    get("/customers/{id}/transactions") {
        val customerId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("MISSING_PARAM"), "message" to JsonPrimitive("Customer ID diperlukan."))))
            return@get
        }
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 20

        val result = TransactionService.listCustomerTransactions(customerId, page, pageSize)
        call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
    }
}
