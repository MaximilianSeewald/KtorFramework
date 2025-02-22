package com.loudless

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*


class ShoppingListManager {

    private fun getUserGroups(call: ApplicationCall): List<String> {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        val groups = transaction {
            DatabaseManager.Users.selectAll().where { DatabaseManager.Users.name eq username }
                .map { it[DatabaseManager.Users.group] }
        }
        return groups
    }

    fun Route.getShoppingList() {
        get("/shoppingList") {
            val groups = getUserGroups(call)
            if (groups.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No user found with this username")
                return@get
            }
            if (groups.size > 1) {
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
                } ?: mutableListOf(ShoppingListItem("Hallo", "Super", "1234"))
            }
            call.respondText(
                text = Json.encodeToString(shoppingListDataMap),
                contentType = ContentType.Application.Json
            )
        }
    }

    fun Route.putShoppingList() {
        put("/shoppingList") {
            val groups = getUserGroups(call)
            if (groups.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No user found with this username")
                return@put
            }
            if (groups.size > 1) {
                call.respond(HttpStatusCode.BadRequest, "Too many users found")
                return@put
            }
            val userGroup = groups[0]
            val shoppingListTable = DatabaseManager.shoppingListMap[userGroup]
            if (shoppingListTable == null) {
                call.respond(HttpStatusCode.BadRequest, "No shopping list found for this user")
                return@put
            }
            val updateData = call.receive<ShoppingListItem>()

            val updateResult = transaction {
                val item = shoppingListTable
                    .selectAll()
                    .where { shoppingListTable.id eq UUID.fromString(updateData.id) }
                    .map { it[shoppingListTable.id] }

                if (item.isEmpty()) {
                    shoppingListTable.insert {
                        it[id] = UUID.fromString(updateData.id)
                        it[name] = updateData.name
                        it[amount] = updateData.amount
                    }
                    "inserted"
                } else if (item.size == 1) {
                    shoppingListTable.update({ shoppingListTable.id eq UUID.fromString(updateData.id) }) {
                        it[name] = updateData.name
                        it[amount] = updateData.amount
                    }
                    "updated"
                } else {
                    "too many items"
                }
            }
            when (updateResult) {
                "inserted" -> call.respond(HttpStatusCode.OK, "Shopping list item successfully inserted")
                "updated" -> call.respond(HttpStatusCode.OK, "Shopping list item successfully updated")
                "too many items" -> call.respond(HttpStatusCode.BadRequest, "Item id is not unique")
            }
        }
    }

    fun Route.deleteShoppingList() {
        delete("/shoppingList") {
            val groups = getUserGroups(call)
            if (groups.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No user found with this username")
                return@delete
            }
            if (groups.size > 1) {
                call.respond(HttpStatusCode.BadRequest, "Too many users found")
                return@delete
            }
            val userGroup = groups[0]
            val shoppingListTable = DatabaseManager.shoppingListMap[userGroup]
            if (shoppingListTable == null) {
                call.respond(HttpStatusCode.BadRequest, "No shopping list found for this user")
                return@delete
            }
            val id = call.request.queryParameters["id"]

            val deleteResult = transaction {
                val item = shoppingListTable
                    .selectAll()
                    .where { shoppingListTable.id eq UUID.fromString(id) }
                    .map { it[shoppingListTable.id] }

                if (item.isEmpty()) {
                    "empty"
                } else if (item.size == 1) {
                    shoppingListTable.deleteWhere {
                        shoppingListTable.id eq UUID.fromString(id)
                    }
                    "deleted"
                } else {
                    "too many items"
                }
            }
            when (deleteResult) {
                "empty" -> call.respond(HttpStatusCode.OK, "Shopping list item already deleted")
                "deleted" -> call.respond(HttpStatusCode.OK, "Shopping list item successfully deleted")
                "too many items" -> call.respond(HttpStatusCode.BadRequest, "Item id is not unique")
            }
        }
    }
}