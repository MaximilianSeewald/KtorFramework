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
import org.slf4j.LoggerFactory

object UserService {
    private val LOGGER = LoggerFactory.getLogger(UserService::class.java)

    private fun getUserGroupsByUsername(username: String): List<String>? {
        return transaction {
            val groups = Users.selectAll().where { Users.name eq username }
                .map { it[Users.group] ?: "" }
            if (groups.isEmpty()) {
                null
            } else {
                groups.filter { it.isNotBlank() }
            }
        }
    }

    fun getUserGroupsByPrincipal(call: ApplicationCall): List<String>? {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        LOGGER.info("Retrieving user group for authenticated user")
        val groups = getUserGroupsByUsername(username)
        LOGGER.info("Retrieved {} user group records for authenticated user", groups?.size ?: 0)
        return groups
    }

    fun getUserGroupsByQuery(decodedJWT: DecodedJWT): List<String>? {
        val username = decodedJWT.getClaim("username").asString()
        LOGGER.info("Retrieving user group for websocket user")
        val groups = getUserGroupsByUsername(username)
        LOGGER.info("Retrieved {} user group records for websocket user", groups?.size ?: 0)
        return groups
    }

    private fun getUserInformationByPrincipal(call: ApplicationCall): List<User> {
        val principal = call.principal<JWTPrincipal>()
        val username = principal?.getClaim("username", String::class) ?: ""
        LOGGER.info("Retrieving user information for authenticated user")
        return findUsersByUsername(username)
    }

    fun findAuthenticatedUser(call: ApplicationCall): User? {
        val principal = call.principal<JWTPrincipal>() ?: return null
        val username = principal.getClaim("username", String::class) ?: return null
        val users = findUsersByUsername(username)
        return when (users.size) {
            1 -> users[0]
            else -> {
                LOGGER.warn("Expected exactly one user for authenticated principal, found {}", users.size)
                null
            }
        }
    }

    fun findUsersByUsername(username: String): List<User> {
        return transaction {
            val users = Users.selectAll().where { Users.name eq username }
                .map { User(it[Users.id], it[Users.name], it[Users.group] ?: "") }
            LOGGER.info("Retrieved {} user information records for username {}", users.size, username)
            users
        }
    }

    suspend fun retrieveAndHandleUsers(call: ApplicationCall): List<User> {
        val userList = getUserInformationByPrincipal(call)
        return when {
            userList.isEmpty() -> {
                LOGGER.warn("No user found for authenticated request")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No User Found"))
                emptyList()
            }
            userList.size > 1 -> {
                LOGGER.warn("Multiple users found for authenticated request")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Multiple User Found for this name"))
                emptyList()
            }
            else -> userList
        }
    }

    fun addUser(name: String, password: String) {
        LOGGER.info("Adding user {}", name)
        transaction {
            Users.insert {
                it[Users.name] = name
                it[Users.hashedPassword] = DatabaseManager.hashPassword(password)
            }
        }
        LOGGER.info("User {} added", name)
    }

    fun addUserGroupToUser(userId: Int, userGroup: String) {
        LOGGER.info("Assigning user {} to group {}", userId, userGroup)
        transaction {
            Users.update({ Users.id eq userId }) {
                it[group] = userGroup
            }
        }
        LOGGER.info("Assigned user {} to group {}", userId, userGroup)
    }

    fun userExists(username: String): Boolean {
        LOGGER.info("Checking whether user {} exists", username)
        return transaction {
            val exists = Users.selectAll().where { Users.name eq username }.count() > 0
            LOGGER.info("User {} exists: {}", username, exists)
            exists
        }
    }

    fun verifyUserPassword(userId: Int, password: String): Boolean {
        LOGGER.info("Verifying password for user {}", userId)
        return transaction {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
            if (user != null) {
                val verified = DatabaseManager.verifyPassword(password, user[Users.hashedPassword])
                LOGGER.info("Password verification for user {} completed: {}", userId, verified)
                verified
            } else {
                LOGGER.warn("Password verification failed because user {} was not found", userId)
                false
            }
        }
    }

    fun updatePassword(userId: Int, newPassword: String) {
        LOGGER.info("Updating password for user {}", userId)
        transaction {
            Users.update(where = { Users.id eq userId }) {
                it[hashedPassword] = DatabaseManager.hashPassword(newPassword)
            }
        }
        LOGGER.info("Updated password for user {}", userId)
    }

    fun deleteUserGroupFromUser(userId: Int) {
        LOGGER.info("Removing group assignment from user {}", userId)
        transaction {
            Users.update({ Users.id eq userId }) {
                it[group] = null
            }
        }
        LOGGER.info("Removed group assignment from user {}", userId)
    }

    fun deleteUserGroupFromAllUsers(userGroup: String) {
        LOGGER.info("Removing group {} from all users", userGroup)
        transaction {
            Users.update({ Users.group eq userGroup }) {
                it[group] = null
            }
        }
        LOGGER.info("Removed group {} from all users", userGroup)
    }

    fun getUsersForGroup(group: String): List<String> {
        LOGGER.info("Retrieving users for group {}", group)
        return transaction {
            val users = Users.selectAll().where { Users.group eq group }.map { it[Users.name] }
            LOGGER.info("Retrieved {} users for group {}", users.size, group)
            users
        }
    }
}
