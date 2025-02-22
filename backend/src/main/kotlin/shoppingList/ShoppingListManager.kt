package com.loudless.shoppingList

import com.loudless.users.UserService.getUserGroups
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class ShoppingListManager {

    fun initRoutes(route: Route) {
        route.getShoppingList()
        route.putShoppingList()
        route.postShoppingList()
        route.deleteShoppingList()
    }

    private suspend fun retrieveUserGroupsAndHandleErrors(call: RoutingCall): List<String> {
        val groups = getUserGroups(call)
        if (groups.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "No user found with this username")
            return emptyList()
        }
        if (groups.size > 1) {
            call.respond(HttpStatusCode.BadRequest, "Too many users found")
            return emptyList()
        }
        return groups
    }

    private fun Route.getShoppingList() {
        get("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if(groups.isEmpty()) return@get
            call.respondText(
                text = Json.encodeToString(ShoppingListService.retrieveItems(groups[0])),
                contentType = ContentType.Application.Json
            )
        }
    }

    private fun Route.postShoppingList() {
        post("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@post
            when (ShoppingListService.addItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, "Couldn't add shopping list item")
            }
        }
    }

    private fun Route.putShoppingList() {
        put("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@put
            when (ShoppingListService.editItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, "Item id does not exist")
            }
        }
    }

    private fun Route.deleteShoppingList() {
        delete("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@delete
            when (ShoppingListService.deleteItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, "Item id is not unique")
            }
        }
    }
}