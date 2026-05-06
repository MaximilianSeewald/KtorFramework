package com.loudless

import com.loudless.grades.GradeManager
import com.loudless.homeassistant.HomeAssistantRoutes
import com.loudless.recipes.RecipeManager
import com.loudless.shoppingList.ShoppingListManager
import com.loudless.userGroups.UserGroupManager
import com.loudless.users.UserManager
import io.ktor.server.application.*
import io.ktor.server.routing.*

object SessionManager {

    val secretJWTKey = System.getenv("JWT_SECRET_KEY") ?: throw IllegalStateException("JWT_SECRET_KEY not set")

    private val ktorManager = KtorManager()
    private val shoppingListManager = ShoppingListManager()
    private val recipeManager = RecipeManager()
    private val gradeManager = GradeManager()
    private val userManager = UserManager()
    private val userGroupManager = UserGroupManager()
    private val homeAssistantRoutes = HomeAssistantRoutes()

    fun initRouting(routing: Route) {
        gradeManager.initRouting(routing)
        userManager.initRouting(routing)
        shoppingListManager.initQueryRoutes(routing)
        recipeManager.initQueryRoutes(routing)
    }

    fun initSafeRoutes(route: Route) {
        shoppingListManager.initRoutes(route)
        recipeManager.initRoutes(route)
        userManager.initSafeRoutes(route)
        userGroupManager.initSafeRoutes(route)
        homeAssistantRoutes.initRoutes(route)
    }

    fun installComponents(application: Application) {
        ktorManager.installComponents(application)
    }

    fun installStartupResources() {
        homeAssistantRoutes.installStartupResources()
    }
}
