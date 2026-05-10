package com.loudless

import com.loudless.database.DatabaseManager
import com.loudless.models.VerifySessionResponse
import com.loudless.users.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import java.io.File
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("com.loudless.Application")

fun main() {
    LOGGER.info("Starting backend application")
    DatabaseManager.init()
    val host = System.getenv("KTOR_HOST") ?: "0.0.0.0"
    val port = System.getenv("KTOR_PORT")?.toIntOrNull() ?: 8080
    LOGGER.info("Backend configured to listen on {}:{}", host, port)

    val server = embeddedServer(Netty, host = host, port = port) {
        configureBackend()
    }
    LOGGER.info("Starting backend server on {}:{}", host, port)
    server.start(wait = true)
}

fun Application.configureBackend() {
    install(io.ktor.server.plugins.forwardedheaders.XForwardedHeaders)
    SessionManager.installComponents(this)
    routing {
        route("/api") {
            SessionManager.initRouting(this)
            authenticate("auth-jwt") {
                get("/verify") {
                    val user = UserService.findAuthenticatedUser(call)
                    if (user == null) {
                        LOGGER.warn("Rejected authenticated API session because token user was not found")
                        call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid session"))
                        return@get
                    }
                    LOGGER.info("Verified authenticated API session for user {}", user.id)
                    call.respond(VerifySessionResponse(valid = true, user = user))
                }
                SessionManager.initSafeRoutes(this)
            }
        }

        staticFiles("/", File("app/browser")) {
            default("index.html")
        }
    }
}
