package com.loudless.users

import com.auth0.jwt.interfaces.DecodedJWT
import com.loudless.database.DatabaseManager
import com.loudless.database.Users
import com.loudless.models.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object UserService {

    fun getUserGroupsByPrincipal(call: ApplicationCall): List<String> {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        return transaction {
            Users.selectAll().where { Users.name eq username }
                .map { it[Users.group] ?: "" }
        }
    }

    fun getUserNameByQuery(decodedJWT: DecodedJWT): String {
        return decodedJWT.getClaim("username").asString()
    }

    fun getUserGroupsByQuery(decodedJWT: DecodedJWT): List<String> {
        val username = decodedJWT.getClaim("username").asString()
        return transaction {
            Users.selectAll().where { Users.name eq username }
                .map { it[Users.group] ?: "" }
        }
    }

    private fun getUserInformationByPrincipal(call: ApplicationCall): List<User> {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        return transaction {
            Users.selectAll().where { Users.name eq username }
                .map { User(it[Users.id], it[Users.name], it[Users.group] ?: "") }
        }
    }

    suspend fun retrieveAndHandleUsers(call: ApplicationCall): List<User> {
        val userList = getUserInformationByPrincipal(call)
        if(userList.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "No User Found")
            return emptyList()
        }
        if(userList.size > 1) {
            call.respond(HttpStatusCode.BadRequest, "Multiple User Found for this name")
            return emptyList()
        }
        return userList
    }

    fun addUser(name: String, password: String) {
        transaction {
            Users.insert {
                it[Users.name] = name
                it[Users.hashedPassword] = DatabaseManager.hashPassword(password)
            }
        }
    }

    fun addUserGroupToUser(userId: Int, userGroup: String) {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[group] = userGroup
            }
        }
    }

    fun userExists(username: String): Boolean {
        return transaction {
            Users.selectAll().where { Users.name eq username }.count() > 0
        }
    }

    fun verifyUserPassword(userId: Int, password: String): Boolean {
        return transaction {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
            if (user != null) {
                DatabaseManager.verifyPassword(password, user[Users.hashedPassword])
            } else {
                false
            }
        }
    }

    fun updatePassword(userId: Int, newPassword: String) {
        transaction {
            Users.update(where = { Users.id eq userId }) {
                it[hashedPassword] = DatabaseManager.hashPassword(newPassword)
            }
        }
    }

    fun deleteUserGroupFromUser(userId: Int) {
        transaction {
            Users.update({ Users.id eq userId }) {
                it[group] = null
            }
        }
    }

    fun deleteUserGroupFromAllUsers(userGroup: String) {
        transaction {
            Users.update({ Users.group eq userGroup }) {
                it[group] = null
            }
        }
    }

    fun getUsersForGroup(group: String): List<String> {
        return transaction {
            Users.selectAll().where { Users.group eq group }.map { it[Users.name] }
        }
    }
}