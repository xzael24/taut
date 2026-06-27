package com.taut.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.taut.config.*
import com.taut.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val securityLog = LoggerFactory.getLogger("com.taut.plugins.Security")

fun Application.configureSecurity(config: AppConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.jwt.audience
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwt.secret))
                    .withIssuer(config.jwt.issuer)
                    .withAudience(config.jwt.audience)
                    .acceptLeeway(60)  // Token expiration grace period
                    .build()
            )
            validate { credential ->
                val payload = credential.payload
                // Validate required claims (JTI, IATA) - FIXED: Added expiration validation
                if (payload.getClaim("sub").asString() != null &&
                    payload.getClaim("jti").asString() != null &&
                    payload.getClaim("iat").asLong() != null &&
                    payload.getClaim("nbf").asLong() != null &&
                    payload.getClaim("exp").asLong() != null) {
                    // Additional expiration validation
                    val exp = payload.getClaim("exp").asLong() * 1000  // Convert to milliseconds
                    val now = System.currentTimeMillis()
                    if (exp > now) {  // Token not expired
                        JWTPrincipal(payload)
                    } else {
                        securityLog.warn("JWT validation failed: token expired at ${exp}, current time: $now")
                        null
                    }
                } else {
                    securityLog.warn("JWT validation failed: missing required claims (sub, jti, iat, nbf, exp)")
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "UNAUTHORIZED", "message" to "Token tidak valid atau sudah kedaluwarsa.")
                )
            }
        }
    }
}
