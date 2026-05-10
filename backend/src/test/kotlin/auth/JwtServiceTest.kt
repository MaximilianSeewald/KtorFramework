package com.loudless.auth

import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.loudless.config.BackendConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtServiceTest {
    @AfterTest
    fun tearDown() {
        System.clearProperty("JWT_SECRET_KEY")
        System.clearProperty("JWT_TOKEN_TTL_MS")
    }

    @Test
    fun `created token contains expected backend claims`() {
        System.setProperty("JWT_SECRET_KEY", "jwt-unit-secret")
        System.setProperty("JWT_TOKEN_TTL_MS", "60000")

        val decoded = JwtService.verifyToken(JwtService.createToken("alice"))

        assertEquals("alice", decoded.getClaim("username").asString())
        assertEquals(BackendConfig.jwtIssuer, decoded.issuer)
        assertTrue(decoded.audience.contains(BackendConfig.jwtAudience))
    }

    @Test
    fun `token verifier rejects token signed with different secret`() {
        System.setProperty("JWT_SECRET_KEY", "first-secret")
        val token = JwtService.createToken("alice")

        System.setProperty("JWT_SECRET_KEY", "second-secret")

        assertFailsWith<JWTVerificationException> {
            JwtService.verifyToken(token)
        }
    }

    @Test
    fun `token verifier rejects expired token`() {
        System.setProperty("JWT_SECRET_KEY", "jwt-unit-secret")
        System.setProperty("JWT_TOKEN_TTL_MS", "-1000")

        val token = JwtService.createToken("alice")

        assertFailsWith<TokenExpiredException> {
            JwtService.verifyToken(token)
        }
    }

    @Test
    fun `auth token service creates jwt compatible with shared verifier`() {
        System.setProperty("JWT_SECRET_KEY", "auth-token-secret")

        val decoded = JwtService.verifyToken(AuthTokenService.createToken("bob"))

        assertEquals("bob", decoded.getClaim("username").asString())
    }
}
