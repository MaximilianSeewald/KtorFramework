package com.loudless.recipes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import com.loudless.models.Recipe
import com.loudless.users.UserService
import com.loudless.users.UserService.getUserGroupsByPrincipal
import com.loudless.users.UserService.getUserGroupsByQuery
import com.loudless.users.UserService.getUserNameByQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json


class RecipeManager {

    private val observerList: MutableMap<String,MutableSharedFlow<List<Recipe>>> = mutableMapOf()

    fun initRoutes(route: Route) {
        route.getRecipes()
        route.putRecipes()
        route.postRecipes()
        route.deleteRecipes()
    }

    fun initQueryRoutes(route: Route) {
        route.webSocketRecipes()
    }

    private suspend fun retrieveUserGroupsAndHandleErrors(call: ApplicationCall): List<String> {
        val groups = getUserGroupsByPrincipal(call)
        if (groups.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No user found with this username"))
            return emptyList()
        }
        if (groups.size > 1) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Too many users found"))
            return emptyList()
        }
        return groups
    }

    private fun Route.webSocketRecipes() {
        webSocket("/recipeWS") {
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
            } catch (_: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
            val groups = getUserGroupsByQuery(decodedJWT)
            val userName = getUserNameByQuery(decodedJWT)
            if(groups.isEmpty() || groups.contains("")) return@webSocket
            var flow = MutableSharedFlow<List<Recipe>>(replay = 1)
            if(observerList[userName] == null) {
                observerList[userName] = flow
            }else {
                flow = observerList[userName]!!
            }
            val job = launch {
                flow.collect { recipes ->
                    send(Json.encodeToString(recipes))
                }
            }
            runCatching {
                send(Json.encodeToString(RecipeService.retrieveRecipes(groups[0])))
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

    private fun Route.getRecipes() {
        get("/recipe") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if(groups.isEmpty()) return@get
            call.respondText(
                text = Json.encodeToString(RecipeService.retrieveRecipes(groups[0])),
                contentType = ContentType.Application.Json
            )
        }
    }

    private fun Route.postRecipes() {
        post("/recipe") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@post
            when (RecipeService.addRecipe(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Couldn't add recipe"))
            }
            emitUpdate(groups)
        }
    }

    private fun Route.putRecipes() {
        put("/recipe") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@put
            when (RecipeService.editRecipe(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Recipe id does not exist"))
            }
            emitUpdate(groups)
        }
    }

    private fun emitUpdate(groups: List<String>) {
        val usersForGroup = UserService.getUsersForGroup(groups[0])
        if (observerList.any { usersForGroup.contains(it.key) }) {
            observerList.filter { usersForGroup.contains(it.key) }.forEach {
                it.value.tryEmit(RecipeService.retrieveRecipes(groups[0]))
            }
        }
    }

    private fun Route.deleteRecipes() {
        delete("/recipe") {
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@delete
            when (RecipeService.deleteRecipe(call, groups[0])) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Recipe id is not unique"))
            }
            emitUpdate(groups)
        }
    }
}

