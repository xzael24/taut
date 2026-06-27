package com.taut.plugins

import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.application.*
import io.ktor.util.*
import org.slf4j.event.Level

fun Application.configureCallLogging() {
    install(CallLogging) {
        level = when (System.getenv("TAUT_LOG_LEVEL")?.uppercase()) {
            "TRACE" -> Level.TRACE
            "DEBUG" -> Level.DEBUG
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }
        filter { call -> call.request.path().startsWith("/v1") }
        format { call ->
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val status = call.response.status()
            val duration = call.processingTimeMillis()
            "$method $path -> $status (${duration}ms)"
        }
    }
}
