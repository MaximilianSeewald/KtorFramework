package com.loudless.config

import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @AfterTest
    fun tearDown() {
        System.clearProperty("DATABASE_PATH")
        System.clearProperty("DATABASE_BACKUP_PATH")
        System.clearProperty("H2_MODE")
        System.clearProperty("ktor.database.path")
        AppConfigLoader.reset()
    }

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
            DATABASE_PATH=./data/db
            DATABASE_BACKUP_PATH=./data/backups
            H2_MODE=AUTO_SERVER=TRUE
            """.trimIndent()
        )

        val config = AppConfigLoader.load(configPath)

        assertEquals("development", config.appEnvironment)
        assertEquals(listOf("https://example.com", "https://www.example.com"), config.corsAllowedOrigins)
        assertEquals("./data/db", config.databasePath)
        assertEquals("./data/backups", config.databaseBackupPath)
        assertEquals("AUTO_SERVER=TRUE", config.h2Mode)
    }

    @Test
    fun `deprecated database system property is used as path alias`() {
        val databasePath = Files.createTempDirectory("ktor-config-db-test").resolve("db").toString()
        System.setProperty("ktor.database.path", databasePath)

        val config = AppConfigLoader.load(Files.createTempDirectory("ktor-config-test").resolve("missing.properties"))

        assertEquals(databasePath, config.databasePath)
        System.clearProperty("ktor.database.path")
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
