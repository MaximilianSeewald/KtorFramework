package com.loudless

import com.loudless.database.DatabaseManager
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*


fun main() {
    DatabaseManager.init()
    val host = System.getenv("KTOR_HOST") ?: "0.0.0.0"
    val port = System.getenv("KTOR_PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, host = host, port = port) {
        install(io.ktor.server.plugins.forwardedheaders.XForwardedHeaders)
        SessionManager.installComponents(this)
        routing {

            route("/api") {
                SessionManager.initRouting(this)
                authenticate("auth-jwt") {
                    get("/verify") {
                        call.respond(mapOf("valid" to true))
                    }
                    SessionManager.initSafeRoutes(this)
                }
            }

            singlePageApplication {
                angular("app/browser")
            }
        }
    }.start(wait = true)
}
