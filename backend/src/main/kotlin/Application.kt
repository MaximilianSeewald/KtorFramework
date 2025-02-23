package com.loudless

import com.loudless.database.DatabaseManager
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*


suspend fun main() {
    DatabaseManager.init()
    embeddedServer(Netty, port = 8080) {
        SessionManager.installComponents(this)
        routing {
            singlePageApplication {
                angular("app/browser")
            }
            SessionManager.initRouting(this)
            authenticate("auth-jwt") {
                get("/verify") {
                    call.respond(mapOf("valid" to true))
                }
                SessionManager.initSafeRoutes(this)
            }
        }
    }.start(wait = true)
}



