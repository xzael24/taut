package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import com.taut.service.CatalogService
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogRoutesTest {

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
    fun setup() { mockkObject(CatalogService) }

    @AfterEach
    fun teardown() { unmockkAll() }

    @Test
    fun `GET categories returns 200 with auth`() {
        every { CatalogService.listCategories() } returns mapOf(
            "data" to listOf(
                mapOf("id" to UUID.randomUUID().toString(), "name" to "Plastik"),
                mapOf("id" to UUID.randomUUID().toString(), "name" to "Kertas")
            ),
            "version" to 1
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/categories") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("Plastik"))
        }
    }

    @Test
    fun `GET categories without auth returns 200 as public endpoint`() {
        every { CatalogService.listCategories() } returns mapOf(
            "data" to listOf(mapOf("id" to UUID.randomUUID().toString(), "name" to "Kardus")),
            "version" to 1
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/categories")
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("Kardus"))
        }
    }

    @Test
    fun `GET prices with lastVersion param returns 200`() {
        every { CatalogService.getPriceCatalog(null, 1) } returns mapOf(
            "data" to listOf(
                mapOf("category_id" to UUID.randomUUID().toString(), "price" to 1000)
            ),
            "version" to 2
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/prices?last_version=1") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("version"))
        }
    }

    @Test
    fun `GET prices without auth returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/prices")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `GET categories price history returns 200 with auth`() {
        val categoryId = UUID.randomUUID().toString()
        every { CatalogService.getPriceHistory(categoryId, null, null, null, 1, 20) } returns mapOf(
            "data" to listOf(mapOf("price" to 500, "effective_from" to "2024-01-01")),
            "total_count" to 1, "page" to 1, "page_size" to 20
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/categories/$categoryId/prices/history") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("effective_from"))
        }
    }

    @Test
    fun `GET categories price history without auth returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/categories/${UUID.randomUUID()}/prices/history")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }
}
