package com.loudless

import at.favre.lib.crypto.bcrypt.BCrypt
import com.loudless.DatabaseManager.ShoppingList
import com.loudless.DatabaseManager.UserGroups
import com.loudless.DatabaseManager.Users
import com.loudless.DatabaseManager.shoppingListMap
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object UserService {

    suspend fun getAllUsers() :List<User> = newSuspendedTransaction{
        Users.selectAll().map { toUsers(it) }
    }

    private fun toUsers(row: ResultRow) : User {
        return User(
            id = row[Users.id],
            name = row[Users.name],
            hashedPassword = row[Users.hashedPassword]
        )
    }

    fun hashPassword(plainPassword: String): String {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
    }

    fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword)
        return result.verified
    }

    fun addUser(name: String, password: String, group: String) {
        transaction {
            if(UserGroups.selectAll().none { it[UserGroups.name] == group }) {
                UserGroups.insert {
                    it[UserGroups.name] = group
                }
                val shoppingList = ShoppingList(group)
                transaction { SchemaUtils.create(shoppingList) }
                shoppingListMap[group] = shoppingList
            }
            Users.insert {
                it[Users.name] = name
                it[Users.group] = group
                it[Users.hashedPassword] = hashPassword(password)
            }
        }
    }
}