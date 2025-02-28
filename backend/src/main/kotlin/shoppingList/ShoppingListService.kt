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
        val shoppingListDataMap = transaction {
            shoppingListTable?.selectAll()?.map {
                ShoppingListItem(
                    name = it[shoppingListTable.name],
                    amount = it[shoppingListTable.amount],
                    id = it[shoppingListTable.id].toString(),
                    retrieved = it[shoppingListTable.retrieved]
                )
            } ?: mutableListOf()
        }
        return shoppingListDataMap
    }

    suspend fun addItem(call: RoutingCall, groupName: String): Boolean {
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            call.respond(HttpStatusCode.BadRequest, "No shopping list found for this user")
            return false
        }
        val updateData = call.receive<ShoppingListItem>()
        return transaction {
            val item = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq UUID.fromString(updateData.id) }
                .map { it[shoppingListTable.id] }

            if (item.isEmpty()) {
                shoppingListTable.insert {
                    it[id] = UUID.fromString(updateData.id)
                    it[name] = updateData.name
                    it[amount] = updateData.amount
                    it[retrieved] = false
                }
                return@transaction true
            }  else {
                return@transaction false
            }
        }
    }

    suspend fun editItem(call: RoutingCall, groupName: String): Boolean {
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            call.respond(HttpStatusCode.BadRequest, "No shopping list found for this user")
            return false
        }
        val updateData = call.receive<ShoppingListItem>()
        return transaction {
            val item = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq UUID.fromString(updateData.id) }
                .map { it[shoppingListTable.id] }

            if (item.size == 1) {
                shoppingListTable.update({ shoppingListTable.id eq UUID.fromString(updateData.id) }) {
                    it[name] = updateData.name
                    it[amount] = updateData.amount
                    it[retrieved] = updateData.retrieved
                }
                return@transaction true
            } else {
                return@transaction false
            }
        }
    }

    suspend fun deleteItem(call: RoutingCall, groupName: String): Boolean {
        val shoppingListTable = DatabaseManager.shoppingListMap[groupName]
        if (shoppingListTable == null) {
            call.respond(HttpStatusCode.BadRequest, "No shopping list found for this user")
            return false
        }
        val id = call.request.queryParameters["id"]

        return transaction {
            val item = shoppingListTable
                .selectAll()
                .where { shoppingListTable.id eq UUID.fromString(id) }
                .map { it[shoppingListTable.id] }

            if (item.isEmpty()) {
                return@transaction true
            } else if (item.size == 1) {
                shoppingListTable.deleteWhere {
                    shoppingListTable.id eq UUID.fromString(id)
                }
                return@transaction true
            } else {
                return@transaction false
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