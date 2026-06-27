package com.taut.plugins

import io.ktor.server.response.*
import com.taut.config.AppConfig
import com.taut.service.CatalogService
import com.taut.service.GrpcHealthService
import com.taut.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

private val routingLog = LoggerFactory.getLogger("com.taut.plugins.Routing")

/**
 * Enforce role-based access control. Returns 403 if the principal's role is not in the allowed set.
 * Takes [call] explicitly to avoid PipelineContext type issues in route handlers.
 */
internal suspend fun authorizeRole(call: ApplicationCall, vararg allowedRoles: String): Boolean {
    val principal = call.principal<JWTPrincipal>()
    val role = principal?.getClaim("role", String::class) ?: ""
    if (role in allowedRoles) return true
    routingLog.warn("RBAC denied: role={}, required={}, path={}", role, allowedRoles.contentToString(), call.request.uri)
    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "FORBIDDEN", "message" to "Akses ditolak. Role tidak memiliki izin."))
    return false
}

fun Application.configureRouting() {
    routing {
        // Health check endpoint (no auth)
        get("/health") {
            val dbHealthy = try {
                val ds = com.taut.config.DataSources.primary
                ds.connection.use { conn ->
                    conn.createStatement().executeQuery("SELECT 1").use { it.next() }
                }
                true
            } catch (e: Exception) {
                routingLog.warn("Health check: DB unreachable: {}", e.message)
                false
            }

            val grpcHealthy = GrpcHealthService.isHealthy()  // real gRPC health status

            val migrationHealthy = try {
                val ds = com.taut.config.DataSources.primary
                ds.connection.use { conn ->
                    val rs = conn.createStatement().executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")
                    rs.next()
                }
                true
            } catch (e: Exception) {
                routingLog.warn("Health check: Flyway migration status unavailable: {}", e.message)
                false
            }

            val allHealthy = dbHealthy && grpcHealthy && migrationHealthy
            val statusCode = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(statusCode, mapOf(
                "status" to if (allHealthy) "ok" else "degraded",
                "service" to "taut-backend",
                "checks" to mapOf(
                    "database" to dbHealthy,
                    "grpc" to grpcHealthy,
                    "migration" to migrationHealthy
                )
            ))
        }

        // ── Auth routes (no auth required) ──
        route("/v1/auth") {
            authRoutes()
        }

        // ── Public catalog endpoint: categories are public ──
        route("/v1") {
            get("/categories") {
                val result = CatalogService.listCategories()
                call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
            }
        }

        // ── All other routes require JWT authentication ──
        authenticate("auth-jwt") {
            // Transaction routes
            route("/v1") {
                transactionRoutes()
            }

            // Catalog routes (prices and history - protected)
            route("/v1") {
                get("/prices") {
                    val regionId = call.request.queryParameters["region_id"]?.toIntOrNull()
                    val lastVersion = call.request.queryParameters["last_version"]?.toIntOrNull()
                    val result = CatalogService.getPriceCatalog(regionId, lastVersion)
                    call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
                }
                get("/categories/{id}/prices/history") {
                    val categoryId = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("MISSING_PARAM"), "message" to JsonPrimitive("Category ID diperlukan."))))
                        return@get
                    }
                    val regionId = call.request.queryParameters["region_id"]?.toIntOrNull()
                    val fromDate = call.request.queryParameters["from_date"]
                    val toDate = call.request.queryParameters["to_date"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 20
                    val result = CatalogService.getPriceHistory(categoryId, regionId, fromDate, toDate, page, pageSize)
                    call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
                }
            }

            // Dashboard routes
            route("/v1") {
                dashboardRoutes()
            }

            // Device routes
            route("/v1") {
                deviceRoutes()
            }

            // Compliance (UU PDP) routes
            route("/v1") {
                complianceRoutes()
            }

            // SMS routes
            route("/v1") {
                smsRoutes()
            }

            // gRPC Sync stub
            route("/v1") {
                syncRoutes()
            }
        }
    }
}
