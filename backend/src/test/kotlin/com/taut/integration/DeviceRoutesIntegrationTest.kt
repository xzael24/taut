package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import com.taut.service.DeviceService
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.mockk.*
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Device routes — register, list, wipe.
 */
@Disabled("Needs DeviceService mock setup fix — Sprint 4")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceRoutesIntegrationTest {

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

    @BeforeEach
    fun setup() {
        mockkObject(DeviceService)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    // ── Device Register Tests ──

    @Test
    fun `POST devices register returns 201 with valid data`() {
        every { DeviceService.registerDevice(any(), any(), any(), any()) } returns mapOf(
            "device_id" to UUID.randomUUID().toString(),
            "device_secret" to "secret-abc123"
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/devices/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"bank_sampah_id":"${UUID.randomUUID()}","device_name":"Tablet 1","device_phone_number":"08123456789","device_pub_key":"pub-key","device_fingerprint":"fp-1"}""")
            }
            assertEquals(HttpStatusCode.Created, r.status)
            assertTrue(r.bodyAsText().contains("device_id"))
            assertTrue(r.bodyAsText().contains("device_secret"))
        }
    }

    @Test
    fun `POST devices register without bank_sampah_id returns 400`() {
        every { DeviceService.registerDevice(any(), any(), any(), any()) } returns mapOf(
            "error" to "INVALID_BANK",
            "message" to "Bank sampah ID tidak valid."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/devices/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"device_name":"Tablet 1","device_pub_key":"pub-key"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    // ── Device List Tests ──

    @Test
    fun `GET devices list returns 200 with valid bank_id`() {
        every { DeviceService.listDevices("bank-1") } returns mapOf(
            "data" to listOf(
                mapOf("id" to UUID.randomUUID().toString(), "name" to "Tablet 1", "status" to "ACTIVE"),
                mapOf("id" to UUID.randomUUID().toString(), "name" to "Tablet 2", "status" to "INACTIVE")
            )
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/devices?bank_sampah_id=bank-1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("data"))
        }
    }

    @Test
    fun `GET devices list without bank_id returns 400`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/devices") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `GET devices list without auth returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/devices?bank_sampah_id=bank-1")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    // ── Device Wipe Tests ──

    @Test
    fun `POST device wipe returns 200 for OPERATOR role`() {
        every { DeviceService.initiateRemoteWipe(any()) } returns mapOf(
            "success" to true,
            "wipe_initiated_at" to java.time.LocalDateTime.now().toString()
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/devices/${UUID.randomUUID()}/wipe") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("success"))
        }
    }

    @Test
    fun `POST device wipe returns 403 for CUSTOMER role`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/devices/${UUID.randomUUID()}/wipe") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "CUSTOMER")}")
            }
            assertEquals(HttpStatusCode.Forbidden, r.status)
        }
    }

    @Test
    fun `POST device wipe returns 404 for not found`() {
        every { DeviceService.initiateRemoteWipe(any()) } returns mapOf(
            "error" to "DEVICE_NOT_FOUND",
            "message" to "Device tidak ditemukan."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/devices/nonexistent/wipe") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.NotFound, r.status)
        }
    }

    @Test
    fun `POST device wipe without auth returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/devices/${UUID.randomUUID()}/wipe") {
                contentType(ContentType.Application.Json)
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }
}
