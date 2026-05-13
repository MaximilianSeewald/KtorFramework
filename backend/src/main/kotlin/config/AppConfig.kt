package com.loudless.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

data class AppConfig(
    val appEnvironment: String = "production",
    val corsAllowedOrigins: List<String> = emptyList(),
    val databasePath: String? = null,
    val databaseBackupPath: String? = null,
    val h2Mode: String = "",
)

object AppConfigLoader {
    var configPath: Path = defaultConfigPath()

    fun load(path: Path = configPath): AppConfig {
        val properties = Properties()
        if (Files.exists(path)) {
            Files.newInputStream(path).use { properties.load(it) }
        }

        return AppConfig(
            appEnvironment = runtimeValue(properties, "APP_ENV")
                ?.trim()
                ?.ifEmpty { null }
                ?: "production",
            corsAllowedOrigins = runtimeValue(properties, "CORS_ALLOWED_ORIGINS")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
            databasePath = runtimeValue(properties, "DATABASE_PATH")
                ?.trim()
                ?.ifEmpty { null }
                ?: System.getProperty("ktor.database.path")
                    ?.trim()
                    ?.ifEmpty { null },
            databaseBackupPath = runtimeValue(properties, "DATABASE_BACKUP_PATH")
                ?.trim()
                ?.ifEmpty { null },
            h2Mode = runtimeValue(properties, "H2_MODE")
                ?.trim()
                ?.ifEmpty { null }
                ?: "",
        )
    }

    private fun runtimeValue(properties: Properties, key: String): String? {
        return System.getProperty(key)
            ?: System.getenv(key)
            ?: properties.getProperty(key)
    }

    fun reset() {
        configPath = defaultConfigPath()
    }

    private fun defaultConfigPath(): Path {
        return discoverConfigPath(Path.of("").toAbsolutePath())
    }

    internal fun discoverConfigPath(workingDirectory: Path): Path {
        return listOf(
            workingDirectory.resolve("config.properties"),
            workingDirectory.resolve("..").resolve("config.properties").normalize(),
        ).firstOrNull { Files.exists(it) } ?: Path.of("config.properties")
    }
}
