package com.loudless.users

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.interfaces.DecodedJWT
import com.loudless.database.DatabaseManager.shoppingListMap
import com.loudless.database.ShoppingList
import com.loudless.database.UserGroups
import com.loudless.database.Users
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object UserService {

    fun getUserGroupsByPrincipal(call: ApplicationCall): List<String> {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        val groups = transaction {
            Users.selectAll().where { Users.name eq username }
                .map { it[Users.group] ?: "" }
        }
        return groups
    }

    fun getUsernameByPrincipal(call: ApplicationCall): String {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        return username
    }

    fun getUserNameByQuery(decodedJWT: DecodedJWT): String {
        return decodedJWT.getClaim("username").asString()
    }

    fun getUserGroupsByQuery(decodedJWT: DecodedJWT): List<String> {
        val username = decodedJWT.getClaim("username").asString()
        val groups = transaction {
            Users.selectAll().where { Users.name eq username }
                .map { it[Users.group] ?: "" }
        }
        return groups
    }

    private fun hashPassword(plainPassword: String): String {
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