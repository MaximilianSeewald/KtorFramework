package com.loudless.shoppingList

import com.loudless.models.ShoppingListItem
import com.loudless.shared.GenericManager
import com.loudless.shared.JwtUtil
import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
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
        webSocket("/shoppingListWS") {
            LOGGER.info("Handling shopping list websocket connection")
            val token = call.request.queryParameters["token"]
            val decodedJWT = JwtUtil.validateWebSocketToken(this, token) ?: return@webSocket
            
            val groups = UserService.getUserGroupsByQuery(decodedJWT)
            val userName = JwtUtil.getUsername(decodedJWT)
            
            if (groups.isEmpty() || groups.contains("")) {
                LOGGER.warn("Rejected shopping list websocket because user has no group")
                return@webSocket
            }
            
            var flow = MutableSharedFlow<List<ShoppingListItem>>(replay = 1)
            if (observerList[userName] == null) {
                observerList[userName] = flow
            } else {
                flow = observerList[userName]!!
            }
            
            val job = launch {
                flow.collect { items ->
                    send(Json.encodeToString(items))
                }
            }
            
            runCatching {
                send(Json.encodeToString(ShoppingListService.retrieveItems(groups[0])))
                incoming.consumeEach { }
            }.onFailure {
                LOGGER.error("Shopping list websocket failed for user {}", userName, it)
                observerList.remove(userName)
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, it.message ?: ""))
                job.cancel()
            }.also {
                observerList.remove(userName)
                job.cancel()
                LOGGER.info("Shopping list websocket closed for user {}", userName)
            }
        }
    }

    private fun Route.getShoppingList() {
        get("/shoppingList") {
            LOGGER.info("Handling get shopping list request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@get
            call.respondText(
                text = Json.encodeToString(ShoppingListService.retrieveItems(groups[0])),
                contentType = ContentType.Application.Json
            )
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
