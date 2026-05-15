package com.loudless

import com.loudless.recipes.RecipeManager
import com.loudless.shoppingList.ShoppingListManager
import com.loudless.userGroups.UserGroupManager
import com.loudless.users.UserManager
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

object SessionManager {
    private val LOGGER = LoggerFactory.getLogger(SessionManager::class.java)

    private val ktorManager = KtorManager()
    private val shoppingListManager = ShoppingListManager()
    private val recipeManager = RecipeManager(shoppingListManager)
    private val userManager = UserManager()
    private val userGroupManager = UserGroupManager()

    fun initRouting(routing: Route) {
        LOGGER.info("Initializing public API routes")
        userManager.initRouting(routing)
        shoppingListManager.initQueryRoutes(routing)
        recipeManager.initQueryRoutes(routing)
        LOGGER.info("Public API routes initialized")
    }

    fun initSafeRoutes(route: Route) {
        LOGGER.info("Initializing authenticated API routes")
        shoppingListManager.initRoutes(route)
        recipeManager.initRoutes(route)
        userManager.initSafeRoutes(route)
        userGroupManager.initSafeRoutes(route)
        LOGGER.info("Authenticated API routes initialized")
    }

    fun installComponents(application: Application) {
        LOGGER.info("Installing session components")
        ktorManager.installComponents(application)
    }
}
