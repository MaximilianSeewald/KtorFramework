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

class ShoppingListManager : GenericManager<ShoppingListItem>() {

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
            val token = call.request.queryParameters["token"]
            val decodedJWT = JwtUtil.validateWebSocketToken(this, token) ?: return@webSocket
            
            val groups = UserService.getUserGroupsByQuery(decodedJWT)
            val userName = JwtUtil.getUsername(decodedJWT)
            
            if (groups.isEmpty() || groups.contains("")) return@webSocket
            
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
                observerList.remove(userName)
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, it.message ?: ""))
                job.cancel()
            }.also {
                observerList.remove(userName)
                job.cancel()
            }
        }
    }

    private fun Route.getShoppingList() {
        get("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@get
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
                false -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Couldn't add shopping list item"))
            }
            emitUpdate(groups) { ShoppingListService.retrieveItems(it) }
        }
    }

    private fun Route.putShoppingList() {
        put("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@put
            when (ShoppingListService.editItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Item id does not exist"))
            }
            emitUpdate(groups) { ShoppingListService.retrieveItems(it) }
        }
    }

    private fun Route.deleteShoppingList() {
        delete("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@delete
            when (ShoppingListService.deleteItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Item id is not unique"))
            }
            emitUpdate(groups) { ShoppingListService.retrieveItems(it) }
        }
    }
}