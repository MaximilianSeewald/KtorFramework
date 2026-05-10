package com.loudless

import com.loudless.database.DatabaseManager
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
        install(io.ktor.server.plugins.forwardedheaders.XForwardedHeaders)
        SessionManager.installComponents(this)
        routing {

            route("/api") {
                SessionManager.initRouting(this)
                authenticate("auth-jwt") {
                    get("/verify") {
                        LOGGER.info("Verified authenticated API session")
                        call.respond(mapOf("valid" to true))
                    }
                    SessionManager.initSafeRoutes(this)
                }
            }

            staticFiles("/", File("app/browser")) {
                default("index.html")
            }
        }
    }
    LOGGER.info("Starting backend server on {}:{}", host, port)
    server.start(wait = true)
}
