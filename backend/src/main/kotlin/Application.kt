package com.loudless

import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        routing {
            singlePageApplication {
                angular("app/browser")
            }
        }
    }.start(wait = true)
}