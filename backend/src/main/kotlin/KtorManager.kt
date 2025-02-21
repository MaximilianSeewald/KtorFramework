package com.loudless

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.util.*

class KtorManager {

    private val secretKey = System.getenv("JWT_SECRET_KEY") ?: throw IllegalStateException("JWT_SECRET_KEY not set")
    private val env = System.getenv("KTOR_ENV") ?: "production"
    private val validityInMs = 10000

    fun initRouting(routing: Routing) {
        routing.uploadGrade()
        routing.login(validityInMs, secretKey)
    }

    private fun Routing.uploadGrade() {
        post("/upload") {
            val multipartData = call.receiveMultipart()
            var byteArrayContent: ByteArray? = null
            var points: String? = null
            multipartData.forEachPart { part ->
                if (part is PartData.FormItem) {
                    points = part.value
                }
                if (part is PartData.FileItem) {
                    byteArrayContent = part.provider().toByteArray()
                }
            }
            if (byteArrayContent == null) {
                call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }
            if (byteArrayContent != null) {
                val outputStream = ByteArrayOutputStream()
                GradeService.readFileAndWriteToStream(byteArrayContent!!, points, outputStream)
                call.respondBytes(
                    bytes = outputStream.toByteArray(),
                    contentType = ContentType.Application.Zip,
                    status = HttpStatusCode.OK
                )
            }
        }
    }

    private fun Routing.login(validityInMs: Int, secretKey: String) {
        post("/login") {
            val parameters = call.receiveParameters()
            val username: String = parameters["username"] ?: ""
            val password: String = parameters["password"] ?: ""
            val user = transaction {
                DatabaseManager.Users
                    .selectAll().where { DatabaseManager.Users.name eq username }
                    .map { it[DatabaseManager.Users.hashedPassword] to it[DatabaseManager.Users.name] }
                    .firstOrNull()
            }

            if (user != null && UserService.verifyPassword(password, user.first)) {
                val token = JWT.create()
                    .withAudience("ktor-app")
                    .withIssuer("ktor-auth")
                    .withClaim("username", username)
                    .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
                    .sign(Algorithm.HMAC256(secretKey))
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }

    fun installComponents(application: Application) {
        application.install(ContentNegotiation) {
            json()
        }
        if(env != "production") {
            application.install(CORS) {
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
        } else {
            application.install(CORS) {
                allowHost("https://maximilian-seewald.de")
                allowHost("https://nrg-esport.de")
                allowHost("https://powerful-salt.de")

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
        }
        application.install(Authentication) {
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
    }
}