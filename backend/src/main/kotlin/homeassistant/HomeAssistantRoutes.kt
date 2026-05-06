package com.loudless.homeassistant

import kotlinx.coroutines.runBlocking
import io.ktor.server.routing.Route

class HomeAssistantRoutes {
    private val lovelaceResourceManager = HomeAssistantLovelaceResourceManager()

    fun initRoutes(route: Route) {
        lovelaceResourceManager.initRoutes(route)
    }

    fun installStartupResources() {
        if (!HomeAssistantMode.enabled) {
            return
        }
        if (HomeAssistantMode.ingressBaseUrl == null) {
            println("Home Assistant Lovelace resource startup sync skipped: ingress URL is not available yet")
            return
        }

        runCatching {
            runBlocking {
                lovelaceResourceManager.installOrUpdateFromEnvironment()
            }
        }.onSuccess { result ->
            println("Home Assistant Lovelace resource startup sync: $result")
        }.onFailure { error ->
            println("Home Assistant Lovelace resource startup sync failed: ${error.message}")
        }
    }
}
