package com.loudless.homeassistant

import io.ktor.server.routing.Route

class HomeAssistantRoutes {
    private val lovelaceResourceManager = HomeAssistantLovelaceResourceManager()

    fun initRoutes(route: Route) {
        lovelaceResourceManager.initRoutes(route)
    }
}
