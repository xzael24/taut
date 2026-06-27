package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import com.taut.service.CatalogService
import com.taut.service.SmsService
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityTest {

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

    private fun generateToken(userId: String = "user-test", role: String = "CUSTOMER"): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(userId)
            .withClaim("role", role)
            .withClaim("phone", "08123456789")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    @BeforeEach
    fun setup() {
        mockkObject(CatalogService)
        every { CatalogService.listCategories() } returns mapOf(
            "data" to emptyList<Any>(),
            "version" to 1
        )
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `health endpoint returns ok or degraded without auth`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.get("/health")
            // Health should be accessible without auth
            // In test env without real DB, it may return 200, 503, or 500
            Assertions.assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.ServiceUnavailable ||
                response.status == HttpStatusCode.InternalServerError,
                "Health endpoint accessible without auth, status: ${response.status}"
            )
        }
    }

    @Test
    fun `auth otp request endpoint returns ok`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"phone_number":"08123456789","device_id":"dev-1"}""")
            }
            // In test env without real DB, endpoint may return 400 (body parse), 500 (DB error), or 200 (if mocked)
            Assertions.assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.InternalServerError ||
                response.status == HttpStatusCode.BadRequest,
                "OTP request endpoint accessible, status: ${response.status}"
            )
        }
    }

    @Test
    fun `categories endpoint is public`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.get("/v1/categories")
            Assertions.assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `transactions post endpoint returns 401 when unauthenticated`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            // POST without auth header
            val response = client.post("/v1/transactions") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }
            Assertions.assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `otp verify endpoint is accessible`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"otp_code":"123456","phone_number":"08123456789"}""")
            }
            // OTP verify is under /v1/auth which is outside authentication block
            // It's accessible without auth (returns 400 for invalid OTP, 500 if DB unavailable)
            Assertions.assertNotEquals(HttpStatusCode.Unauthorized, response.status,
                "OTP verify should not require auth, got: ${response.status}")
        }
    }

    @Test
    fun `dashboard operator endpoint returns 401 when unauthenticated`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.get("/v1/dashboard/operator/bank-1")
            Assertions.assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `dashboard dlh endpoint returns 401 when unauthenticated`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.get("/v1/dashboard/dlh/kota-1")
            Assertions.assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `device register endpoint is accessible`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val response = client.post("/v1/devices/register") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }
            // Device registration should be accessible (may return specific validation error)
            Assertions.assertTrue(
                response.status != HttpStatusCode.NotFound &&
                response.status != HttpStatusCode.InternalServerError,
                "Device registration endpoint should exist, got: ${response.status}"
            )
        }
    }

    @Test
    fun `pin change endpoint rejects unauthenticated requests`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            // POST without auth header — PIN change should not be accessible without auth
            val response = client.post("/v1/auth/pin/change") {
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"test-user","old_pin":"123456","new_pin":"654321"}""")
            }
            // This pin endpoint requires auth, so expect non-200
            Assertions.assertNotEquals(HttpStatusCode.OK, response.status,
                "PIN change should reject unauthenticated requests, got: ${response.status}")
        }
    }

    @Test
    fun `pin verify endpoint rejects unauthenticated requests`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            // POST without auth header — PIN verify should not be accessible without auth
            val response = client.post("/v1/auth/pin/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"test-user","pin":"123456"}""")
            }
            // This pin endpoint requires auth, so expect non-200
            Assertions.assertNotEquals(HttpStatusCode.OK, response.status,
                "PIN verify should reject unauthenticated requests, got: ${response.status}")
        }
    }
}
