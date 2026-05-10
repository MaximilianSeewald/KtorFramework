package com.loudless.recipes

import com.loudless.models.Recipe
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


class RecipeManager : GenericManager<Recipe>() {
    private val LOGGER = LoggerFactory.getLogger(RecipeManager::class.java)

    fun initRoutes(route: Route) {
        route.getRecipes()
        route.putRecipes()
        route.postRecipes()
        route.deleteRecipes()
    }

    fun initQueryRoutes(route: Route) {
        route.webSocketRecipes()
    }

    private fun Route.webSocketRecipes() {
        webSocket("/recipeWS") {
            LOGGER.info("Handling recipe websocket connection")
            val token = call.request.queryParameters["token"]
            val decodedJWT = JwtUtil.validateWebSocketToken(this, token) ?: return@webSocket
            
            val groups = UserService.getUserGroupsByQuery(decodedJWT)
            val userName = JwtUtil.getUsername(decodedJWT)
            
            if (groups.isEmpty() || groups.contains("")) {
                LOGGER.warn("Rejected recipe websocket because user has no group")
                return@webSocket
            }
            
            var flow = MutableSharedFlow<List<Recipe>>(replay = 1)
            if (observerList[userName] == null) {
                observerList[userName] = flow
            } else {
                flow = observerList[userName]!!
            }
            
            val job = launch {
                flow.collect { recipes ->
                    send(Json.encodeToString(recipes))
                }
            }
            
            runCatching {
                send(Json.encodeToString(RecipeService.retrieveRecipes(groups[0])))
                incoming.consumeEach { }
            }.onFailure {
                LOGGER.error("Recipe websocket failed for user {}", userName, it)
                observerList.remove(userName)
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, it.message ?: ""))
                job.cancel()
            }.also {
                observerList.remove(userName)
                job.cancel()
                LOGGER.info("Recipe websocket closed for user {}", userName)
            }
        }
    }

    private fun Route.getRecipes() {
        get("/recipe") {
            LOGGER.info("Handling get recipes request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@get
            call.respondText(
                text = Json.encodeToString(RecipeService.retrieveRecipes(groups[0])),
                contentType = ContentType.Application.Json
            )
            LOGGER.info("Returned recipes for group {}", groups[0])
        }
    }

    private fun Route.postRecipes() {
        post("/recipe") {
            LOGGER.info("Handling add recipe request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@post
            when (RecipeService.addRecipe(call, groups[0])) {
                true -> {
                    call.respond(HttpStatusCode.OK)
                    LOGGER.info("Added recipe for group {}", groups[0])
                }
                false -> {
                    LOGGER.warn("Rejected add recipe for group {}", groups[0])
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Couldn't add recipe"))
                }
            }
            emitUpdate(groups) { RecipeService.retrieveRecipes(it) }
        }
    }

    private fun Route.putRecipes() {
        put("/recipe") {
            LOGGER.info("Handling edit recipe request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@put
            when (RecipeService.editRecipe(call, groups[0])) {
                true -> {
                    call.respond(HttpStatusCode.OK)
                    LOGGER.info("Edited recipe for group {}", groups[0])
                }
                false -> {
                    LOGGER.warn("Rejected edit recipe for group {}", groups[0])
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Recipe id does not exist"))
                }
            }
            emitUpdate(groups) { RecipeService.retrieveRecipes(it) }
        }
    }

    private fun Route.deleteRecipes() {
        delete("/recipe") {
            LOGGER.info("Handling delete recipe request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@delete
            when (RecipeService.deleteRecipe(call, groups[0])) {
                true -> {
                    call.respond(HttpStatusCode.OK)
                    LOGGER.info("Deleted recipe for group {}", groups[0])
                }
                false -> {
                    LOGGER.warn("Rejected delete recipe for group {}", groups[0])
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Recipe id is not unique"))
                }
            }
            emitUpdate(groups) { RecipeService.retrieveRecipes(it) }
        }
    }
}

