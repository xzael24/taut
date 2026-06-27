package com.taut.config

import org.slf4j.LoggerFactory

/**
 * Centralized environment-variable loader with fail-fast validation.
 *
 * All required env vars are validated once at startup; if any are missing the
 * application refuses to start with a clear, actionable error message listing
 * every missing variable.
 *
 * Optional variables use [getOr] / [getIntOr] helpers so callers never have to
 * repeat the null-coalesce boilerplate.
 */
object ConfigLoader {

    private val log = LoggerFactory.getLogger(ConfigLoader::class.java)

    // ------------------------------------------------------------------ //
    //  Public API                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Load a required env var.  Throws [IllegalStateException] immediately
     * (during startup) if the variable is absent or blank.
     */
    fun requireEnv(name: String): String {
        val value = System.getenv(name)
        if (value.isNullOrBlank()) {
            throw IllegalStateException(
                "FATAL: Required environment variable '$name' is not set. " +
                    "Set it before starting the application."
            )
        }
        return value
    }

    /** Load an optional env var with a default fallback. */
    fun getOr(name: String, default: String): String =
        System.getenv(name)?.ifBlank { null } ?: default

    /** Load an optional env var as [Int] with a default fallback. */
    fun getIntOr(name: String, default: Int): Int =
        getOr(name, default.toString()).toIntOrNull()
            ?: throw IllegalStateException(
                "Environment variable '$name' must be an integer, but got: '${System.getenv(name)}'"
            )

    /** Load an optional env var as [Long] with a default fallback. */
    fun getLongOr(name: String, default: Long): Long =
        getOr(name, default.toString()).toLongOrNull()
            ?: throw IllegalStateException(
                "Environment variable '$name' must be a long integer, but got: '${System.getenv(name)}'"
            )

    // ------------------------------------------------------------------ //
    //  Startup validation                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Validate ALL required environment variables in one shot.
     * Returns early on the first missing var so the operator can fix them
     * one at a time (each failure logs the specific missing var).
     *
     * Call this as the very first line of [main].
     */
    fun validateRequired() {
        val required = listOf(
            "TAUT_JWT_SECRET",
            "TAUT_DB_PASSWORD"
        )

        val missing = mutableListOf<String>()
        for (envVar in required) {
            if (System.getenv(envVar).isNullOrBlank()) {
                missing.add(envVar)
            }
        }

        if (missing.isNotEmpty()) {
            val msg = StringBuilder()
            msg.appendLine("═══════════════════════════════════════════════════════════════")
            msg.appendLine(" TAUT STARTUP FAILED — missing required environment variables")
            msg.appendLine("═══════════════════════════════════════════════════════════════")
            for (envVar in missing) {
                msg.appendLine("   ✗ $envVar")
            }
            msg.appendLine()
            msg.appendLine(" Required env vars:")
            msg.appendLine("   TAUT_JWT_SECRET       — HMAC-256 signing key (min 32 chars)")
            msg.appendLine("   TAUT_DB_PASSWORD      — PostgreSQL password")
            msg.appendLine()
            msg.appendLine(" Optional env vars:")
            msg.appendLine("   TAUT_HOST             — bind address (default: 0.0.0.0)")
            msg.appendLine("   TAUT_PORT             — HTTP port (default: 8080)")
            msg.appendLine("   TAUT_DB_URL           — JDBC URL (default: jdbc:postgresql://localhost:5432/taut)")
            msg.appendLine("   TAUT_DB_USER          — DB user (default: taut)")
            msg.appendLine("   TAUT_CORS_ORIGIN     — comma-separated allowed origins")
            msg.appendLine("   TAUT_GRPC_PORT        — gRPC port (default: 9000)")
            msg.appendLine("   TAUT_GRPC_SHUTDOWN_TIMEOUT_SECONDS — graceful shutdown timeout (default: 10)")
            msg.appendLine("   TAUT_JWT_ACCESS_EXPIRY  — access token TTL in seconds (default: 900)")
            msg.appendLine("   TAUT_JWT_REFRESH_EXPIRY — refresh token TTL in seconds (default: 2592000)")
            msg.appendLine("   TAUT_JWT_ISSUER       — JWT issuer (default: taut)")
            msg.appendLine("   TAUT_JWT_AUDIENCE     — JWT audience (default: taut-api)")
            msg.appendLine("═══════════════════════════════════════════════════════════════")
            throw IllegalStateException(msg.toString())
        }

        // Additional: warn about weak JWT secret
        val jwtSecret = System.getenv("TAUT_JWT_SECRET") ?: ""
        if (jwtSecret.length < 32) {
            log.warn(
                "TAUT_JWT_SECRET is shorter than 32 characters ({}). " +
                    "This is insecure — use a strong random string for production.",
                jwtSecret.length
            )
        }

        log.info("Environment variable validation passed — all required vars present.")
    }
}
