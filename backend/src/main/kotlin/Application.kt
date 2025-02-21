package com.loudless


import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction


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
                get("/shoppingList") {
                    val principal = call.principal<JWTPrincipal>()
                    val username = principal?.getClaim("username", String::class) ?: ""
                    val groups = transaction {
                        DatabaseManager.Users.selectAll().where { DatabaseManager.Users.name eq username }
                            .map { it[DatabaseManager.Users.group] }
                    }
                    if(groups.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "No user found with this username")
                        return@get
                    }
                    if(groups.size > 1) {
                        call.respond(HttpStatusCode.BadRequest, "Too many users found")
                        return@get
                    }
                    val userGroup = groups[0]
                    val shoppingListTable = DatabaseManager.shoppingListMap[userGroup]
                    val shoppingListDataMap = transaction {
                        shoppingListTable?.selectAll()?.map {
                            ShoppingListItem(
                                name = it[shoppingListTable.name],
                                amount = it[shoppingListTable.amount],
                                id = it[shoppingListTable.id].toString()
                            )
                        } ?: mutableListOf(ShoppingListItem("Hallo","Super","1234"))
                    }
                    call.respondText(text = Json.encodeToString(shoppingListDataMap), contentType = ContentType.Application.Json)
                }
            }
        }
    }.start(wait = true)
}



