package com.loudless.database

import com.loudless.config.AppConfigLoader
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseBackupCommandTest {
    @AfterTest
    fun tearDown() {
        DatabaseManager.close()
        System.clearProperty("DATABASE_PATH")
        System.clearProperty("DATABASE_BACKUP_PATH")
        System.clearProperty("JWT_SECRET_KEY")
        System.clearProperty("HA_MODE")
        AppConfigLoader.reset()
    }

    @Test
    fun `backup command creates timestamped zip`() {
        val root = Files.createTempDirectory("ktor-backup-test")
        val databasePath = root.resolve("db")
        val backupPath = root.resolve("backups")
        System.setProperty("DATABASE_PATH", databasePath.toString())
        System.setProperty("DATABASE_BACKUP_PATH", backupPath.toString())
        System.setProperty("JWT_SECRET_KEY", "test-secret")

        DatabaseManager.init()
        DatabaseManager.close()

        main()

        val backups = Files.list(backupPath).use { files ->
            files.filter { it.fileName.toString().matches(Regex("ktor-framework-h2-backup-\\d{8}-\\d{6}\\.zip")) }
                .toList()
        }
        assertEquals(1, backups.size)
        assertTrue(backups[0].exists())
    }
}
