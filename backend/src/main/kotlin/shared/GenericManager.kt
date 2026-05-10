package com.loudless.shared

import com.loudless.users.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.slf4j.LoggerFactory

/**
 * Base manager for group-based resources with common route patterns
 */
abstract class GenericManager<T> {
    private val LOGGER = LoggerFactory.getLogger(GenericManager::class.java)

    protected val observerList: MutableMap<String, MutableSharedFlow<List<T>>> = mutableMapOf()

    protected suspend fun retrieveUserGroupsAndHandleErrors(call: ApplicationCall): List<String> {
        LOGGER.info("Retrieving user groups for group-based request")
        val groups = UserService.getUserGroupsByPrincipal(call)
        return when {
            groups.isEmpty() -> {
                LOGGER.warn("Rejected group-based request because no user group was found")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No user found with this username"))
                emptyList()
            }
            groups.size > 1 -> {
                LOGGER.warn("Rejected group-based request because multiple user groups were found")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Too many users found"))
                emptyList()
            }
            else -> {
                LOGGER.info("Resolved group {} for group-based request", groups[0])
                groups
            }
        }
    }

    protected fun emitUpdate(groups: List<String>, dataRetriever: (String) -> List<T>) {
        if (groups.isEmpty()) return

        LOGGER.info("Emitting resource update for group {}", groups[0])
        val usersForGroup = UserService.getUsersForGroup(groups[0])
        val observersForGroup = observerList.filter { usersForGroup.contains(it.key) }

        if (observersForGroup.isNotEmpty()) {
            val data = dataRetriever(groups[0])
            observersForGroup.forEach {
                it.value.tryEmit(data)
            }
            LOGGER.info("Emitted resource update for {} observers in group {}", observersForGroup.size, groups[0])
        } else {
            LOGGER.info("No active observers for group {}", groups[0])
        }
    }
}


