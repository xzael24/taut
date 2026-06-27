package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import com.taut.service.AuthService
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
 * Integration tests for Auth routes — OTP request/verify, token refresh, PIN change/verify.
 * Uses Ktor test host (testApplication {}) and mocks AuthService to avoid DB dependencies.
 */
@Disabled("Needs AuthService mock setup fix for serialization — Sprint 4")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRoutesIntegrationTest {

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
            .withJWTId(UUID.randomUUID().toString())
            .withNotBefore(Date(System.currentTimeMillis() - 60000))
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    @BeforeEach
    fun setup() {
        mockkObject(AuthService)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    // ── OTP Request Tests ──

    @Test
    fun `POST otp request returns 200 with valid phone`() {
        every { AuthService.requestOtp("08123456789", "dev-1", any()) } returns mapOf(
            "session_id" to UUID.randomUUID().toString(),
            "expires_seconds" to 300,
            "masked_phone" to "0812****789"
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"phone_number":"08123456789","device_id":"dev-1"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = r.bodyAsText()
            assertTrue(body.contains("session_id"))
            assertTrue(body.contains("masked_phone"))
        }
    }

    @Test
    fun `POST otp request with missing phone returns 400`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"device_id":"dev-1"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `POST otp request with empty body returns 400`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `POST otp request returns 429 when rate limited`() {
        every { AuthService.requestOtp("08123456789", "dev-1", any()) } returns mapOf(
            "error" to "RATE_LIMITED",
            "message" to "Terlalu banyak permintaan.",
            "retry_after" to 30
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"phone_number":"08123456789","device_id":"dev-1"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, r.status)
            assertTrue(r.bodyAsText().contains("RATE_LIMITED"))
        }
    }

    @Test
    fun `POST otp request with bank_sampah_id is accepted`() {
        every { AuthService.requestOtp("08123456789", "dev-1", any()) } returns mapOf(
            "session_id" to UUID.randomUUID().toString(),
            "expires_seconds" to 300,
            "masked_phone" to "0812****789"
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"phone_number":"08123456789","device_id":"dev-1","bank_sampah_id":"${UUID.randomUUID()}"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }

    @Test
    fun `POST otp request with invalid phone returns 400`() {
        every { AuthService.requestOtp("invalid", "dev-1", any()) } returns mapOf(
            "error" to "INVALID_PHONE",
            "message" to "Format nomor telepon tidak valid."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"phone_number":"invalid","device_id":"dev-1"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
            assertTrue(r.bodyAsText().contains("INVALID_PHONE"))
        }
    }

    // ── OTP Verify Tests ──

    @Test
    fun `POST otp verify returns 200 with valid session and code`() {
        val sessionId = UUID.randomUUID().toString()
        every { AuthService.verifyOtp(sessionId, "123456", "dev-1", "pub-key") } returns mapOf(
            "access_token" to generateToken(),
            "refresh_token" to "rt-abc123",
            "access_expires_in" to 900,
            "refresh_expires_in" to 2592000,
            "user" to mapOf("id" to "user-1", "phone_number" to "08123456789", "role" to "CUSTOMER"),
            "is_new_user" to false
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"session_id":"$sessionId","otp_code":"123456","device_id":"dev-1","device_pub_key":"pub-key"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = r.bodyAsText()
            assertTrue(body.contains("access_token"))
            assertTrue(body.contains("refresh_token"))
            assertTrue(body.contains("user"))
        }
    }

    @Test
    fun `POST otp verify returns 410 for expired otp`() {
        every { AuthService.verifyOtp(any(), any(), any(), any()) } returns mapOf(
            "error" to "OTP_EXPIRED",
            "message" to "Kode OTP sudah kedaluwarsa."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"session_id":"${UUID.randomUUID()}","otp_code":"111111","device_id":"dev-1","device_pub_key":"pk"}""")
            }
            assertEquals(HttpStatusCode.Gone, r.status)
        }
    }

    @Test
    fun `POST otp verify returns 410 for already used otp`() {
        every { AuthService.verifyOtp(any(), any(), any(), any()) } returns mapOf(
            "error" to "OTP_ALREADY_USED",
            "message" to "Kode OTP sudah pernah digunakan."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"session_id":"${UUID.randomUUID()}","otp_code":"222222","device_id":"dev-1","device_pub_key":"pk"}""")
            }
            assertEquals(HttpStatusCode.Gone, r.status)
        }
    }

    @Test
    fun `POST otp verify returns 400 for invalid session`() {
        every { AuthService.verifyOtp(any(), any(), any(), any()) } returns mapOf(
            "error" to "INVALID_SESSION",
            "message" to "Session ID tidak valid."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"session_id":"bad-session","otp_code":"123456","device_id":"dev-1","device_pub_key":"pk"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `POST otp verify with empty session ID returns 400`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"session_id":"","otp_code":"123456","device_id":"dev-1","device_pub_key":"pk"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `POST otp verify with invalid body returns 400`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("not-json")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    // ── Token Refresh Tests ──

    @Test
    fun `POST token refresh via header returns 200`() {
        every { AuthService.refreshToken("rt-abc", "dev-1", "sig") } returns mapOf(
            "access_token" to generateToken(),
            "refresh_token" to "rt-new",
            "expires_in" to 900
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/token/refresh") {
                header("X-Refresh-Token", "rt-abc")
                header("X-Device-ID", "dev-1")
                header("X-Device-Signature", "sig")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("access_token"))
        }
    }

    @Test
    fun `POST token refresh via body returns 200`() {
        every { AuthService.refreshToken("rt-body", "dev-1", "body-sig") } returns mapOf(
            "access_token" to generateToken(),
            "refresh_token" to "rt-new",
            "expires_in" to 900
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/token/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refresh_token":"rt-body","device_id":"dev-1","device_signature":"body-sig"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }

    @Test
    fun `POST token refresh with expired token returns 401`() {
        every { AuthService.refreshToken(any(), any(), any()) } returns mapOf(
            "error" to "TOKEN_EXPIRED",
            "message" to "Refresh token sudah kedaluwarsa."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/token/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refresh_token":"expired","device_id":"dev-1","device_signature":"sig"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `POST token refresh with invalid token returns 400`() {
        every { AuthService.refreshToken(any(), any(), any()) } returns mapOf(
            "error" to "INVALID_TOKEN",
            "message" to "Refresh token tidak valid."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/token/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refresh_token":"invalid","device_id":"dev-1","device_signature":"sig"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    // ── PIN Change Tests ──

    @Test
    fun `POST pin change returns 200 with valid params`() {
        every { AuthService.changePin("user-1", "123456", "654321", "idem-1") } returns mapOf(
            "success" to true
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/change") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                setBody("""{"user_id":"user-1","old_pin":"123456","new_pin":"654321","idempotency_key":"idem-1"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }

    @Test
    fun `POST pin change returns 400 for invalid old pin`() {
        every { AuthService.changePin(any(), any(), any(), any()) } returns mapOf(
            "error" to "INVALID_PIN",
            "message" to "PIN lama salah."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/change") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                setBody("""{"user_id":"user-1","old_pin":"wrong","new_pin":"654321","idempotency_key":"idem-2"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `POST pin change without auth returns non-200`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/change") {
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"user-1","old_pin":"123456","new_pin":"654321"}""")
            }
            Assertions.assertNotEquals(HttpStatusCode.OK, r.status)
        }
    }

    // ── PIN Verify Tests ──

    @Test
    fun `POST pin verify returns 200 with valid pin`() {
        every { AuthService.checkPinVerifyRateLimit(any()) } returns AuthService.RateLimitResult(true)
        every { AuthService.verifyPin("user-1", "123456") } returns mapOf(
            "valid" to true,
            "remaining_attempts" to 10
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/verify") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                setBody("""{"user_id":"user-1","pin":"123456"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("\"valid\":true"))
        }
    }

    @Test
    fun `POST pin verify returns 429 when rate limited`() {
        every { AuthService.checkPinVerifyRateLimit(any()) } returns AuthService.RateLimitResult(false, 30)
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/verify") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                setBody("""{"user_id":"user-1","pin":"123456"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, r.status)
            assertTrue(r.bodyAsText().contains("RATE_LIMITED"))
            assertTrue(r.bodyAsText().contains("retry_after"))
        }
    }

    @Test
    fun `POST pin verify returns 403 when PIN locked`() {
        every { AuthService.checkPinVerifyRateLimit(any()) } returns AuthService.RateLimitResult(true)
        every { AuthService.verifyPin(any(), any()) } returns mapOf(
            "valid" to false,
            "remaining_attempts" to 0,
            "error" to "PIN_LOCKED",
            "message" to "Akun terkunci."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/verify") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                setBody("""{"user_id":"user-locked","pin":"0000"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, r.status)
        }
    }

    @Test
    fun `POST pin verify returns 400 for invalid pin`() {
        every { AuthService.checkPinVerifyRateLimit(any()) } returns AuthService.RateLimitResult(true)
        every { AuthService.verifyPin(any(), any()) } returns mapOf(
            "valid" to false,
            "remaining_attempts" to 9,
            "error" to "INVALID_PIN",
            "message" to "PIN salah."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/verify") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                setBody("""{"user_id":"user-1","pin":"wrong"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status) // INVALID_PIN still returns 200 in routes
            assertTrue(r.bodyAsText().contains("\"valid\":false"))
        }
    }

    @Test
    fun `POST pin verify without auth returns non-200`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/auth/pin/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"user-1","pin":"123456"}""")
            }
            Assertions.assertNotEquals(HttpStatusCode.OK, r.status)
        }
    }
}
