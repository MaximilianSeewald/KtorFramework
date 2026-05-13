package com.loudless.users

import com.loudless.auth.AuthTokenService
import com.loudless.auth.CredentialPolicy
import com.loudless.database.DatabaseManager
import com.loudless.database.Users
import com.loudless.homeassistant.HomeAssistantMode
import com.loudless.models.JoinUserGroupRequest
import com.loudless.models.LoginRequest
import com.loudless.shared.RateLimiter
import com.loudless.userGroups.UserGroupService
import com.loudless.userGroups.UserGroupNameValidator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class UserManager {
    private val LOGGER = LoggerFactory.getLogger(UserManager::class.java)

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
            LOGGER.info("Handling get user information request")
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@get
            call.respond(users[0])
            LOGGER.info("Returned user information")
        }
    }

    private fun Route.userSignUp() {
        post("/user") {
            LOGGER.info("Handling user signup request")
            if (!RateLimiter.check(call, "user-signup")) return@post
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected user signup because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User registration is disabled for Home Assistant"))
                return@post
            }
            val parameters = call.receiveParameters()
            val username: String = parameters["username"] ?: ""
            val password: String = parameters["password"] ?: ""
            if (username == "" || password == "") {
                LOGGER.warn("Rejected user signup because username or password was empty")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Username or password is empty"))
                return@post
            }
            val passwordError = CredentialPolicy.passwordValidationMessage(password)
            if (passwordError != null) {
                LOGGER.warn("Rejected user signup because password did not meet policy")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to passwordError))
                return@post
            }
            if (UserService.userExists(username)) {
                LOGGER.warn("Rejected user signup because user {} already exists", username)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User already exists"))
                return@post
            }
            UserService.addUser(username, password)
            call.respond(HttpStatusCode.Created)
            LOGGER.info("Created user {}", username)
        }
    }

    private fun Route.userChangePassword() {
        post("/user/{userName}/password") {
            LOGGER.info("Handling password change request")
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected password change because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Password changes are disabled for Home Assistant"))
                return@post
            }
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@post
            val user = users[0]
            val userNameFromRoute = call.parameters["userName"]!!
            val parameters = call.receiveParameters()
            val currentPassword: String = parameters["oldPassword"] ?: ""
            val newPassword: String = parameters["newPassword"] ?: ""
            if (user.name != userNameFromRoute) {
                LOGGER.warn("Rejected password change because route user did not match authenticated user")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not match"))
                return@post
            }
            if (!UserService.verifyUserPassword(user.id, currentPassword)) {
                LOGGER.warn("Rejected password change for user {} because current password did not verify", user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Current password is incorrect"))
                return@post
            }
            if (newPassword == "") {
                LOGGER.warn("Rejected password change for user {} because new password was empty", user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "New password is empty"))
                return@post
            }
            val passwordError = CredentialPolicy.passwordValidationMessage(newPassword)
            if (passwordError != null) {
                LOGGER.warn("Rejected password change for user {} because new password did not meet policy", user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to passwordError))
                return@post
            }
            UserService.updatePassword(user.id, newPassword)
            call.respond(HttpStatusCode.OK)
            LOGGER.info("Changed password for user {}", user.id)
        }
    }

    private fun Route.joinUserGroup() {
        post("/user/{userName}/groups") {
            LOGGER.info("Handling join user group request")
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected join user group because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@post
            }
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@post
            val user = users[0]
            val userFromRoute = call.parameters["userName"]!!
            val joinUserGroupRequest = call.receive<JoinUserGroupRequest>()
            val password = joinUserGroupRequest.password
            val userGroupName = joinUserGroupRequest.userGroupName
            UserGroupNameValidator.validationMessage(userGroupName)?.let {
                LOGGER.warn("Rejected join user group because group name was invalid")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to it))
                return@post
            }
            if (user.name != userFromRoute) {
                LOGGER.warn("Rejected join user group because route user did not match authenticated user")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not match"))
                return@post
            }
            if (user.userGroup != null && user.userGroup != "") {
                LOGGER.warn("Rejected join user group for user {} because they already have a group", user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is already in a group"))
                return@post
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                LOGGER.warn("Rejected join user group because group {} does not exist", userGroupName)
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User group does not exist"))
                return@post
            }
            if (!RateLimiter.check(call, "group-password-check")) return@post
            if (!UserGroupService.checkPassword(userGroupName, password)) {
                LOGGER.warn("Rejected join user group {} for user {} because password did not verify", userGroupName, user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Incorrect password"))
                return@post
            }
            if (user.userGroup == userGroupName) {
                LOGGER.warn("Rejected join user group because user {} is already in group {}", user.id, userGroupName)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is already in the group"))
                return@post
            }
            UserService.addUserGroupToUser(user.id, userGroupName)
            call.respond(HttpStatusCode.OK)
            LOGGER.info("User {} joined group {}", user.id, userGroupName)
        }
    }

    private fun Route.leaveUserGroup() {
        delete("/user/{userName}/groups/{groupName}") {
            LOGGER.info("Handling leave user group request")
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected leave user group because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@delete
            }
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@delete
            val user = users[0]
            val userFromRoute = call.parameters["userName"]!!
            val userGroupName = call.parameters["groupName"]!!
            UserGroupNameValidator.validationMessage(userGroupName)?.let {
                LOGGER.warn("Rejected leave user group because group name was invalid")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to it))
                return@delete
            }
            if (user.name != userFromRoute) {
                LOGGER.warn("Rejected leave user group because route user did not match authenticated user")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not match"))
                return@delete
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                LOGGER.warn("Rejected leave user group because group {} does not exist", userGroupName)
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User group does not exist"))
                return@delete
            }
            if (UserGroupService.checkIsAdmin(user)) {
                LOGGER.warn("Rejected leave user group because user {} is admin", user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Admin cannot leave group"))
                return@delete
            }
            if (user.userGroup != userGroupName) {
                LOGGER.warn("Rejected leave user group because user {} is not in group {}", user.id, userGroupName)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not in the group"))
                return@delete
            }
            UserService.deleteUserGroupFromUser(user.id)
            call.respond(HttpStatusCode.OK)
            LOGGER.info("User {} left group {}", user.id, userGroupName)

        }
    }

    private fun Route.login() {
        post("/login") {
            LOGGER.info("Handling login request")
            if (!RateLimiter.check(call, "login")) return@post
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected password login because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Password login is disabled for Home Assistant"))
                return@post
            }
            val body = call.receive<LoginRequest>()
            val username: String = body.username
            val password: String = body.password
            val user = transaction {
                LOGGER.info("Looking up login user {}", username)
                val foundUser = Users
                    .selectAll().where { Users.name eq username }
                    .map { it[Users.hashedPassword] to it[Users.name] }
                    .firstOrNull()
                LOGGER.info("Login user {} found: {}", username, foundUser != null)
                foundUser
            }

            if (user != null && DatabaseManager.verifyPassword(password, user.first)) {
                call.respond(HttpStatusCode.OK, mapOf("token" to AuthTokenService.createToken(username)))
                LOGGER.info("User {} logged in successfully", username)
            } else {
                LOGGER.warn("Login failed for user {}", username)
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Login failed. Please check your credentials."))
            }
        }
    }

    private fun Route.homeAssistantSession() {
        get("/ha/session") {
            LOGGER.info("Handling Home Assistant session request")
            if (!RateLimiter.check(call, "ha-session")) return@get
            if (!HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected Home Assistant session because Home Assistant mode is disabled")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(HttpStatusCode.OK, mapOf("token" to AuthTokenService.createToken(HomeAssistantMode.userName)))
            LOGGER.info("Created Home Assistant session token")
        }
    }
}

