package com.taut.plugins

import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.util.UUID

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to "INTERNAL_ERROR",
                    "message" to "Terjadi kesalahan server internal. Silakan coba lagi nanti.",
                    "request_id" to UUID.randomUUID().toString()
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf(
                    "error" to "NOT_FOUND",
                    "message" to "Resource tidak ditemukan."
                )
            )
        }
    }
}
