package com.loudless

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

suspend fun main() {
    DatabaseManager.init()
    val secretKey = System.getenv("JWT_SECRET_KEY") ?: throw IllegalStateException("JWT_SECRET_KEY not set")
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            anyHost()  // Allow requests from any origin (use with caution in production)
            allowMethod(HttpMethod.Get)  // Allow GET method
            allowMethod(HttpMethod.Post)  // Allow POST method
            allowMethod(HttpMethod.Options)  // Make sure OPTIONS method is allowed
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Accept) // Allow Content-Type header
            allowNonSimpleContentTypes = true  // Allow non-simple content types (like JSON)
            maxAgeInSeconds = 3600  // Allow the browser to cache the preflight response for an hour
            allowCredentials = true  // Allow cookies or authentication information

        }
        install(Authentication) {
            jwt("auth-jwt") {
                realm = "Ktor Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(secretKey))
                        .withAudience("ktor-app")
                        .withIssuer("ktor-auth")
                        .build()
                )
                validate { credential ->
                    if (credential.payload.audience.contains("ktor-app")) JWTPrincipal(credential.payload) else null
                }
            }
        }
        routing {
            singlePageApplication {
                angular("app/browser")
            }
            post("/login") {
                val parameters = call.receiveParameters()
                val username: String = parameters["username"] ?: ""
                val password: String = parameters["password"] ?: ""
                val user = transaction {
                    DatabaseManager.Users
                        .selectAll().where { DatabaseManager.Users.name eq username}
                        .map { it[DatabaseManager.Users.hashedPassword] to it[DatabaseManager.Users.name] }
                        .firstOrNull()
                }

                if (user != null && UserService.verifyPassword(password, user.first)) {
                    val token = JWT.create()
                        .withAudience("ktor-app")
                        .withIssuer("ktor-auth")
                        .withClaim("username", username)
                        .sign(Algorithm.HMAC256(secretKey))
                    call.respond(HttpStatusCode.OK, mapOf("token" to token))
                } else {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }

            authenticate("auth-jwt") {
                route("/user") {
                    get {
                        val principal = call.principal<JWTPrincipal>()
                        call.respond(mapOf("username" to principal?.payload?.getClaim("username")?.asString()))
                    }
                }
            }
        }
    }.start(wait = true)
}

