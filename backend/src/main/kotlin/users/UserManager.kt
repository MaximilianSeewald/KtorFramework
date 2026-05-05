package com.loudless.users

import com.loudless.HomeAssistantMode
import com.loudless.auth.AuthTokenService
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

class UserManager {
    fun initRouting(routing: Route) {
        routing.login()
        routing.homeAssistantSession()
        routing.userSignUp()
    }

    fun initSafeRoutes(routing: Route) {
        routing.getUserInformation()
        routing.joinUserGroup()
        routing.leaveUserGroup()
        routing.userChangePassword()
    }

    private fun Route.getUserInformation() {
        get("/user") {
            call.respond(UserService.retrieveAndHandleUsers(call)[0])
        }
    }

    private fun Route.userSignUp() {
        post("/user") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User registration is disabled for Home Assistant"))
                return@post
            }
            val parameters = call.receiveParameters()
            val username: String = parameters["username"] ?: ""
            val password: String = parameters["password"] ?: ""
            if (username == "" || password == "") {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Username or password is empty"))
                return@post
            }
            if (UserService.userExists(username)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User already exists"))
                return@post
            }
            UserService.addUser(username, password)
            call.respond(HttpStatusCode.Created)
        }
    }

    private fun Route.userChangePassword() {
        post("/user/{userName}/password") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Password changes are disabled for Home Assistant"))
                return@post
            }
            val user = UserService.retrieveAndHandleUsers(call)[0]
            val userNameFromRoute = call.parameters["userName"]!!
            val parameters = call.receiveParameters()
            val currentPassword: String = parameters["oldPassword"] ?: ""
            val newPassword: String = parameters["newPassword"] ?: ""
            if (user.name != userNameFromRoute) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not match"))
                return@post
            }
            if (!UserService.verifyUserPassword(user.id, currentPassword)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Current password is incorrect"))
                return@post
            }
            if (newPassword == "") {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "New password is empty"))
                return@post
            }
            UserService.updatePassword(user.id, newPassword)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.joinUserGroup() {
        post("/user/{userName}/groups") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@post
            }
            val user = UserService.retrieveAndHandleUsers(call)[0]
            val userFromRoute = call.parameters["userName"]!!
            val joinUserGroupRequest = call.receive<JoinUserGroupRequest>()
            val password = joinUserGroupRequest.password
            val userGroupName = joinUserGroupRequest.userGroupName
            if (user.name != userFromRoute) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not match"))
                return@post
            }
            if (user.userGroup != null && user.userGroup != "") {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is already in a group"))
                return@post
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User group does not exist"))
                return@post
            }
            if (!UserGroupService.checkPassword(userGroupName, password)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Incorrect password"))
                return@post
            }
            if (user.userGroup == userGroupName) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is already in the group"))
                return@post
            }
            UserService.addUserGroupToUser(user.id, userGroupName)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.leaveUserGroup() {
        delete("/user/{userName}/groups/{groupName}") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@delete
            }
            val user = UserService.retrieveAndHandleUsers(call)[0]
            val userFromRoute = call.parameters["userName"]!!
            val userGroupName = call.parameters["groupName"]!!
            if (user.name != userFromRoute) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not match"))
                return@delete
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User group does not exist"))
                return@delete
            }
            if (UserGroupService.checkIsAdmin(user)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Admin cannot leave group"))
                return@delete
            }
            if (user.userGroup != userGroupName) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not in the group"))
                return@delete
            }
            UserService.deleteUserGroupFromUser(user.id)
            call.respond(HttpStatusCode.OK)

        }
    }

    private fun Route.login() {
        post("/login") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Password login is disabled for Home Assistant"))
                return@post
            }
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
                call.respond(HttpStatusCode.OK, mapOf("token" to AuthTokenService.createToken(username)))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Login failed. Please check your credentials."))
            }
        }
    }

    private fun Route.homeAssistantSession() {
        get("/ha/session") {
            if (!HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(HttpStatusCode.OK, mapOf("token" to AuthTokenService.createToken(HomeAssistantMode.userName)))
        }
    }
}
