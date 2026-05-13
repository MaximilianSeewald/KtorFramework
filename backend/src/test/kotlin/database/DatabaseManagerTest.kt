package com.loudless.database

import com.loudless.config.AppConfig
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DatabaseManagerTest {
    @AfterTest
    fun tearDown() {
        DatabaseManager.close()
    }

    @Test
    fun `hash password does not store plain text`() {
        val hashedPassword = DatabaseManager.hashPassword("correct-password")

        assertNotEquals("correct-password", hashedPassword)
    }

    @Test
    fun `verify password accepts matching password`() {
        val hashedPassword = DatabaseManager.hashPassword("correct-password")

        assertTrue(DatabaseManager.verifyPassword("correct-password", hashedPassword))
    }

    @Test
    fun `verify password rejects non matching password`() {
        val hashedPassword = DatabaseManager.hashPassword("correct-password")

        assertFalse(DatabaseManager.verifyPassword("wrong-password", hashedPassword))
    }

    @Test
    fun `production database path must be explicit and absolute`() {
        assertFailsWith<IllegalStateException> {
            DatabaseRuntimeConfig.resolve(AppConfig(appEnvironment = "production"))
        }

        assertFailsWith<IllegalStateException> {
            DatabaseRuntimeConfig.resolve(AppConfig(appEnvironment = "production", databasePath = "./data/db"))
        }
    }

    @Test
    fun `development database path allows local default`() {
        val resolved = DatabaseRuntimeConfig.resolve(AppConfig(appEnvironment = "development"))

        assertTrue(resolved.databasePath.endsWith("data/db"))
    }

    @Test
    fun `database config resolves paths and h2 mode`() {
        val root = Files.createTempDirectory("ktor-database-config-test")
        val resolved = DatabaseRuntimeConfig.resolve(
            AppConfig(
                appEnvironment = "production",
                databasePath = root.resolve("db").toString(),
                databaseBackupPath = root.resolve("backups").toString(),
                h2Mode = "AUTO_SERVER=TRUE",
            )
        )

        assertEquals(root.resolve("db").toAbsolutePath().normalize(), resolved.databasePath)
        assertEquals(root.resolve("backups").toAbsolutePath().normalize(), resolved.backupPath)
        assertTrue(resolved.jdbcUrl.endsWith(";AUTO_SERVER=TRUE"))
    }

    @Test
    fun `close is idempotent`() {
        DatabaseManager.close()
        DatabaseManager.close()
    }
}
