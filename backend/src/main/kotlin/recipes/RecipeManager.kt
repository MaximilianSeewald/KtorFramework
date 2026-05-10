package com.loudless.recipes

import com.loudless.models.Recipe
import com.loudless.shared.GenericManager
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        webSocketResource(
            path = "/recipeWS",
            resourceName = "recipe",
            retrieveData = { RecipeService.retrieveRecipes(it) },
            serializeData = { Json.encodeToString(it) }
        )
    }

    private fun Route.getRecipes() {
        get("/recipe") {
            LOGGER.info("Handling get recipes request")
            val groups = retrieveUserGroupsAndHandleErrors(call)
            if (groups.isEmpty()) return@get
            call.respond(RecipeService.retrieveRecipes(groups[0]))
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
            if (call.request.queryParameters["id"].isNullOrBlank()) {
                LOGGER.warn("Rejected delete recipe because id query parameter was missing")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Recipe id is missing"))
                return@delete
            }
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

