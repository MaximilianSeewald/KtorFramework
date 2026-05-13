package com.loudless.database

import org.h2.tools.Backup
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists

private val LOGGER = LoggerFactory.getLogger("com.loudless.database.DatabaseBackupCommand")

fun main() {
    val resolvedConfig = DatabaseRuntimeConfig.resolve()
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val backupFile = resolvedConfig.backupPath.resolve("ktor-framework-h2-backup-$timestamp.zip")
    val databaseFile = resolvedConfig.databasePath.resolveSibling("${resolvedConfig.databasePath.fileName}.mv.db")

    DatabaseRuntimeConfig.validateWritableDirectory(resolvedConfig.backupPath)
    if (!databaseFile.exists()) {
        throw IllegalStateException("Database file does not exist: $databaseFile")
    }

    LOGGER.info("Creating H2 backup from {} into {}", resolvedConfig.databasePath, backupFile)
    Backup.execute(
        backupFile.toString(),
        resolvedConfig.databasePath.parent.toString(),
        DatabaseRuntimeConfig.databaseName(resolvedConfig.databasePath),
        true
    )
    LOGGER.info("Created database backup {}", backupFile)
}
