package com.loudless.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

data class AppConfig(
    val appEnvironment: String = "production",
    val corsAllowedOrigins: List<String> = emptyList(),
)

object AppConfigLoader {
    var configPath: Path = defaultConfigPath()

    fun load(path: Path = configPath): AppConfig {
        if (!Files.exists(path)) {
            return AppConfig()
        }

        val properties = Properties()
        Files.newInputStream(path).use { properties.load(it) }

        return AppConfig(
            appEnvironment = properties.getProperty("APP_ENV")
                ?.trim()
                ?.ifEmpty { null }
                ?: "production",
            corsAllowedOrigins = properties.getProperty("CORS_ALLOWED_ORIGINS")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
        )
    }

    fun reset() {
        configPath = defaultConfigPath()
    }

    private fun defaultConfigPath(): Path {
        return listOf(
            Path.of("config.properties"),
            Path.of("..", "config.properties"),
        ).firstOrNull { Files.exists(it) } ?: Path.of("config.properties")
    }
}
