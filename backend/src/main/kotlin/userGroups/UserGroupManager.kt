package com.loudless.userGroups

import com.loudless.database.DatabaseManager
import com.loudless.database.UserGroups
import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll

class UserGroupManager {

    fun initRoutes(route: Route) {
        route.postUserGroup()
        route.deleteUserGroup()
        route.getUserGroupAdmin()
    }

    private fun Route.postUserGroup() {
        post("/usergroups") {
            val createUserGroupRequest = call.receive<CreateUserGroupRequest>()
            if (UserGroupService.userGroupExists(createUserGroupRequest.userGroupName)) {
                call.respond(HttpStatusCode.BadRequest, "User group already exists")
                return@post
            }
            UserGroupService.addUserGroup(
                createUserGroupRequest.userGroupName,
                DatabaseManager.hashPassword(createUserGroupRequest.password),
                UserService.retrieveAndHandleUsers(call)[0].id
            )
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.deleteUserGroup() {
        delete("/usergroups") {
            val userGroupName = call.request.queryParameters["name"]!!
            if (!UserGroupService.userGroupExists(userGroupName)) {
                call.respond(HttpStatusCode.BadRequest, "User group does not exist")
                return@delete
            }
            val success = UserGroupService.checkOwnershipAndDeleteUserGroup(
                userGroupName,
                UserService.retrieveAndHandleUsers(call)[0].id
            )
            if (!success) {
                call.respond(HttpStatusCode.BadRequest, "User is not the owner of the group")
                return@delete
            }
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.getUserGroupAdmin() {
        get("/usergroups/admin") {
            val userGroup = call.request.queryParameters["name"]!!
            val user = UserService.retrieveAndHandleUsers(call)[0].id
            val isAdmin = UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroup }
                .map { it[UserGroups.adminUserId] }
                .firstOrNull() == user
            call.respond(isAdmin)
        }
    }
}