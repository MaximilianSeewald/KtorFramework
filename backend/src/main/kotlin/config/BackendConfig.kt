package com.loudless.config

object BackendConfig {
    const val jwtAudience = "ktor-app"
    const val jwtIssuer = "ktor-auth"
    const val jwtRealm = "Ktor Server"
    private const val defaultJwtTokenTtlMs = 1000000000L
    private const val defaultRateLimitWindowSeconds = 60L
    private const val defaultRateLimitMaxRequests = 120

    private val localDevelopmentCorsOrigins = listOf(
        "http://localhost:4200",
        "http://127.0.0.1:4200",
        "http://localhost:4201",
        "http://127.0.0.1:4201",
    )

    val jwtSecret: String
        get() = System.getProperty("JWT_SECRET_KEY")
            ?: System.getenv("JWT_SECRET_KEY")
            ?: throw IllegalStateException("JWT_SECRET_KEY not set")

    val jwtTokenTtlMs: Long
        get() = System.getProperty("JWT_TOKEN_TTL_MS")?.toLongOrNull()
            ?: System.getenv("JWT_TOKEN_TTL_MS")?.toLongOrNull()
            ?: defaultJwtTokenTtlMs

    val appEnvironment: String
        get() = AppConfigLoader.load().appEnvironment

    val corsAllowedOrigins: List<String>
        get() = AppConfigLoader.load().corsAllowedOrigins

    val corsOriginsForRuntime: List<String>
        get() = corsAllowedOrigins.ifEmpty {
            if (isDevelopment) localDevelopmentCorsOrigins else emptyList()
        }

    val isDevelopment: Boolean
        get() = appEnvironment.equals("development", ignoreCase = true)

    val swaggerEnabled: Boolean
        get() = isDevelopment

    const val webSocketMaxFrameSize: Long = 1024L * 1024L

    val rateLimitWindowSeconds: Long
        get() = AppConfigLoader.load().rateLimitWindowSeconds
            ?.takeIf { it > 0 }
            ?: defaultRateLimitWindowSeconds

    val rateLimitMaxRequests: Int
        get() = AppConfigLoader.load().rateLimitMaxRequests
            ?.takeIf { it > 0 }
            ?: defaultRateLimitMaxRequests

    fun validateStartupSecurity() {
        val secret = jwtSecret
        if (!isDevelopment && isWeakJwtSecret(secret)) {
            throw IllegalStateException("JWT_SECRET_KEY must be at least 32 characters and must not use the packaged placeholder")
        }
    }

    fun isWeakJwtSecret(secret: String): Boolean {
        return secret.length < 32
    }
}
