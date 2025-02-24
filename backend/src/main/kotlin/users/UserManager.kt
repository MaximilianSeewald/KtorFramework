package com.loudless.users

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import com.loudless.database.DatabaseManager
import com.loudless.database.Users
import io.ktor.http.*
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
        routing.getUserInformation()
        routing.updateUserInformation()
    }


    private fun Route.getUserInformation() {
        get("/user") {
            call.respond(UserService.retrieveAndHandleUsers(call)[0])
        }
    }

    private fun Route.updateUserInformation() {
        put("/user") {
            UserService.retrieveAndHandleUsers(call)
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

            if (user != null && DatabaseManager.verifyPassword(password, user.first)) {
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