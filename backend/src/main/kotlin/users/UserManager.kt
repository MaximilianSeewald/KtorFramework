package com.loudless.users

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import com.loudless.database.DatabaseManager
import com.loudless.database.Users
import com.loudless.models.JoinUserGroupRequest
import com.loudless.userGroups.UserGroupService
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
        routing.userSignUp()
    }

    fun initSafeRoutes(routing: Route) {
        routing.getUserInformation()
        routing.joinUserGroup()
        routing.leaveUserGroup()
        routing.userChangePassword()
    }


    // TODO: change to `GET /user/{userName}` and verify that userName matches the user in the token?
    private fun Route.getUserInformation() {
        get("/user") {
            call.respond(UserService.retrieveAndHandleUsers(call)[0])
        }
    }

    private fun Route.userSignUp() {
        post("/user") {
            val parameters = call.receiveParameters()
            val username: String = parameters["username"] ?: ""
            val password: String = parameters["password"] ?: ""
            if (username == "" || password == "") {
                call.respond(HttpStatusCode.BadRequest, "Username or password is empty")
                return@post
            }
            if (UserService.userExists(username)) {
                call.respond(HttpStatusCode.BadRequest, "User already exists")
                return@post
            }
            UserService.addUser(username, password)
            call.respond(HttpStatusCode.Created)
        }
    }

    private fun Route.userChangePassword() {
        post("/user/{userName}/password") {
            val user = UserService.retrieveAndHandleUsers(call)[0]
            val userNameFromRoute = call.parameters["userName"]!!
            val parameters = call.receiveParameters()
            val currentPassword: String = parameters["oldPassword"] ?: ""
            val newPassword: String = parameters["newPassword"] ?: ""
            if (user.name != userNameFromRoute) {
                call.respond(HttpStatusCode.BadRequest, "User does not match")
                return@post
            }
            if (!UserService.verifyUserPassword(user.id, currentPassword)) {
                call.respond(HttpStatusCode.BadRequest, "Current password is incorrect")
                return@post
            }
            if (newPassword == "") {
                call.respond(HttpStatusCode.BadRequest, "New password is empty")
                return@post
            }
            //UserService.changePassword(user.id, newPassword)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.joinUserGroup() {
        post("/user/{userName}/groups") {
            val user = UserService.retrieveAndHandleUsers(call)[0]
            val userFromRoute = call.parameters["userName"]!!
            val joinUserGroupRequest = call.receive<JoinUserGroupRequest>()
            val password = joinUserGroupRequest.password
            val userGroupName = joinUserGroupRequest.userGroupName
            if (user.name != userFromRoute) {
                call.respond(HttpStatusCode.BadRequest, "User does not match")
                return@post
            }
            if (user.userGroup != null && user.userGroup != "") {
                call.respond(HttpStatusCode.BadRequest, "User is already in a group")
                return@post
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.NotFound, "User group does not exist")
                return@post
            }
            if (!UserGroupService.checkPassword(userGroupName, password)) {
                call.respond(HttpStatusCode.BadRequest, "Incorrect password")
                return@post
            }
            if (user.userGroup == userGroupName) {
                call.respond(HttpStatusCode.BadRequest, "User is already in the group")
                return@post
            }
            UserService.addUserGroupToUser(user.id, userGroupName)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.leaveUserGroup() {
        delete("/user/{userName}/groups/{groupName}") {
            val user = UserService.retrieveAndHandleUsers(call)[0]
            val userFromRoute = call.parameters["userName"]!!
            val userGroupName = call.parameters["groupName"]!!
            if (user.name != userFromRoute) {
                call.respond(HttpStatusCode.BadRequest, "User does not match")
                return@delete
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.NotFound, "User group does not exist")
                return@delete
            }
            if (UserGroupService.checkIsAdmin(user)) {
                call.respond(HttpStatusCode.BadRequest, "Admin cannot leave group")
                return@delete
            }
            if (user.userGroup != userGroupName) {
                call.respond(HttpStatusCode.BadRequest, "User is not in the group")
                return@delete
            }
            UserService.deleteUserGroupFromUser(user.id)
            call.respond(HttpStatusCode.OK)

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