package com.loudless.config

import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @Test
    fun `missing config file uses production defaults`() {
        val missingPath = Files.createTempDirectory("ktor-config-test").resolve("missing.properties")

        val config = AppConfigLoader.load(missingPath)

        assertEquals("production", config.appEnvironment)
        assertEquals(emptyList(), config.corsAllowedOrigins)
    }

    @Test
    fun `config file parses app environment and comma separated origins`() {
        val configPath = Files.createTempFile("ktor-config-test", ".properties")
        Files.writeString(
            configPath,
            """
            APP_ENV=development
            CORS_ALLOWED_ORIGINS=https://example.com, https://www.example.com, ,
            """.trimIndent()
        )

        val config = AppConfigLoader.load(configPath)

        assertEquals("development", config.appEnvironment)
        assertEquals(listOf("https://example.com", "https://www.example.com"), config.corsAllowedOrigins)
    }

    @Test
    fun `discovers config file one directory above working directory`() {
        val root = Files.createTempDirectory("ktor-config-parent-test")
        val backend = root.resolve("backend").createDirectory()
        Files.writeString(root.resolve("config.properties"), "APP_ENV=development")

        val configPath = AppConfigLoader.discoverConfigPath(backend)
        val config = AppConfigLoader.load(configPath)

        assertEquals("development", config.appEnvironment)
    }
}
