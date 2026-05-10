package com.loudless

import com.loudless.auth.JwtService
import com.loudless.config.BackendConfig
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import kotlin.time.Duration.Companion.seconds

class KtorManager {
    private val LOGGER = LoggerFactory.getLogger(KtorManager::class.java)

    fun installComponents(application: Application) {
        LOGGER.info("Installing Ktor components")
        application.install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        application.install(CallLogging) {
            level = Level.INFO
            disableDefaultColors()
            disableForStaticContent()
            format { call ->
                val status = call.response.status()?.value ?: "unhandled"
                val method = call.request.httpMethod.value
                val path = call.request.path()
                val duration = call.processingTimeMillis()
                "$method $path -> $status in ${duration}ms"
            }
        }
        application.install(ContentNegotiation) {
            json()
        }
        application.install(CORS) {
            val allowedOrigins = BackendConfig.corsAllowedOrigins
            if (allowedOrigins.isEmpty()) {
                anyHost()
            } else {
                allowedOrigins.forEach { origin ->
                    val uri = URI(origin)
                    val host = uri.host ?: origin
                    val port = if (uri.port >= 0) ":${uri.port}" else ""
                    val schemes = uri.scheme?.let { listOf(it) } ?: listOf("http", "https")
                    allowHost("$host$port", schemes = schemes)
                }
            }
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Accept)
            allowNonSimpleContentTypes = true
            maxAgeInSeconds = 3600
            allowCredentials = true

        }
        application.install(Authentication) {
            jwt("auth-jwt") {
                realm = BackendConfig.jwtRealm
                verifier(JwtService.verifier())
                validate { credential ->
                    if (credential.payload.audience.contains(BackendConfig.jwtAudience)) {
                        JWTPrincipal(credential.payload)
                    } else {
                        LOGGER.warn("Rejected JWT with invalid audience")
                        null
                    }
                }
            }
        }
        LOGGER.info("Ktor components installed")
    }
}
