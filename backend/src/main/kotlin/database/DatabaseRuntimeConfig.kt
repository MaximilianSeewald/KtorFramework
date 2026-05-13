package com.loudless.database

import com.loudless.config.AppConfig
import com.loudless.config.AppConfigLoader
import com.loudless.homeassistant.HomeAssistantMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.name

data class ResolvedDatabaseConfig(
    val databasePath: Path,
    val backupPath: Path,
    val h2Mode: String,
) {
    val jdbcUrl: String
        get() = buildString {
            append("jdbc:h2:file:")
            append(databasePath)
            if (h2Mode.isNotBlank()) {
                append(";")
                append(h2Mode)
            }
        }
}

object DatabaseRuntimeConfig {
    private val developmentDefaultPath = Path.of("./data/db")
    private val allowedH2Modes = setOf("", "AUTO_SERVER=TRUE")

    fun resolve(config: AppConfig = AppConfigLoader.load()): ResolvedDatabaseConfig {
        val isDevelopment = config.appEnvironment.equals("development", ignoreCase = true)
        val rawDatabasePath = config.databasePath ?: if (isDevelopment) developmentDefaultPath.toString() else null
        val databasePath = resolveDatabasePath(rawDatabasePath, isDevelopment)
        val backupPath = resolveBackupPath(config.databaseBackupPath, databasePath, isDevelopment)
        val h2Mode = validateH2Mode(config.h2Mode)

        return ResolvedDatabaseConfig(databasePath, backupPath, h2Mode)
    }

    private fun resolveDatabasePath(rawPath: String?, isDevelopment: Boolean): Path {
        if (rawPath.isNullOrBlank()) {
            throw IllegalStateException("DATABASE_PATH must be set when APP_ENV=production")
        }

        val configuredPath = Path.of(rawPath)
        if (!isDevelopment && !configuredPath.isAbsolute) {
            throw IllegalStateException("DATABASE_PATH must be absolute when APP_ENV=production")
        }

        val absolutePath = configuredPath.toAbsolutePath().normalize()
        if (!isDevelopment && isUnderUnsafeProductionDirectory(absolutePath)) {
            throw IllegalStateException("DATABASE_PATH must not point inside the application working directory or repository when APP_ENV=production")
        }

        validateWritableParent(absolutePath.parent ?: throw IllegalStateException("DATABASE_PATH must have a parent directory"))
        return absolutePath
    }

    private fun resolveBackupPath(rawPath: String?, databasePath: Path, isDevelopment: Boolean): Path {
        val configuredBackupPath = rawPath
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
            ?: if (HomeAssistantMode.enabled) Path.of("/data/backups") else databasePath.parent.resolve("backups")

        if (!isDevelopment && !configuredBackupPath.isAbsolute) {
            throw IllegalStateException("DATABASE_BACKUP_PATH must be absolute when APP_ENV=production")
        }

        val absoluteBackupPath = configuredBackupPath.toAbsolutePath().normalize()
        if (!isDevelopment && isUnderUnsafeProductionDirectory(absoluteBackupPath)) {
            throw IllegalStateException("DATABASE_BACKUP_PATH must not point inside the application working directory or repository when APP_ENV=production")
        }

        return absoluteBackupPath
    }

    private fun validateH2Mode(h2Mode: String): String {
        val normalizedMode = h2Mode.trim().uppercase()
        if (normalizedMode !in allowedH2Modes) {
            throw IllegalStateException("H2_MODE must be empty or one of: ${allowedH2Modes.filter { it.isNotBlank() }.joinToString()}")
        }
        return normalizedMode
    }

    fun validateWritableParent(parent: Path) {
        parent.createDirectories()
        validateWritableDirectory(parent)
    }

    fun validateWritableDirectory(directory: Path) {
        directory.createDirectories()
        if (!directory.isDirectory()) {
            throw IllegalStateException("${directory.toAbsolutePath().normalize()} is not a directory")
        }
        val probe = directory.resolve(".ktor-write-test-${System.nanoTime()}")
        try {
            Files.writeString(probe, "ok")
        } finally {
            Files.deleteIfExists(probe)
        }
    }

    private fun isUnderUnsafeProductionDirectory(path: Path): Boolean {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        val repoRoot = sequenceOf(workingDirectory, workingDirectory.parent)
            .filterNotNull()
            .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) || Files.exists(it.resolve(".git")) }

        return path == workingDirectory
            || path.startsWith(workingDirectory)
            || repoRoot?.let { path == it || path.startsWith(it) } == true
    }

    fun databaseName(databasePath: Path): String = databasePath.name
}
