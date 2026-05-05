package com.loudless.userGroups

import com.loudless.HomeAssistantMode
import com.loudless.database.DatabaseManager
import com.loudless.models.CreateUserGroupRequest
import com.loudless.models.EditUserGroupRequest
import com.loudless.recipes.RecipeService
import com.loudless.shoppingList.ShoppingListService
import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class UserGroupManager {

    fun initSafeRoutes(route: Route) {
        route.postUserGroup()
        route.deleteUserGroup()
        route.getUserGroupAdmin()
        route.editPasswordUserGroup()
    }

    private fun Route.editPasswordUserGroup() {
        put("/usergroups") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@put
            }
            val editUserGroupRequest = call.receive<EditUserGroupRequest>()
            val user = UserService.retrieveAndHandleUsers(call)[0]
            if (!UserGroupService.checkIsAdmin(user)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not admin"))
                return@put
            }
            UserGroupService.updatePassword(editUserGroupRequest.userGroupName, editUserGroupRequest.newPassword)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.postUserGroup() {
        post("/usergroups") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@post
            }
            val createUserGroupRequest = call.receive<CreateUserGroupRequest>()
            val userId = UserService.retrieveAndHandleUsers(call)[0].id
            val userGroupName = createUserGroupRequest.userGroupName
            if (UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User group already exists"))
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
        }
    }

    private fun Route.deleteUserGroup() {
        delete("/usergroups") {
            if (HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "User group changes are disabled for Home Assistant"))
                return@delete
            }
            val userId = UserService.retrieveAndHandleUsers(call)[0].id
            val userGroupName = call.request.queryParameters["name"]!!
            if (!UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User group does not exist"))
                return@delete
            }
            val success = UserGroupService.checkOwnershipAndDeleteUserGroup(
                userGroupName,
                userId
            )
            if (!success) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not the owner of the group"))
                return@delete
            }
            UserService.deleteUserGroupFromAllUsers(userGroupName)
            ShoppingListService.deleteShoppingList(userGroupName)
            RecipeService.deleteRecipeList(userGroupName)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.getUserGroupAdmin() {
        get("/usergroups/admin") {
            val user = UserService.retrieveAndHandleUsers(call)[0]
            call.respond(UserGroupService.checkIsAdmin(user))
        }
    }
}
