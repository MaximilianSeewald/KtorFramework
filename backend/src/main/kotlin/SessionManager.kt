package com.loudless

import com.loudless.grades.GradeManager
import com.loudless.shoppingList.ShoppingListManager
import com.loudless.users.UserManager
import io.ktor.server.application.*
import io.ktor.server.routing.*

object SessionManager {

    val secretJWTKey = System.getenv("JWT_SECRET_KEY") ?: throw IllegalStateException("JWT_SECRET_KEY not set")

    private val ktorManager = KtorManager()
    private val shoppingListManager = ShoppingListManager()
    private val gradeManager = GradeManager()
    private val userManager = UserManager()

    fun initRouting(routing: Routing) {
        gradeManager.initRouting(routing)
        userManager.initRouting(routing)
        shoppingListManager.initQueryRoutes(routing)
    }

    fun initSafeRoutes(route: Route) {
        shoppingListManager.initRoutes(route)
    }

    fun installComponents(application: Application) {
        ktorManager.installComponents(application)
    }
}