package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Sync routes — gRPC sync status and push (REST stubs).
 */
@Disabled("Needs SyncService mock setup fix — Sprint 4")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncRoutesIntegrationTest {

    private val jwtSecret = "test-jwt-secret-32-chars-minimum!!"
    private val jwtIssuer = "taut-test"
    private val jwtAudience = "taut-test-api"

    private val testConfig = AppConfig(
        server = ServerConfig("0.0.0.0", 8080),
        database = DatabaseConfig("test-url", "test", "test", 5, 1, 300000, 30000, 1800000),
        jwt = JwtConfig(jwtSecret, 900, 2592000, jwtIssuer, jwtAudience),
        redis = null,
        sms = null
    )

    private fun generateToken(userId: String = "user-test", role: String = "OPERATOR"): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(userId)
            .withClaim("role", role)
            .withClaim("phone", "08123456789")
            .withJWTId(UUID.randomUUID().toString())
            .withNotBefore(Date(System.currentTimeMillis() - 60000))
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    @AfterEach
    fun teardown() {
        io.mockk.unmockkAll()
    }

    // ── Sync Status Tests ──

    @Test
    fun `GET sync status returns 501 not implemented`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/sync/status?device_id=dev-1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.NotImplemented, r.status)
            assertTrue(r.bodyAsText().contains("NOT_IMPLEMENTED"))
        }
    }

    @Test
    fun `GET sync status without auth returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/sync/status?device_id=dev-1")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `GET sync status without device_id param is still routed`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/sync/status") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.NotImplemented, r.status)
        }
    }

    // ── Sync Push Tests ──

    @Test
    fun `POST sync push returns 501 not implemented`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/sync/push") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                contentType(ContentType.Application.Json)
                setBody("""{"transactions":[],"cursor":"abc"}""")
            }
            assertEquals(HttpStatusCode.NotImplemented, r.status)
            assertTrue(r.bodyAsText().contains("NOT_IMPLEMENTED"))
        }
    }

    @Test
    fun `POST sync push without auth returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/sync/push") {
                contentType(ContentType.Application.Json)
                setBody("""{"transactions":[]}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `POST sync push with device headers is accepted`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/sync/push") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                header("X-Device-ID", "dev-uuid-1")
                header("X-Device-Signature", "hmac-signature")
                contentType(ContentType.Application.Json)
                setBody("""{"transactions":[],"processed_ids":[],"counter_deltas":{}}""")
            }
            assertEquals(HttpStatusCode.NotImplemented, r.status)
        }
    }
}
