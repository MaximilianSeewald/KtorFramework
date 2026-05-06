package com.loudless.homeassistant

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LovelaceResourceRequest(val url: String)

class HomeAssistantLovelaceResourceManager {
    private val json = Json { ignoreUnknownKeys = true }

    fun initRoutes(route: Route) {
        route.get("/ha/lovelace-resource") {
            if (!HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val token = System.getenv("SUPERVISOR_TOKEN")
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "SUPERVISOR_TOKEN is not available"))
                return@get
            }

            runCatching {
                listKtorResources(token)
            }.onSuccess { resources ->
                call.respond(HttpStatusCode.OK, mapOf("resources" to resources))
            }.onFailure { error ->
                call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("message" to (error.message ?: "Could not read Lovelace resources"))
                )
            }
        }

        route.post("/ha/lovelace-resource") {
            if (!HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val request = call.receive<LovelaceResourceRequest>()
            if (!request.url.contains("ktor-lovelace-cards.js")) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Unsupported Lovelace resource URL"))
                return@post
            }

            val token = System.getenv("SUPERVISOR_TOKEN")
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "SUPERVISOR_TOKEN is not available"))
                return@post
            }

            runCatching {
                installOrUpdateResource(request.url, token)
            }.onSuccess { result ->
                call.respond(HttpStatusCode.OK, mapOf("message" to result))
            }.onFailure { error ->
                call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("message" to (error.message ?: "Could not install Lovelace resource"))
                )
            }
        }
    }

    private suspend fun installOrUpdateResource(resourceUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val connection = HomeAssistantCoreWebSocket(token, json)
        try {
            connection.connect()
            val resources = connection.listLovelaceResources()

            val existing = resources.firstOrNull { resource ->
                val url = resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
                url.substringBefore("?") == resourceUrl.substringBefore("?")
            }

            if (existing == null) {
                createResource(connection, resourceUrl)
                "Lovelace resource installed"
            } else {
                updateExistingResource(connection, existing, resourceUrl)
            }
        } finally {
            connection.close()
        }
    }

    private suspend fun listKtorResources(token: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val connection = HomeAssistantCoreWebSocket(token, json)
        try {
            connection.connect()
            connection.listLovelaceResources()
                .filter { resource ->
                    resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull?.contains("ktor-lovelace-cards.js") == true
                }
                .map { resource ->
                    mapOf(
                        "id" to (resource.jsonObject["id"] ?: resource.jsonObject["resource_id"]).toString(),
                        "type" to (resource.jsonObject["type"] ?: resource.jsonObject["res_type"]).toString(),
                        "url" to (resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: "")
                    )
                }
        } finally {
            connection.close()
        }
    }

    private fun HomeAssistantCoreWebSocket.listLovelaceResources(): JsonArray =
        command("lovelace/resources")
            .jsonObject["result"]
            ?.jsonArray
            ?: JsonArray(emptyList())

    private fun createResource(connection: HomeAssistantCoreWebSocket, resourceUrl: String) {
        connection.command(
            "lovelace/resources/create",
            mapOf(
                "res_type" to "module",
                "url" to resourceUrl
            )
        )
    }

    private fun updateExistingResource(
        connection: HomeAssistantCoreWebSocket,
        existing: JsonElement,
        resourceUrl: String
    ): String {
        val existingUrl = existing.jsonObject["url"]?.jsonPrimitive?.contentOrNull
        if (existingUrl == resourceUrl) {
            return "Lovelace resource is already installed"
        }

        val resourceId = existing.jsonObject["id"] ?: existing.jsonObject["resource_id"]
            ?: throw IllegalStateException("Existing Lovelace resource has no id")
        connection.command(
            "lovelace/resources/update",
            mapOf(
                "resource_id" to resourceId,
                "res_type" to "module",
                "url" to resourceUrl
            )
        )
        return "Lovelace resource updated"
    }
}
