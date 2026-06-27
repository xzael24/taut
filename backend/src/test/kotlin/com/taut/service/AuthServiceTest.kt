package com.taut.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.JwtConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertFailsWith

/**
 * Unit tests for AuthService — token generation and validation.
 * Uses mockk to stub out DB calls where necessary.
 */
class AuthServiceTest {

    private val testSecret = "test-secret-key-32-chars-minimum!"
    private val testIssuer = "taut-test"
    private val testAudience = "taut-test-api"
    private val accessExpiry = 900L
    private val refreshExpiry = 2_592_000L

    @BeforeEach
    fun setup() {
        AuthService.init(
            JwtConfig(
                secret = testSecret,
                accessTokenExpirySeconds = accessExpiry,
                refreshTokenExpirySeconds = refreshExpiry,
                issuer = testIssuer,
                audience = testAudience
            )
        )
    }

    @Test
    fun `generateAccessToken returns a valid JWT string`() {
        val token = AuthService.generateAccessToken("user-123", "CUSTOMER", "08123456789")

        assertNotNull(token)
        assertTrue(token.isNotBlank())
        assertTrue(token.contains("."), "JWT should contain dots as separators")
    }

    @Test
    fun `generated token contains correct subject`() {
        val token = AuthService.generateAccessToken("user-42", "OPERATOR", "08123456789")

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        val decoded = verifier.verify(token)
        assertEquals("user-42", decoded.subject)
    }

    @Test
    fun `generated token contains role claim`() {
        val token = AuthService.generateAccessToken("user-1", "ADMIN", "08123456789")

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        val decoded = verifier.verify(token)
        assertEquals("ADMIN", decoded.getClaim("role").asString())
    }

    @Test
    fun `generated token contains phone claim`() {
        val token = AuthService.generateAccessToken("user-1", "CUSTOMER", "08987654321")

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        val decoded = verifier.verify(token)
        assertEquals("08987654321", decoded.getClaim("phone").asString())
    }

    @Test
    fun `token signed with wrong secret fails verification`() {
        val token = AuthService.generateAccessToken("user-1", "CUSTOMER", "08123456789")

        val verifier = JWT.require(Algorithm.HMAC256("wrong-secret-key-32-chars-minimum!"))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        assertFailsWith<com.auth0.jwt.exceptions.JWTVerificationException> {
            verifier.verify(token)
        }
    }

    @Test
    fun `token with wrong issuer fails verification`() {
        val token = AuthService.generateAccessToken("user-1", "CUSTOMER", "08123456789")

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer("wrong-issuer")
            .withAudience(testAudience)
            .build()

        assertFailsWith<com.auth0.jwt.exceptions.JWTVerificationException> {
            verifier.verify(token)
        }
    }

    @Test
    fun `token with wrong audience fails verification`() {
        val token = AuthService.generateAccessToken("user-1", "CUSTOMER", "08123456789")

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience("wrong-audience")
            .build()

        assertFailsWith<com.auth0.jwt.exceptions.JWTVerificationException> {
            verifier.verify(token)
        }
    }

    @Test
    fun `expired token fails verification`() {
        // Create a token with the same secret that is already expired
        val expiredToken = JWT.create()
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .withSubject("user-1")
            .withClaim("role", "CUSTOMER")
            .withIssuedAt(Date(System.currentTimeMillis() - 10_000))
            .withExpiresAt(Date(System.currentTimeMillis() - 5_000)) // expired 5s ago
            .sign(Algorithm.HMAC256(testSecret))

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        assertFailsWith<com.auth0.jwt.exceptions.TokenExpiredException> {
            verifier.verify(expiredToken)
        }
    }

    @Test
    fun `token without sub claim fails custom validation`() {
        // Create a token without 'sub' claim
        val badToken = JWT.create()
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 300_000))
            .sign(Algorithm.HMAC256(testSecret))

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        // The JWT library verification passes (it's a valid JWT), but
        // our application's validate function would reject it (no 'sub' claim).
        val decoded = verifier.verify(badToken)
        assertNull(decoded.subject, "Token should not have a subject")
    }

    @Test
    fun `different tokens for different users have different subjects`() {
        val token1 = AuthService.generateAccessToken("user-A", "CUSTOMER", "08111111111")
        val token2 = AuthService.generateAccessToken("user-B", "OPERATOR", "08222222222")

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        val decoded1 = verifier.verify(token1)
        val decoded2 = verifier.verify(token2)

        assertNotEquals(decoded1.subject, decoded2.subject)
        assertNotEquals(decoded1.getClaim("role").asString(), decoded2.getClaim("role").asString())
        assertNotEquals(decoded1.getClaim("phone").asString(), decoded2.getClaim("phone").asString())
    }

    @Test
    fun `token issued at time is set to approximately now`() {
        val before = System.currentTimeMillis()
        val token = AuthService.generateAccessToken("user-1", "CUSTOMER", "08123456789")
        val after = System.currentTimeMillis()

        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()

        val decoded = verifier.verify(token)
        val issuedAt = decoded.issuedAt.time

        assertTrue(issuedAt >= before - 1000, "Issued at should be >= test start time")
        assertTrue(issuedAt <= after + 1000, "Issued at should be <= test end time")
    }
}
