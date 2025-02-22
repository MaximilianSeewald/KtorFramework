package com.loudless


import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*


suspend fun main() {
    DatabaseManager.init()
    embeddedServer(Netty, port = 8080) {
        val ktorManager = KtorManager()
        ktorManager.installComponents(this)
        routing {
            singlePageApplication {
                angular("app/browser")
            }
            ktorManager.initRouting(this)
            authenticate("auth-jwt") {
                get("/verify") {
                    call.respond(mapOf("valid" to true))
                }
                ShoppingListManager().apply { getShoppingList() }
                ShoppingListManager().apply { putShoppingList() }
                ShoppingListManager().apply { deleteShoppingList() }
            }
        }
    }.start(wait = true)
}



