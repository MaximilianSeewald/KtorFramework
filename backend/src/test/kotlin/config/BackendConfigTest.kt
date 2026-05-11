package com.loudless.config

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendConfigTest {
    @AfterTest
    fun tearDown() {
        System.clearProperty("JWT_SECRET_KEY")
        System.clearProperty("JWT_TOKEN_TTL_MS")
    }

    @Test
    fun `jwt secret can be read from system property`() {
        System.setProperty("JWT_SECRET_KEY", "unit-test-secret")

        assertEquals("unit-test-secret", BackendConfig.jwtSecret)
    }

    @Test
    fun `jwt secret fails clearly when no environment or property is present`() {
        System.clearProperty("JWT_SECRET_KEY")
        if (System.getenv("JWT_SECRET_KEY") != null) return

        assertFailsWith<IllegalStateException> {
            BackendConfig.jwtSecret
        }
    }

    @Test
    fun `jwt token ttl uses default when property is absent or invalid`() {
        System.clearProperty("JWT_TOKEN_TTL_MS")
        assertEquals(1000000000L, BackendConfig.jwtTokenTtlMs)

        System.setProperty("JWT_TOKEN_TTL_MS", "not-a-number")
        assertEquals(1000000000L, BackendConfig.jwtTokenTtlMs)
    }

    @Test
    fun `jwt token ttl can be configured from system property`() {
        System.setProperty("JWT_TOKEN_TTL_MS", "12345")

        assertEquals(12345L, BackendConfig.jwtTokenTtlMs)
    }

    @Test
    fun `cors allowed origins defaults to permissive empty list`() {
        assertEquals(emptyList(), BackendConfig.corsAllowedOrigins)
    }

    @Test
    fun `jwt constants preserve public token contract`() {
        assertEquals("ktor-app", BackendConfig.jwtAudience)
        assertEquals("ktor-auth", BackendConfig.jwtIssuer)
        assertEquals("Ktor Server", BackendConfig.jwtRealm)
    }
}
