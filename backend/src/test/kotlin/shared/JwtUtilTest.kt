package com.loudless.shared

import com.loudless.auth.JwtService
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtUtilTest {
    @AfterTest
    fun tearDown() {
        System.clearProperty("JWT_SECRET_KEY")
        System.clearProperty("JWT_TOKEN_TTL_MS")
    }

    @Test
    fun `verify token returns decoded token for valid jwt`() {
        System.setProperty("JWT_SECRET_KEY", "jwt-util-secret")
        val token = JwtService.createToken("carol")

        val decoded = JwtUtil.verifyToken(token)

        assertNotNull(decoded)
        assertEquals("carol", JwtUtil.getUsername(decoded))
    }

    @Test
    fun `verify token returns null for malformed jwt`() {
        System.setProperty("JWT_SECRET_KEY", "jwt-util-secret")

        assertNull(JwtUtil.verifyToken("not-a-token"))
    }

    @Test
    fun `verify token returns null when signature does not match current secret`() {
        System.setProperty("JWT_SECRET_KEY", "original-secret")
        val token = JwtService.createToken("carol")

        System.setProperty("JWT_SECRET_KEY", "different-secret")

        assertNull(JwtUtil.verifyToken(token))
    }
}
