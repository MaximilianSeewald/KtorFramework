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
            runCatching {
                lovelaceResourceManager.publishCardResourceFromEnvironment()
            }.onSuccess { result ->
                println("Home Assistant Lovelace resource startup sync: $result; ingress URL is not available yet")
            }.onFailure { error ->
                println("Home Assistant Lovelace resource startup sync failed: ${error.message}")
            }
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
