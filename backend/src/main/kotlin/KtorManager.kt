package com.loudless

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

class KtorManager {

    fun installComponents(application: Application) {
        application.install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        application.install(ContentNegotiation) {
            json()
        }
        application.install(CORS) {
            anyHost()  // Allow requests from any origin (use with caution in production)
            allowMethod(HttpMethod.Get)  // Allow GET method
            allowMethod(HttpMethod.Put)  // Allow PUT method
            allowMethod(HttpMethod.Post)  // Allow POST method
            allowMethod(HttpMethod.Delete)  // Allow DELETE method
            allowMethod(HttpMethod.Options)  // Make sure OPTIONS method is allowed
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Accept) // Allow Content-Type header
            allowNonSimpleContentTypes = true  // Allow non-simple content types (like JSON)
            maxAgeInSeconds = 3600  // Allow the browser to cache the preflight response for an hour
            allowCredentials = true  // Allow cookies or authentication information

        }
        application.install(Authentication) {
            jwt("auth-jwt") {
                realm = "Ktor Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(secretJWTKey))
                        .withAudience("ktor-app")
                        .withIssuer("ktor-auth")
                        .build()
                )
                validate { credential ->
                    if (credential.payload.audience.contains("ktor-app")) JWTPrincipal(credential.payload) else null
                }
            }
        }
    }
}