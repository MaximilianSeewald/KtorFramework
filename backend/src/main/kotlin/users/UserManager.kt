package com.loudless.users

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import com.loudless.database.Users
import com.loudless.models.User
import com.loudless.users.UserService.getUserInformationByPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class UserManager {
    private val validityInMs = 1000000000

    fun initRouting(routing: Routing) {
        routing.login()

    }

    fun initSafeRoutes(routing: Route) {
        routing.getInformation()
        routing.updateInformation()
    }

    private suspend fun retrieveAndHandleUsers(call: ApplicationCall): List<User> {
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

    private fun Route.getInformation() {
        get("/user") {
            call.respond(retrieveAndHandleUsers(call)[0])
        }
    }

    private fun Route.updateInformation() {
        put("/user") {
            retrieveAndHandleUsers(call)
            UserService.editUser(call)
        }
    }

    private fun Routing.login() {
        post("/login") {
            val parameters = call.receiveParameters()
            val username: String = parameters["username"] ?: ""
            val password: String = parameters["password"] ?: ""
            val user = transaction {
                Users
                    .selectAll().where { Users.name eq username }
                    .map { it[Users.hashedPassword] to it[Users.name] }
                    .firstOrNull()
            }

            if (user != null && UserService.verifyPassword(password, user.first)) {
                val token = JWT.create()
                    .withAudience("ktor-app")
                    .withIssuer("ktor-auth")
                    .withClaim("username", username)
                    .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
                    .sign(Algorithm.HMAC256(secretJWTKey))
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}