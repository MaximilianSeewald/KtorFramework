package com.loudless.recipes

import com.loudless.database.DatabaseManager
import com.loudless.database.Recipe
import com.loudless.models.Recipe as RecipeModel
import com.loudless.models.RecipeItem
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object RecipeService {

    fun retrieveRecipes(userGroup: String): List<RecipeModel> {
        val recipeTable = DatabaseManager.recipeMap[userGroup]
        return transaction {
            recipeTable?.selectAll()?.map {
                RecipeModel(
                    id = it[recipeTable.id].toString(),
                    name = it[recipeTable.name],
                    items = parseItems(it[recipeTable.items])
                )
            } ?: emptyList()
        }
    }

    suspend fun addRecipe(call: RoutingCall, groupName: String): Boolean {
        val recipeTable = DatabaseManager.recipeMap[groupName]
        if (recipeTable == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No recipe list found for this user"))
            return false
        }

        return try {
            val updateData = call.receive<RecipeModel>()
            transaction {
                if (recipeTable
                        .selectAll()
                        .where { recipeTable.id eq UUID.fromString(updateData.id) }
                        .empty()) {
                    recipeTable.insert {
                        it[id] = UUID.fromString(updateData.id)
                        it[name] = updateData.name
                        it[items] = serializeItems(updateData.items)
                    }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid recipe data: ${e.message}"))
            false
        }
    }

    suspend fun editRecipe(call: RoutingCall, groupName: String): Boolean {
        val recipeTable = DatabaseManager.recipeMap[groupName]
        if (recipeTable == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No recipe list found for this user"))
            return false
        }

        return try {
            val updateData = call.receive<RecipeModel>()
            transaction {
                val count = recipeTable
                    .selectAll()
                    .where { recipeTable.id eq UUID.fromString(updateData.id) }
                    .count()

                if (count == 1L) {
                    recipeTable.update({ recipeTable.id eq UUID.fromString(updateData.id) }) {
                        it[name] = updateData.name
                        it[items] = serializeItems(updateData.items)
                    }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid recipe data: ${e.message}"))
            false
        }
    }

    suspend fun deleteRecipe(call: RoutingCall, groupName: String): Boolean {
        val recipeTable = DatabaseManager.recipeMap[groupName]
        if (recipeTable == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No recipe list found for this user"))
            return false
        }
        val id = call.request.queryParameters["id"] ?: return false

        return transaction {
            val count = recipeTable
                .selectAll()
                .where { recipeTable.id eq UUID.fromString(id) }
                .count()

            when (count) {
                0L -> true
                1L -> {
                    recipeTable.deleteWhere { recipeTable.id eq UUID.fromString(id) }
                    true
                }
                else -> false
            }
        }
    }

    fun addRecipeList(userGroup: String) {
        transaction {
            val recipe = Recipe(userGroup + "_recipe")
            SchemaUtils.create(recipe)
            DatabaseManager.recipeMap[userGroup] = recipe
        }
    }

    fun deleteRecipeList(userGroup: String) {
        transaction {
            val recipe = DatabaseManager.recipeMap[userGroup]
            SchemaUtils.drop(recipe!!)
            DatabaseManager.recipeMap.remove(userGroup)
        }
    }

    private fun serializeItems(items: List<RecipeItem>): String {
        return Json.encodeToString(items)
    }

    private fun parseItems(itemsJson: String): List<RecipeItem> {
        return try {
            Json.decodeFromString(itemsJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
