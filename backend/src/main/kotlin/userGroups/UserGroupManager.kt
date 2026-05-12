package com.loudless.userGroups

import com.loudless.auth.CredentialPolicy
import com.loudless.database.DatabaseManager
import com.loudless.homeassistant.HomeAssistantMode
import com.loudless.models.CreateUserGroupRequest
import com.loudless.models.EditUserGroupRequest
import com.loudless.recipes.RecipeService
import com.loudless.shoppingList.ShoppingListService
import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

class UserGroupManager {
    private val LOGGER = LoggerFactory.getLogger(UserGroupManager::class.java)

    fun initSafeRoutes(route: Route) {
        route.postUserGroup()
        route.deleteUserGroup()
        route.getUserGroupAdmin()
        route.editPasswordUserGroup()
    }

    private fun Route.editPasswordUserGroup() {
        put("/usergroups") {
            LOGGER.info("Handling edit user group password request")
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected user group password edit because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@put
            }
            val editUserGroupRequest = call.receive<EditUserGroupRequest>()
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@put
            val user = users[0]
            if (!UserGroupService.checkIsAdmin(user)) {
                LOGGER.warn("Rejected user group password edit because user {} is not admin", user.id)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not admin"))
                return@put
            }
            val passwordError = CredentialPolicy.passwordValidationMessage(editUserGroupRequest.newPassword)
            if (passwordError != null) {
                LOGGER.warn("Rejected user group password edit because new password did not meet policy")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to passwordError))
                return@put
            }
            UserGroupService.updatePassword(editUserGroupRequest.userGroupName, editUserGroupRequest.newPassword)
            call.respond(HttpStatusCode.OK)
            LOGGER.info("Updated password for user group {}", editUserGroupRequest.userGroupName)
        }
    }

    private fun Route.postUserGroup() {
        post("/usergroups") {
            LOGGER.info("Handling create user group request")
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected create user group because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@post
            }
            val createUserGroupRequest = call.receive<CreateUserGroupRequest>()
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@post
            val userId = users[0].id
            val userGroupName = createUserGroupRequest.userGroupName
            if (UserGroupService.userGroupExists(userGroupName)) {
                LOGGER.warn("Rejected create user group because group {} already exists", userGroupName)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User group already exists"))
                return@post
            }
            val passwordError = CredentialPolicy.passwordValidationMessage(createUserGroupRequest.password)
            if (passwordError != null) {
                LOGGER.warn("Rejected create user group because password did not meet policy")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to passwordError))
                return@post
            }
            UserGroupService.addUserGroup(
                userGroupName,
                DatabaseManager.hashPassword(createUserGroupRequest.password),
                userId
            )
            UserService.addUserGroupToUser(userId, userGroupName)
            ShoppingListService.addShoppingList(userGroupName)
            RecipeService.addRecipeList(userGroupName)
            call.respond(HttpStatusCode.OK)
            LOGGER.info("Created user group {} for user {}", userGroupName, userId)
        }
    }

    private fun Route.deleteUserGroup() {
        delete("/usergroups") {
            LOGGER.info("Handling delete user group request")
            if (HomeAssistantMode.enabled) {
                LOGGER.warn("Rejected delete user group because Home Assistant mode is enabled")
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@delete
            }
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@delete
            val userId = users[0].id
            val userGroupName = call.request.queryParameters["name"]
            if (userGroupName.isNullOrBlank()) {
                LOGGER.warn("Rejected delete user group because name query parameter was missing")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User group name is missing"))
                return@delete
            }
            if (!UserGroupService.userGroupExists(userGroupName)) {
                LOGGER.warn("Rejected delete user group because group {} does not exist", userGroupName)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User group does not exist"))
                return@delete
            }
            val success = UserGroupService.checkOwnershipAndDeleteUserGroup(
                userGroupName,
                userId
            )
            if (!success) {
                LOGGER.warn("Rejected delete user group {} because user {} is not owner", userGroupName, userId)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not the owner of the group"))
                return@delete
            }
            UserService.deleteUserGroupFromAllUsers(userGroupName)
            ShoppingListService.deleteShoppingList(userGroupName)
            RecipeService.deleteRecipeList(userGroupName)
            call.respond(HttpStatusCode.OK)
            LOGGER.info("Deleted user group {}", userGroupName)
        }
    }

    private fun Route.getUserGroupAdmin() {
        get("/usergroups/admin") {
            LOGGER.info("Handling get user group admin request")
            val users = UserService.retrieveAndHandleUsers(call)
            if (users.isEmpty()) return@get
            val user = users[0]
            val isAdmin = UserGroupService.checkIsAdmin(user)
            call.respond(isAdmin)
            LOGGER.info("Returned admin status for user {}: {}", user.id, isAdmin)
        }
    }
}
