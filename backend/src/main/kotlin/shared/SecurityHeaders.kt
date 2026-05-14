package com.loudless.shared

import io.ktor.server.application.createApplicationPlugin

val SecurityHeaders = createApplicationPlugin(name = "SecurityHeaders") {
    onCall { call ->
        call.response.headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
        call.response.headers.append("Referrer-Policy", "same-origin", safeOnly = false)
        call.response.headers.append(
            "Content-Security-Policy",
            listOf(
                "default-src 'self'",
                "script-src 'self' 'unsafe-inline'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data:",
                "font-src 'self' data: https://fonts.gstatic.com",
                "connect-src 'self' ws: wss:",
                "object-src 'none'",
                "base-uri 'self'",
            ).joinToString("; "),
            safeOnly = false,
        )
    }
}
