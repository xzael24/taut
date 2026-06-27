package com.taut

import com.taut.config.AppConfig
import com.taut.config.ConfigLoader
import com.taut.config.DataSources
import com.taut.config.load
import com.taut.db.Db
import com.taut.plugins.*
import com.taut.service.AuthService
import com.taut.service.GrpcHealthService
import com.taut.service.SyncServiceImpl
import io.grpc.ServerBuilder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("com.taut.ApplicationKt")

fun main() {
    // Validate all required environment variables BEFORE any other work
    ConfigLoader.validateRequired()

    val config = AppConfig.load()

    // Initialize database connection pool
    DataSources.init(config.database)

    // Initialize AuthService with JWT configuration
    AuthService.init(config.jwt)

    // Run database migrations via Flyway
    runMigrations(config.database)

    // Initialize Exposed ORM
    Db.init()

    // Start gRPC SyncService (aligned with Android client config)
    val grpcPort = System.getenv("TAUT_GRPC_PORT")?.toIntOrNull() ?: 9000
    val grpcShutdownTimeoutSeconds = System.getenv("TAUT_GRPC_SHUTDOWN_TIMEOUT_SECONDS")?.toIntOrNull() ?: 10

    val grpcServer = ServerBuilder.forPort(grpcPort)
        .addService(SyncServiceImpl())
        .addService(GrpcHealthService.service)
        .build()

    // Start gRPC server in background
    CoroutineScope(Dispatchers.IO).launch {
        grpcServer.start()
        log.info("[gRPC] SyncService server started on port $grpcPort")
        GrpcHealthService.markHealthy()
    }

    // Start Ktor HTTP server (blocking)
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        // CORS configuration from environment var (default to localhost for dev)
        install(CORS) {
            val corsOrigin = System.getenv("TAUT_CORS_ORIGIN") ?: "http://localhost:3000,http://localhost:8080"
            // Parse comma-separated allowed origins
            val allowedOrigins = corsOrigin.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (allowedOrigins.isEmpty()) {
                allowHost("localhost")
                allowHost("127.0.0.1")
            } else {
                allowedOrigins.forEach { origin ->
                    allowHost(origin)
                }
            }
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader("Authorization")
            allowHeader("Content-Type")
            allowHeader("X-Idempotency-Key")
            allowCredentials = true
        }

        // CSRF/XSRF protection removed — not included in ktor 2.3.13; JWT auth provides adequate protection

        // Graceful shutdown handler (ceo-shutdown) — monitors ApplicationStopping event
        environment.monitor.subscribe(ApplicationStopping) {
            try {
                GrpcHealthService.markNotHealthy()
                grpcServer.shutdown().awaitTermination(grpcShutdownTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                log.info("[gRPC] Server shut down gracefully within ${grpcShutdownTimeoutSeconds}s")
            } catch (e: InterruptedException) {
                log.warn("[gRPC] Shutdown interrupted: {}", e.message)
                grpcServer.shutdownNow()
            } catch (e: Exception) {
                log.error("[gRPC] Error during shutdown: {}", e.message, e)
                grpcServer.shutdownNow()
            }
            try {
                DataSources.primary.close()
                log.info("[DB] Connection pool closed")
            } catch (e: Exception) {
                log.error("[DB] Error closing connection pool: {}", e.message, e)
            }
        }

        configureSerialization()
        configureSecurity(config)
        configureStatusPages()
        configureCallLogging()
        configureRouting()
    }.start(wait = true)
}

private fun runMigrations(dbConfig: com.taut.config.DatabaseConfig) {
    val flyway = Flyway.configure()
        .dataSource(dbConfig.url, dbConfig.user, dbConfig.password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .load()
    flyway.migrate()
}
