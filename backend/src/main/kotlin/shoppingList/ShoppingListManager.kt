package com.loudless.shoppingList

import com.loudless.models.ShoppingListItem
import com.loudless.shared.GenericManager
import com.loudless.shared.JwtUtil
import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ShoppingListManager : GenericManager<ShoppingListItem>() {
    private val LOGGER = LoggerFactory.getLogger(ShoppingListManager::class.java)

    fun initRoutes(route: Route) {
        route.getShoppingList()
        route.putShoppingList()
        route.postShoppingList()
        route.deleteShoppingList()
    }

    fun initQueryRoutes(route: Route) {
        route.webSocketShoppingList()
    }

    private fun Route.webSocketShoppingList() {
        webSocketResource(
            path = "/shoppingListWS",
            resourceName = "shopping list",
            retrieveData = { ShoppingListService.retrieveItems(it) },
            serializeData = { Json.encodeToString(it) }
        )
    }

    private fun Route.getShoppingList() {
        get("/shoppingList") {
            LOGGER.info("Handling get shopping list request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@get
            call.respond(ShoppingListService.retrieveItems(groups[0]))
            LOGGER.info("Returned shopping list for group {}", groups[0])
        }
    }

    private fun Route.postShoppingList() {
        post("/shoppingList") {
            LOGGER.info("Handling add shopping list item request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@post
            when (ShoppingListService.addItem(call, groups[0])) {
                true -> {
                    call.respond(HttpStatusCode.OK)
                    LOGGER.info("Added shopping list item for group {}", groups[0])
                }
                false -> {
                    LOGGER.warn("Rejected add shopping list item for group {}", groups[0])
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Couldn't add shopping list item"))
                }
            }
            emitUpdate(groups) { ShoppingListService.retrieveItems(it) }
        }
    }

    private fun Route.putShoppingList() {
        put("/shoppingList") {
            LOGGER.info("Handling edit shopping list item request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@put
            when (ShoppingListService.editItem(call, groups[0])) {
                true -> {
                    call.respond(HttpStatusCode.OK)
                    LOGGER.info("Edited shopping list item for group {}", groups[0])
                }
                false -> {
                    LOGGER.warn("Rejected edit shopping list item for group {}", groups[0])
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Item id does not exist"))
                }
            }
            emitUpdate(groups) { ShoppingListService.retrieveItems(it) }
        }
    }

    private fun Route.deleteShoppingList() {
        delete("/shoppingList") {
            LOGGER.info("Handling delete shopping list item request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@delete
            if (call.request.queryParameters["id"].isNullOrBlank()) {
                LOGGER.warn("Rejected delete shopping list item because id query parameter was missing")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Item id is missing"))
                return@delete
            }
            when (ShoppingListService.deleteItem(call, groups[0])) {
                true -> {
                    call.respond(HttpStatusCode.OK)
                    LOGGER.info("Deleted shopping list item for group {}", groups[0])
                }
                false -> {
                    LOGGER.warn("Rejected delete shopping list item for group {}", groups[0])
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Item id is not unique"))
                }
            }
            emitUpdate(groups) { ShoppingListService.retrieveItems(it) }
        }
    }
}
