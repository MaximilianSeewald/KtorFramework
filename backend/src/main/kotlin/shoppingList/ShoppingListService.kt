package com.loudless.shoppingList

import com.loudless.database.DatabaseManager
import com.loudless.database.ShoppingList
import com.loudless.models.ShoppingListItem
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object ShoppingListService {

    fun retrieveItems(userGroup: String): List<ShoppingListItem> {
        val shoppingListTable = DatabaseManager.shoppingListMap[userGroup]
        return transaction {
            shoppingListTable?.selectAll()?.map {
                ShoppingListItem(
                    name = it[shoppingListTable.name],
                    amount = it[shoppingListTable.amount],
                    id = it[shoppingListTable.id].toString(),
                    retrieved = it[shoppingListTable.retrieved]
                )
            } ?: emptyList()
        }
    }

    suspend fun addItem(call: RoutingCall, groupName: String): Boolean {
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No shopping list found for this user"))
            return false
        }
        val updateData = call.receive<ShoppingListItem>()
        return transaction {
            if (shoppingListTable
                    .selectAll()
                    .where { shoppingListTable.id eq UUID.fromString(updateData.id) }
                    .empty()) {
                shoppingListTable.insert {
                    it[id] = UUID.fromString(updateData.id)
                    it[name] = updateData.name
                    it[amount] = updateData.amount
                    it[retrieved] = false
                }
                true
            } else {
                false
            }
        }
    }

    suspend fun editItem(call: RoutingCall, groupName: String): Boolean {
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No shopping list found for this user"))
            return false
        }
        val updateData = call.receive<ShoppingListItem>()
        return transaction {
            val count = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq UUID.fromString(updateData.id) }
                .count()

            if (count == 1L) {
                shoppingListTable.update({ shoppingListTable.id eq UUID.fromString(updateData.id) }) {
                    it[name] = updateData.name
                    it[amount] = updateData.amount
                    it[retrieved] = updateData.retrieved
                }
                true
            } else {
                false
            }
        }
    }

    suspend fun deleteItem(call: RoutingCall, groupName: String): Boolean {
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No shopping list found for this user"))
            return false
        }
        val id = call.request.queryParameters["id"] ?: return false

        return transaction {
            val count = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq UUID.fromString(id) }
                .count()

            when (count) {
                0L -> true
                1L -> {
                    shoppingListTable.deleteWhere { shoppingListTable.id eq UUID.fromString(id) }
                    true
                }
                else -> false
            }
        }
    }

    fun addShoppingList(userGroup: String) {
        transaction {
            val shoppingList = ShoppingList(userGroup)
            SchemaUtils.create(shoppingList)
            DatabaseManager.shoppingListMap[userGroup] = shoppingList
        }
    }

    fun deleteShoppingList(userGroup: String) {
        transaction {
            val shoppingList = DatabaseManager.shoppingListMap[userGroup]
            SchemaUtils.drop(shoppingList!!)
            DatabaseManager.shoppingListMap.remove(userGroup)
        }
    }
}