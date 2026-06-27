package com.taut.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import com.taut.service.TransactionService
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
class TransactionRoutesTest {
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
    fun setup() { mockkObject(TransactionService) }

    @AfterEach
    fun teardown() { unmockkAll() }

    @Test
    fun `POST transactions with valid token returns 201`() {
        every { TransactionService.createTransaction(any()) } returns mapOf(
            "transaction_id" to UUID.randomUUID().toString(),
            "status" to "CONFIRMED", "total_weight" to 5000,
            "total_value" to 15000, "item_count" to 3, "fraud_flag" to false
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/transactions") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                contentType(ContentType.Application.Json)
                setBody("""{"bank_sampah_id":"${UUID.randomUUID()}","operator_id":"${UUID.randomUUID()}","customer_id":"${UUID.randomUUID()}","items":[{"category_id":"${UUID.randomUUID()}","weight":2000}]}""")
            }
            println("RESPONSE STATUS: ${r.status}")
            println("RESPONSE BODY: ${r.bodyAsText()}")
            assertEquals(HttpStatusCode.Created, r.status)
        }
    }

    @Test
    fun `POST transactions without token returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/transactions") {
                contentType(ContentType.Application.Json)
                setBody("""{"bank_sampah_id":"abc"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `POST transactions with invalid token returns 401`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/transactions") {
                header(HttpHeaders.Authorization, "Bearer invalid.token.here")
                contentType(ContentType.Application.Json)
                setBody("""{"bank_sampah_id":"abc"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `GET transactions by id returns 200 when found`() {
        val txId = UUID.randomUUID().toString()
        every { TransactionService.getTransaction(txId) } returns mapOf(
            "id" to txId, "status" to "CONFIRMED", "items" to emptyList<Any>()
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/transactions/$txId") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            println("RESPONSE STATUS: ${r.status}")
            println("RESPONSE BODY: ${r.bodyAsText()}")
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains(txId))
        }
    }

    @Test
    fun `GET transactions by id returns 404 when not found`() {
        every { TransactionService.getTransaction(any()) } returns null
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/transactions/nonexistent-id") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            assertEquals(HttpStatusCode.NotFound, r.status)
        }
    }

    @Test
    fun `GET banks bankId transactions with filter params works`() {
        val bankId = UUID.randomUUID().toString()
        every { TransactionService.listTransactions(bankId, null, null, null, null, 1, 20) } returns mapOf(
            "data" to emptyList<Any>(), "pagination" to mapOf("page" to 1, "total_count" to 0)
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/banks/$bankId/transactions") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            println("RESPONSE STATUS: ${r.status}")
            println("RESPONSE BODY: ${r.bodyAsText()}")
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }

    @Test
    fun `GET customers id transactions returns 200`() {
        val custId = UUID.randomUUID().toString()
        every { TransactionService.listCustomerTransactions(custId, 1, 20) } returns mapOf(
            "data" to emptyList<Any>(), "pagination" to mapOf("page" to 1, "total_count" to 0)
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.get("/v1/customers/$custId/transactions") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            }
            println("RESPONSE STATUS: ${r.status}")
            println("RESPONSE BODY: ${r.bodyAsText()}")
            assertEquals(HttpStatusCode.OK, r.status)
        }
    }

    @Test
    fun `POST transactions id void requires auth`() {
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/transactions/some-id/void") {
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"test"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun `POST transactions duplicate returns 409`() {
        every { TransactionService.createTransaction(any()) } returns mapOf(
            "error" to "DUPLICATE", "message" to "Duplicate.", "transaction_id" to UUID.randomUUID().toString()
        )
        testApplication {
            application { configureSerialization(); configureSecurity(testConfig); configureStatusPages(); configureRouting() }
            val r = client.post("/v1/transactions") {
                header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
                header("X-Idempotency-Key", "dup-key-123")
                contentType(ContentType.Application.Json)
                setBody("""{"bank_sampah_id":"${UUID.randomUUID()}","operator_id":"${UUID.randomUUID()}","customer_id":"${UUID.randomUUID()}","items":[{"category_id":"${UUID.randomUUID()}","weight":1000}]}""")
            }
            println("RESPONSE STATUS: ${r.status}")
            println("RESPONSE BODY: ${r.bodyAsText()}")
            assertEquals(HttpStatusCode.Conflict, r.status)
        }
    }
}
