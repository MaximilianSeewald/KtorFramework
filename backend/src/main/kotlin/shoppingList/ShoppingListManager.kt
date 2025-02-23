package com.loudless.shoppingList

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import com.loudless.models.ShoppingListItem
import com.loudless.users.UserService.getUserGroupsByPrincipal
import com.loudless.users.UserService.getUserGroupsByQuery
import com.loudless.users.UserService.getUserNameByQuery
import com.loudless.users.UserService.getUsernameByPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class ShoppingListManager {

    private val observerList: MutableMap<String,MutableSharedFlow<List<ShoppingListItem>>> = mutableMapOf()

    fun initRoutes(route: Route) {
        route.getShoppingList()
        route.putShoppingList()
        route.postShoppingList()
        route.deleteShoppingList()
    }

    fun initQueryRoutes(route: Route) {
       route.webSocketShoppingList()
    }

    private suspend fun retrieveUserGroupsAndHandleErrors(call: ApplicationCall): List<String> {
        val groups = getUserGroupsByPrincipal(call)
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

    private fun Route.webSocketShoppingList() {
        webSocket("/shoppingListWS") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
                return@webSocket
            }
            val verifier = JWT.require(Algorithm.HMAC256(secretJWTKey))
                .withAudience("ktor-app")
                .withIssuer("ktor-auth")
                .build()

            val decodedJWT = try {
                verifier.verify(token)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
            val groups = getUserGroupsByQuery(decodedJWT)
            if(groups.isEmpty()) return@webSocket
            val userName = getUserNameByQuery(decodedJWT)
            val flow = MutableSharedFlow<List<ShoppingListItem>>(replay = 1)
            observerList[userName ] = flow
            val job = launch {
                flow.collect { shoppingList ->
                    send(Json.encodeToString(shoppingList))
                }
            }
            runCatching {
                incoming.consumeEach {  }
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
            val userName = getUsernameByPrincipal(call)
            if (groups.isEmpty()) return@post
            when (ShoppingListService.addItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, "Couldn't add shopping list item")
            }
            if(observerList[userName] != null) {
                observerList[userName]!!.tryEmit(ShoppingListService.retrieveItems(groups[0]))
            }
        }
    }

    private fun Route.putShoppingList() {
        put("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            val userName = getUsernameByPrincipal(call)
            if (groups.isEmpty()) return@put
            when (ShoppingListService.editItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, "Item id does not exist")
            }
            if(observerList[userName] != null) {
                observerList[userName]!!.tryEmit(ShoppingListService.retrieveItems(groups[0]))
            }
        }
    }

    private fun Route.deleteShoppingList() {
        delete("/shoppingList") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            val userName = getUsernameByPrincipal(call)
            if (groups.isEmpty()) return@delete
            when (ShoppingListService.deleteItem(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, "Item id is not unique")
            }
            if(observerList[userName] != null) {
                observerList[userName]!!.tryEmit(ShoppingListService.retrieveItems(groups[0]))
            }
        }
    }
}