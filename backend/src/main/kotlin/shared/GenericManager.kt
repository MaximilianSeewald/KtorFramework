package com.loudless.shared

import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Base manager for group-based resources with common route patterns
 */
abstract class GenericManager<T> {

    protected val observerList: MutableMap<String, MutableSharedFlow<List<T>>> = mutableMapOf()

    protected suspend fun retrieveUserGroupsAndHandleErrors(call: ApplicationCall): List<String> {
        val groups = UserService.getUserGroupsByPrincipal(call)
        return when {
            groups.isEmpty() -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No user found with this username"))
                emptyList()
            }
            groups.size > 1 -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Too many users found"))
                emptyList()
            }
            else -> groups
        }
    }

    protected fun emitUpdate(groups: List<String>, dataRetriever: (String) -> List<T>) {
        if (groups.isEmpty()) return

        val usersForGroup = UserService.getUsersForGroup(groups[0])
        if (observerList.any { usersForGroup.contains(it.key) }) {
            observerList
                .filter { usersForGroup.contains(it.key) }
                .forEach {
                    it.value.tryEmit(dataRetriever(groups[0]))
                }
        }
    }
}


