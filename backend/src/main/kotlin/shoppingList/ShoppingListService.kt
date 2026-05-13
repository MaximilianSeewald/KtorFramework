package com.loudless.shoppingList

import com.loudless.database.DatabaseManager
import com.loudless.database.ShoppingList
import com.loudless.models.ShoppingListItem
import com.loudless.userGroups.UserGroupNameValidator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

object ShoppingListService {
    private val LOGGER = LoggerFactory.getLogger(ShoppingListService::class.java)

    fun retrieveItems(userGroup: String): List<ShoppingListItem> {
        LOGGER.info("Retrieving shopping list items for group {}", userGroup)
        val shoppingListTable = DatabaseManager.shoppingListMap[userGroup]
        return transaction {
            val items = shoppingListTable?.selectAll()?.map {
                ShoppingListItem(
                    name = it[shoppingListTable.name],
                    amount = it[shoppingListTable.amount],
                    id = it[shoppingListTable.id].toString(),
                    retrieved = it[shoppingListTable.retrieved]
                )
            } ?: emptyList()
            LOGGER.info("Retrieved {} shopping list items for group {}", items.size, userGroup)
            items
        }
    }

    suspend fun addItem(call: RoutingCall, groupName: String): Boolean {
        LOGGER.info("Adding shopping list item for group {}", groupName)
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            LOGGER.warn("Cannot add shopping list item because group {} has no shopping list", groupName)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No shopping list found for this user"))
            return false
        }
        val updateData = call.receive<ShoppingListItem>()
        val itemId = parseUuid(updateData.id) ?: return false
        return transaction {
            if (shoppingListTable
                    .selectAll()
                    .where { shoppingListTable.id eq itemId }
                    .empty()) {
                shoppingListTable.insert {
                    it[id] = itemId
                    it[name] = updateData.name
                    it[amount] = updateData.amount
                    it[retrieved] = false
                }
                LOGGER.info("Added shopping list item {} for group {}", updateData.id, groupName)
                true
            } else {
                LOGGER.warn("Did not add shopping list item {} for group {} because it already exists", updateData.id, groupName)
                false
            }
        }
    }

    suspend fun editItem(call: RoutingCall, groupName: String): Boolean {
        LOGGER.info("Editing shopping list item for group {}", groupName)
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            LOGGER.warn("Cannot edit shopping list item because group {} has no shopping list", groupName)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No shopping list found for this user"))
            return false
        }
        val updateData = call.receive<ShoppingListItem>()
        val itemId = parseUuid(updateData.id) ?: return false
        return transaction {
            val count = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq itemId }
                .count()

            if (count == 1L) {
                shoppingListTable.update({ shoppingListTable.id eq itemId }) {
                    it[name] = updateData.name
                    it[amount] = updateData.amount
                    it[retrieved] = updateData.retrieved
                }
                LOGGER.info("Edited shopping list item {} for group {}", updateData.id, groupName)
                true
            } else {
                LOGGER.warn("Did not edit shopping list item {} for group {} because match count was {}", updateData.id, groupName, count)
                false
            }
        }
    }

    suspend fun deleteItem(call: RoutingCall, groupName: String): Boolean {
        LOGGER.info("Deleting shopping list item for group {}", groupName)
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            LOGGER.warn("Cannot delete shopping list item because group {} has no shopping list", groupName)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No shopping list found for this user"))
            return false
        }
        val id = call.request.queryParameters["id"] ?: run {
            LOGGER.warn("Cannot delete shopping list item because id query parameter is missing")
            return false
        }
        val itemId = parseUuid(id) ?: return false

        return transaction {
            val count = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq itemId }
                .count()

            when (count) {
                0L -> true
                1L -> {
                    shoppingListTable.deleteWhere { shoppingListTable.id eq itemId }
                    LOGGER.info("Deleted shopping list item {} for group {}", id, groupName)
                    true
                }
                else -> {
                    LOGGER.warn("Did not delete shopping list item {} for group {} because match count was {}", id, groupName, count)
                    false
                }
            }
        }
    }

    fun addShoppingList(userGroup: String) {
        UserGroupNameValidator.requireValid(userGroup)
        LOGGER.info("Creating shopping list table for group {}", userGroup)
        transaction {
            val shoppingList = ShoppingList(userGroup)
            SchemaUtils.create(shoppingList)
            DatabaseManager.shoppingListMap[userGroup] = shoppingList
        }
        LOGGER.info("Created shopping list table for group {}", userGroup)
    }

    fun deleteShoppingList(userGroup: String) {
        LOGGER.info("Dropping shopping list table for group {}", userGroup)
        transaction {
            val shoppingList = DatabaseManager.shoppingListMap[userGroup]
            if (shoppingList == null) {
                LOGGER.warn("Skipping shopping list drop because group {} has no table", userGroup)
                return@transaction
            }
            SchemaUtils.drop(shoppingList)
            DatabaseManager.shoppingListMap.remove(userGroup)
        }
        LOGGER.info("Dropped shopping list table for group {}", userGroup)
    }

    private fun parseUuid(id: String): UUID? {
        return runCatching { UUID.fromString(id) }
            .onFailure { LOGGER.warn("Rejected shopping list item because id {} is not a valid UUID", id) }
            .getOrNull()
    }
}
