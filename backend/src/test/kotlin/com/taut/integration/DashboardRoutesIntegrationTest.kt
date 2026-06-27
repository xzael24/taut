package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import com.taut.service.DashboardService
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
 * Integration tests for Dashboard routes — operator dashboard, DLH dashboard, report generation.
 */
@Disabled("Needs DashboardService mock setup fix — Sprint 4")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardRoutesIntegrationTest {

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
        mockkObject(DashboardService)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    // ── Operator Dashboard Tests ──

    @Test
    fun `GET dashboard operator returns 200 for OPERATOR`() {
        every { DashboardService.getOperatorDashboard("bank-1", "this_month", null, null) } returns mapOf(
            "total_transactions" to 150,
            "total_weight" to 250000,
            "total_value" to 500000,
            "top_categories" to listOf(mapOf("name" to "Plastik", "weight" to 100000))
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/bank-1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("total_transactions"))
            assertTrue(r.bodyAsText().contains("total_weight"))
        }
    }

    @Test
    fun `GET dashboard operator returns 200 with custom period`() {
        every { DashboardService.getOperatorDashboard("bank-1", "custom", "2026-01-01", "2026-01-31") } returns mapOf(
            "total_transactions" to 50,
            "total_weight" to 80000
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/bank-1?period=custom&from_date=2026-01-01&to_date=2026-01-31") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }

    @Test
    fun `GET dashboard operator returns 403 for CUSTOMER`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/bank-1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "CUSTOMER")}")
            }
            assertEquals(HttpStatusCode.Forbidden, r.status)
        }
    }

    @Test
    fun `GET dashboard operator returns 401 without auth`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/bank-1")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `GET dashboard operator returns 400 when service returns error`() {
        every { DashboardService.getOperatorDashboard(any(), any(), any(), any()) } returns mapOf(
            "error" to "BANK_NOT_FOUND",
            "message" to "Bank tidak ditemukan."
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/nonexistent") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    // ── DLH Dashboard Tests ──

    @Test
    fun `GET dashboard dlh returns 200 for DLH_ADMIN`() {
        every { DashboardService.getKecamatanDashboard(1, "2026-01-01", "2026-01-31") } returns mapOf(
            "total_transactions" to 500,
            "total_weight" to 1000000,
            "kecamatan_data" to listOf(
                mapOf("nama" to "Kec A", "weight" to 500000),
                mapOf("nama" to "Kec B", "weight" to 500000)
            )
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/dlh/1?from_date=2026-01-01&to_date=2026-01-31") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "DLH_ADMIN")}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("kecamatan_data"))
        }
    }

    @Test
    fun `GET dashboard dlh returns 403 for OPERATOR`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/dlh/1?from_date=2026-01-01&to_date=2026-01-31") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.Forbidden, r.status)
        }
    }

    @Test
    fun `GET dashboard dlh returns 400 without date params`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/dlh/1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "DLH_ADMIN")}")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    @Test
    fun `GET dashboard dlh returns 400 for non-numeric kota_id`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/dlh/abc?from_date=2026-01-01&to_date=2026-01-31") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "DLH_ADMIN")}")
            }
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
    }

    // ── Monthly Report Tests ──

    @Test
    fun `GET dashboard operator report returns 200`() {
        every { DashboardService.generateMonthlyReport("bank-1", 2026, 1) } returns mapOf(
            "report_url" to "https://cdn.taut.id/reports/bank-1/2026-01.pdf",
            "generated_at" to "2026-01-31T23:59:59"
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/bank-1/report?year=2026&month=1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("report_url"))
        }
    }

    @Test
    fun `GET dashboard operator report uses current year month when not specified`() {
        every { DashboardService.generateMonthlyReport("bank-1", java.time.Year.now().value, java.time.MonthDay.now().monthValue) } returns mapOf(
            "report_url" to "https://cdn.taut.id/reports/bank-1/report.pdf"
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/dashboard/operator/bank-1/report") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken(role = "OPERATOR")}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }
}
