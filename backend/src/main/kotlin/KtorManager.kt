package com.loudless

import com.loudless.auth.JwtService
import com.loudless.config.BackendConfig
import com.loudless.shared.SecurityHeaders
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.websocket.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val REQUEST_ID_HEADER = "X-Request-ID"

@Serializable
private data class ErrorResponse(
    val message: String,
    val requestId: String,
)

class KtorManager {
    private val LOGGER = LoggerFactory.getLogger(KtorManager::class.java)

    fun installComponents(application: Application) {
        LOGGER.info("Installing Ktor components")
        application.install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = BackendConfig.webSocketMaxFrameSize
            masking = false
        }
        application.install(CallId) {
            header(REQUEST_ID_HEADER)
            generate { UUID.randomUUID().toString() }
            replyToHeader(REQUEST_ID_HEADER)
        }
        application.install(ContentNegotiation) {
            json()
        }
        if (!BackendConfig.swaggerEnabled){
            application.install(SecurityHeaders)
        }
        application.install(StatusPages) {
            exception<Throwable> { call, cause ->
                val requestId = call.callId ?: "unknown"
                LOGGER.error(
                    "Unhandled request exception request_id={} method={} path={}",
                    requestId,
                    call.request.httpMethod.value,
                    call.request.path(),
                    cause
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(
                        message = "Internal server error",
                        requestId = requestId
                    )
                )
            }
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
                val requestId = call.callId ?: "unknown"
                "request_id=$requestId method=$method path=$path status=$status duration_ms=$duration"
            }
        }
        val allowedOrigins = BackendConfig.corsOriginsForRuntime
        if (allowedOrigins.isNotEmpty()) {
            application.install(CORS) {
                allowedOrigins.forEach { origin ->
                    val corsOrigin = parseCorsOrigin(origin)
                    allowHost(corsOrigin.hostWithPort, schemes = corsOrigin.schemes)
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

internal data class CorsOrigin(
    val hostWithPort: String,
    val schemes: List<String>,
)

internal fun parseCorsOrigin(origin: String): CorsOrigin {
    val hasScheme = origin.contains("://")
    val uriText = if (hasScheme) origin else "http://$origin"
    val uri = try {
        URI(uriText)
    } catch (exception: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid CORS origin '$origin'", exception)
    }

    val host = uri.host
        ?: throw IllegalArgumentException("Invalid CORS origin '$origin': host is required")
    val port = if (uri.port >= 0) ":${uri.port}" else ""
    val schemes = if (hasScheme) {
        listOf(uri.scheme)
    } else {
        listOf("http", "https")
    }

    return CorsOrigin("$host$port", schemes)
}
