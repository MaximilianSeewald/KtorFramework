package com.loudless.recipes

import com.loudless.database.DatabaseManager
import com.loudless.database.Recipe
import com.loudless.models.Recipe as RecipeModel
import com.loudless.models.RecipeItem
import com.loudless.shoppingList.ShoppingListService
import com.loudless.userGroups.UserGroupNameValidator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

object RecipeService {
    private val LOGGER = LoggerFactory.getLogger(RecipeService::class.java)

    sealed class AddRecipeToShoppingListResult {
        data class Success(val added: Int) : AddRecipeToShoppingListResult()
        data class Failure(val message: String) : AddRecipeToShoppingListResult()
    }

    fun retrieveRecipes(userGroup: String): List<RecipeModel> {
        LOGGER.info("Retrieving recipes for group {}", userGroup)
        val recipeTable = DatabaseManager.recipeMap[userGroup]
        return transaction {
            val recipes = recipeTable?.selectAll()?.map {
                RecipeModel(
                    id = it[recipeTable.id].toString(),
                    name = it[recipeTable.name],
                    items = parseItems(it[recipeTable.items])
                )
            } ?: emptyList()
            LOGGER.info("Retrieved {} recipes for group {}", recipes.size, userGroup)
            recipes
        }
    }

    suspend fun addRecipe(call: RoutingCall, groupName: String): Boolean {
        LOGGER.info("Adding recipe for group {}", groupName)
        val recipeTable = DatabaseManager.recipeMap[groupName]
        if (recipeTable == null) {
            LOGGER.warn("Cannot add recipe because group {} has no recipe list", groupName)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No recipe list found for this user"))
            return false
        }

        return try {
            val updateData = call.receive<RecipeModel>()
            val recipeId = parseUuid(updateData.id) ?: return false
            transaction {
                if (recipeTable
                        .selectAll()
                        .where { recipeTable.id eq recipeId }
                        .empty()) {
                    recipeTable.insert {
                        it[id] = recipeId
                        it[name] = updateData.name
                        it[items] = serializeItems(updateData.items)
                    }
                    LOGGER.info("Added recipe {} for group {}", updateData.id, groupName)
                    true
                } else {
                    LOGGER.warn("Did not add recipe {} for group {} because it already exists", updateData.id, groupName)
                    false
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Invalid recipe data while adding recipe for group {}", groupName, e)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid recipe data: ${e.message}"))
            false
        }
    }

    suspend fun editRecipe(call: RoutingCall, groupName: String): Boolean {
        LOGGER.info("Editing recipe for group {}", groupName)
        val recipeTable = DatabaseManager.recipeMap[groupName]
        if (recipeTable == null) {
            LOGGER.warn("Cannot edit recipe because group {} has no recipe list", groupName)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No recipe list found for this user"))
            return false
        }

        return try {
            val updateData = call.receive<RecipeModel>()
            val recipeId = parseUuid(updateData.id) ?: return false
            transaction {
                val count = recipeTable
                    .selectAll()
                    .where { recipeTable.id eq recipeId }
                    .count()

                if (count == 1L) {
                    recipeTable.update({ recipeTable.id eq recipeId }) {
                        it[name] = updateData.name
                        it[items] = serializeItems(updateData.items)
                    }
                    LOGGER.info("Edited recipe {} for group {}", updateData.id, groupName)
                    true
                } else {
                    LOGGER.warn("Did not edit recipe {} for group {} because match count was {}", updateData.id, groupName, count)
                    false
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Invalid recipe data while editing recipe for group {}", groupName, e)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid recipe data: ${e.message}"))
            false
        }
    }

    suspend fun deleteRecipe(call: RoutingCall, groupName: String): Boolean {
        LOGGER.info("Deleting recipe for group {}", groupName)
        val recipeTable = DatabaseManager.recipeMap[groupName]
        if (recipeTable == null) {
            LOGGER.warn("Cannot delete recipe because group {} has no recipe list", groupName)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No recipe list found for this user"))
            return false
        }
        val id = call.request.queryParameters["id"] ?: run {
            LOGGER.warn("Cannot delete recipe because id query parameter is missing")
            return false
        }
        val recipeId = parseUuid(id) ?: return false

        return transaction {
            val count = recipeTable
                .selectAll()
                .where { recipeTable.id eq recipeId }
                .count()

            when (count) {
                0L -> true
                1L -> {
                    recipeTable.deleteWhere { recipeTable.id eq recipeId }
                    LOGGER.info("Deleted recipe {} for group {}", id, groupName)
                    true
                }
                else -> {
                    LOGGER.warn("Did not delete recipe {} for group {} because match count was {}", id, groupName, count)
                    false
                }
            }
        }
    }

    fun addRecipeToShoppingList(recipeIdText: String, groupName: String): AddRecipeToShoppingListResult {
        LOGGER.info("Adding recipe {} to shopping list for group {}", recipeIdText, groupName)
        val recipeTable = DatabaseManager.recipeMap[groupName]
            ?: return AddRecipeToShoppingListResult.Failure("No recipe list found for this user")
        val recipeId = parseUuid(recipeIdText)
            ?: return AddRecipeToShoppingListResult.Failure("Recipe id is invalid")

        val recipeItems = transaction {
            recipeTable
                .selectAll()
                .where { recipeTable.id eq recipeId }
                .map { parseItems(it[recipeTable.items]) }
                .singleOrNull()
        } ?: return AddRecipeToShoppingListResult.Failure("Recipe id does not exist")

        if (recipeItems.isEmpty()) {
            LOGGER.warn("Rejected adding recipe {} to shopping list because it has no items", recipeIdText)
            return AddRecipeToShoppingListResult.Failure("Recipe has no items")
        }

        val added = ShoppingListService.addRecipeItems(recipeItems, groupName)
            ?: return AddRecipeToShoppingListResult.Failure("No shopping list found for this user")

        return AddRecipeToShoppingListResult.Success(added)
    }

    fun addRecipeList(userGroup: String) {
        UserGroupNameValidator.requireValid(userGroup)
        LOGGER.info("Creating recipe table for group {}", userGroup)
        transaction {
            val recipe = Recipe(userGroup + "_recipe")
            SchemaUtils.create(recipe)
            DatabaseManager.recipeMap[userGroup] = recipe
        }
        LOGGER.info("Created recipe table for group {}", userGroup)
    }

    fun deleteRecipeList(userGroup: String) {
        LOGGER.info("Dropping recipe table for group {}", userGroup)
        transaction {
            val recipe = DatabaseManager.recipeMap[userGroup]
            if (recipe == null) {
                LOGGER.warn("Skipping recipe table drop because group {} has no table", userGroup)
                return@transaction
            }
            SchemaUtils.drop(recipe)
            DatabaseManager.recipeMap.remove(userGroup)
        }
        LOGGER.info("Dropped recipe table for group {}", userGroup)
    }

    private fun serializeItems(items: List<RecipeItem>): String {
        return Json.encodeToString(items)
    }

    private fun parseItems(itemsJson: String): List<RecipeItem> {
        return try {
            Json.decodeFromString(itemsJson)
        } catch (e: Exception) {
            LOGGER.warn("Could not parse stored recipe items JSON", e)
            emptyList()
        }
    }

    private fun parseUuid(id: String): UUID? {
        return runCatching { UUID.fromString(id) }
            .onFailure { LOGGER.warn("Rejected recipe because id {} is not a valid UUID", id) }
            .getOrNull()
    }
}
