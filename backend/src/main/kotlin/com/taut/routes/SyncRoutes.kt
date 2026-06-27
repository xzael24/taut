package com.taut.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Sync routes — placeholder for gRPC bidirectional sync.
 * In production, this will be a gRPC endpoint handled by Envoy/Kong.
 * The REST stubs here provide mock interfaces for development/testing.
 *
 * Spec: docs/api-spec.md §3.1 (gRPC SyncService).
 */
fun Route.syncRoutes() {

    // GET /v1/sync/status — Get current sync status for a device
    get("/sync/status") {
        val deviceId = call.request.queryParameters["device_id"] ?: return@get
        // TODO: Authenticate via JWT + X-Device-ID + X-Device-Signature
        // TODO: Query latest sync_log for this device
        // TODO: Count pending_sync transactions for this bank
        // TODO: Return sync status
        call.respond(
            HttpStatusCode.NotImplemented,
            mapOf(
                "error" to "NOT_IMPLEMENTED",
                "message" to "Sync status endpoint belum diimplementasi."
            )
        )
    }

    // POST /v1/sync/push — One-shot sync push (for testing; real impl uses gRPC)
    post("/sync/push") {
        // TODO: Authenticate via JWT + HMAC signature
        // TODO: Parse SyncBatch (transactions + counter deltas)
        // TODO: Validate HMAC signatures
        // TODO: Deduplicate via processed_ids
        // TODO: Process transactions (validate, calculate, insert)
        // TODO: Return SyncResponse (acks + catalog updates + new cursor)
        call.respond(
            HttpStatusCode.NotImplemented,
            mapOf(
                "error" to "NOT_IMPLEMENTED",
                "message" to "Sync push endpoint belum diimplementasi."
            )
        )
    }
}
