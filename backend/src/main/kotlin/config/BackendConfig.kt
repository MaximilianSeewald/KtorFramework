package com.loudless.config

object BackendConfig {
    const val jwtAudience = "ktor-app"
    const val jwtIssuer = "ktor-auth"
    const val jwtRealm = "Ktor Server"
    private const val defaultJwtTokenTtlMs = 1000000000L

    val jwtSecret: String
        get() = System.getProperty("JWT_SECRET_KEY")
            ?: System.getenv("JWT_SECRET_KEY")
            ?: throw IllegalStateException("JWT_SECRET_KEY not set")

    val jwtTokenTtlMs: Long
        get() = System.getProperty("JWT_TOKEN_TTL_MS")?.toLongOrNull()
            ?: System.getenv("JWT_TOKEN_TTL_MS")?.toLongOrNull()
            ?: defaultJwtTokenTtlMs

    val corsAllowedOrigins: List<String>
        get() = System.getenv("CORS_ALLOWED_ORIGINS")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
