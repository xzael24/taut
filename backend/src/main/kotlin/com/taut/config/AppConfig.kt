package com.taut.config

data class ServerConfig(
    val host: String,
    val port: Int
)

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int,
    val minimumIdle: Int,
    val idleTimeout: Long,
    val connectionTimeout: Long,
    val maxLifetime: Long
)

data class JwtConfig(
    val secret: String,
    val accessTokenExpirySeconds: Long,
    val refreshTokenExpirySeconds: Long,
    val issuer: String,
    val audience: String,
    val bcryptCost: Int = 12
)

data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val redis: RedisConfig? = null,
    val sms: SmsConfig? = null,
    val cdn: CdnConfig? = null
) {
    companion object
}

data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String?
)

data class SmsConfig(
    val provider: String,
    val apiKey: String,
    val fromNumber: String
) {
    companion object {
        const val DEFAULT_PROVIDER = "twilio"
    }
}

data class CdnConfig(
    val baseUrl: String,
    val reportsPrefix: String = "reports"
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://cdn.taut.id"
    }
}

fun AppConfig.Companion.load(): AppConfig {
    val jwtSecret = System.getenv("TAUT_JWT_SECRET")
        ?: throw IllegalStateException("TAUT_JWT_SECRET environment variable is required. Set it to a secure random string (min 32 chars).")
    val dbPassword = System.getenv("TAUT_DB_PASSWORD")
        ?: throw IllegalStateException("TAUT_DB_PASSWORD environment variable is required.")

    return AppConfig(
        server = ServerConfig(
            host = System.getenv("TAUT_HOST") ?: "0.0.0.0",
            port = (System.getenv("TAUT_PORT") ?: "8080").toInt()
        ),
        database = DatabaseConfig(
            url = System.getenv("TAUT_DB_URL") ?: "jdbc:postgresql://localhost:5432/taut",
            user = System.getenv("TAUT_DB_USER") ?: "taut",
            password = dbPassword,
            maximumPoolSize = 20,
            minimumIdle = 5,
            idleTimeout = 300_000L,
            connectionTimeout = 30_000L,
            maxLifetime = 1_800_000L
        ),
        jwt = JwtConfig(
            secret = jwtSecret,
            accessTokenExpirySeconds = (System.getenv("TAUT_JWT_ACCESS_EXPIRY") ?: "900").toLong(),
            refreshTokenExpirySeconds = (System.getenv("TAUT_JWT_REFRESH_EXPIRY") ?: "2592000").toLong(),
            issuer = System.getenv("TAUT_JWT_ISSUER") ?: "taut",
            audience = System.getenv("TAUT_JWT_AUDIENCE") ?: "taut-api",
            bcryptCost = (System.getenv("TAUT_BCRYPT_COST") ?: "12").toInt()
        ),
        redis = null,
        sms = null,
        cdn = CdnConfig(
            baseUrl = System.getenv("TAUT_CDN_URL") ?: CdnConfig.DEFAULT_BASE_URL,
            reportsPrefix = System.getenv("TAUT_CDN_REPORTS_PREFIX") ?: "reports"
        )
    )
}
