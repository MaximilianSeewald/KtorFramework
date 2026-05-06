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
import java.io.File

@Serializable
data class LovelaceResourceRequest(val ingressBaseUrl: String)

@Serializable
data class LovelaceResourceStatus(val id: String, val type: String, val url: String)

@Serializable
data class LovelaceResourceCheckResponse(
    val published: Boolean,
    val publishedPath: String,
    val resources: List<LovelaceResourceStatus>
)

class HomeAssistantLovelaceResourceManager {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun installOrUpdateFromEnvironment(): String {
        if (!HomeAssistantMode.enabled) {
            return "Home Assistant mode is disabled"
        }

        val token = HomeAssistantMode.supervisorToken
            ?: throw IllegalStateException("SUPERVISOR_TOKEN is not available")
        val ingressBaseUrl = HomeAssistantMode.ingressBaseUrl
            ?: throw IllegalStateException("Home Assistant ingress URL is not available")

        publishCardResource(ingressBaseUrl)
        return installOrUpdateResource(HomeAssistantMode.localLovelaceResourceUrl, token)
    }

    fun initRoutes(route: Route) {
        route.get("/ha/lovelace-resource") {
            if (!HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val token = HomeAssistantMode.supervisorToken
            if (token == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "SUPERVISOR_TOKEN is not available"))
                return@get
            }

            runCatching {
                LovelaceResourceCheckResponse(
                    published = publishedCardResourceFile().isFile,
                    publishedPath = publishedCardResourceFile().absolutePath,
                    resources = listKtorResources(token)
                )
            }.onSuccess { response ->
                call.respond(HttpStatusCode.OK, response)
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
            if (!request.ingressBaseUrl.contains("/api/hassio_ingress/")) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Unsupported ingress URL"))
                return@post
            }

            val token = HomeAssistantMode.supervisorToken
            if (token == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "SUPERVISOR_TOKEN is not available"))
                return@post
            }

            runCatching {
                publishCardResource(request.ingressBaseUrl)
                installOrUpdateResource(HomeAssistantMode.localLovelaceResourceUrl, token)
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

    private fun publishCardResource(ingressBaseUrl: String) {
        val source = File("app/browser/ktor-lovelace-cards.js")
        if (!source.isFile) {
            throw IllegalStateException("Lovelace card module was not found in the add-on")
        }

        val targetDirectory = File("/config/www")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw IllegalStateException("Could not create /config/www")
        }

        val target = publishedCardResourceFile()
        val normalizedIngressBaseUrl = ingressBaseUrl.replace(Regex("/?$"), "/")
        target.writeText(
            source.readText()
                .replace("__KTOR_INGRESS_BASE_URL__", normalizedIngressBaseUrl),
            Charsets.UTF_8
        )
    }

    private fun publishedCardResourceFile(): File =
        File("/config/www/ktor-lovelace-cards.js")

    private suspend fun installOrUpdateResource(resourceUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val connection = HomeAssistantCoreWebSocket(token, json)
        try {
            connection.connect()
            val resources = connection.listLovelaceResources()

            val existing = resources.firstOrNull { resource ->
                val url = resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
                url.contains("ktor-lovelace-cards.js")
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

    private suspend fun listKtorResources(token: String): List<LovelaceResourceStatus> = withContext(Dispatchers.IO) {
        val connection = HomeAssistantCoreWebSocket(token, json)
        try {
            connection.connect()
            connection.listLovelaceResources()
                .filter { resource ->
                    resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull?.contains("ktor-lovelace-cards.js") == true
                }
                .map { resource ->
                    LovelaceResourceStatus(
                        id = (resource.jsonObject["id"] ?: resource.jsonObject["resource_id"]).toString(),
                        type = (resource.jsonObject["type"] ?: resource.jsonObject["res_type"]).toString(),
                        url = resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
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
