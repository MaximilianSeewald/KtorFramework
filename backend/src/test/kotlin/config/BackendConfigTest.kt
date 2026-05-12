package com.loudless.config

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendConfigTest {
    @AfterTest
    fun tearDown() {
        System.clearProperty("JWT_SECRET_KEY")
        System.clearProperty("JWT_TOKEN_TTL_MS")
        AppConfigLoader.reset()
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
    fun `cors allowed origins defaults to empty list`() {
        assertEquals(emptyList(), BackendConfig.corsAllowedOrigins)
    }

    @Test
    fun `runtime cors origins default to local Angular origins in development`() {
        val configPath = Files.createTempFile("ktor-config-test", ".properties")
        Files.writeString(configPath, "APP_ENV=development")
        AppConfigLoader.configPath = configPath

        assertEquals(
            listOf(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://localhost:4201",
                "http://127.0.0.1:4201",
            ),
            BackendConfig.corsOriginsForRuntime
        )
    }

    @Test
    fun `runtime cors origins prefer explicit config file origins`() {
        val configPath = Files.createTempFile("ktor-config-test", ".properties")
        Files.writeString(
            configPath,
            "APP_ENV=development\nCORS_ALLOWED_ORIGINS=https://app.example.com"
        )
        AppConfigLoader.configPath = configPath

        assertEquals(listOf("https://app.example.com"), BackendConfig.corsOriginsForRuntime)
    }

    @Test
    fun `runtime cors origins are empty by default in production`() {
        AppConfigLoader.configPath = Files.createTempDirectory("ktor-config-test").resolve("missing.properties")

        assertEquals(emptyList(), BackendConfig.corsOriginsForRuntime)
    }

    @Test
    fun `startup security rejects weak production jwt secret`() {
        AppConfigLoader.configPath = Files.createTempDirectory("ktor-config-test").resolve("missing.properties")
        System.setProperty("JWT_SECRET_KEY", "short-secret")

        assertFailsWith<IllegalStateException> {
            BackendConfig.validateStartupSecurity()
        }
    }

    @Test
    fun `startup security allows weak jwt secret in development`() {
        val configPath = Files.createTempFile("ktor-config-test", ".properties")
        Files.writeString(configPath, "APP_ENV=development")
        AppConfigLoader.configPath = configPath
        System.setProperty("JWT_SECRET_KEY", "short-secret")

        BackendConfig.validateStartupSecurity()
    }

    @Test
    fun `jwt constants preserve public token contract`() {
        assertEquals("ktor-app", BackendConfig.jwtAudience)
        assertEquals("ktor-auth", BackendConfig.jwtIssuer)
        assertEquals("Ktor Server", BackendConfig.jwtRealm)
    }
}
