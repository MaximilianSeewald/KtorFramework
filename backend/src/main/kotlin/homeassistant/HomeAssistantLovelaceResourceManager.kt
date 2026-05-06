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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
data class LovelaceResourceRequest(val ingressBaseUrl: String)

@Serializable
data class LovelaceResourceStatus(val id: String, val type: String, val url: String)

@Serializable
data class LovelaceResourceCheckResponse(
    val published: Boolean,
    val publishedPath: String,
    val served: Boolean,
    val servedStatus: Int?,
    val frontendExtraModule: Boolean,
    val frontendExtraModulePath: String,
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

    fun publishCardResourceFromEnvironment(): String {
        if (!HomeAssistantMode.enabled) {
            return "Home Assistant mode is disabled"
        }

        publishCardResource(HomeAssistantMode.ingressBaseUrl)
        return "Lovelace card resource published"
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
                    served = isPublishedResourceServed(token),
                    servedStatus = publishedResourceStatus(token),
                    frontendExtraModule = isFrontendExtraModuleConfigured(),
                    frontendExtraModulePath = HomeAssistantMode.configurationFilePath,
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
                val resourceResult = installOrUpdateResource(HomeAssistantMode.localLovelaceResourceUrl, token)
                val frontendResult = ensureFrontendExtraModule()
                "$resourceResult; $frontendResult"
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

    private fun publishCardResource(ingressBaseUrl: String?) {
        val source = File("app/browser/ktor-lovelace-cards.js")
        if (!source.isFile) {
            throw IllegalStateException("Lovelace card module was not found in the add-on")
        }

        val targetDirectory = homeAssistantWwwDirectory()
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw IllegalStateException("Could not create /homeassistant/www")
        }

        val target = publishedCardResourceFile()
        val cardModule = source.readText()
        val normalizedIngressBaseUrl = ingressBaseUrl?.replace(Regex("/?$"), "/")
        val publishedCardModule = if (normalizedIngressBaseUrl == null) {
            cardModule
        } else {
            cardModule.replace("__KTOR_INGRESS_BASE_URL__", normalizedIngressBaseUrl)
        }
        target.writeText(publishedCardModule, Charsets.UTF_8)
        ensureFrontendExtraModule()
    }

    private fun publishedCardResourceFile(): File =
        File(homeAssistantWwwDirectory(), HomeAssistantMode.lovelaceCardFileName)

    private fun homeAssistantWwwDirectory(): File =
        File("/homeassistant/www")

    private fun isFrontendExtraModuleConfigured(): Boolean {
        val configuration = File(HomeAssistantMode.configurationFilePath)
        if (!configuration.isFile) {
            return false
        }

        return configuration.readLines(Charsets.UTF_8).any { line ->
            line.trim().trim('"', '\'') == "- ${HomeAssistantMode.localLovelaceResourceUrl}" ||
                line.trim().removePrefix("-").trim().trim('"', '\'') == HomeAssistantMode.localLovelaceResourceUrl
        }
    }

    private fun ensureFrontendExtraModule(): String {
        val configuration = File(HomeAssistantMode.configurationFilePath)
        configuration.parentFile?.mkdirs()

        if (!configuration.exists()) {
            configuration.writeText(frontendExtraModuleSection().joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
            return "Frontend extra module configured"
        }

        val originalLines = configuration.readLines(Charsets.UTF_8)
        if (isFrontendExtraModuleConfigured()) {
            return "Frontend extra module already configured"
        }

        val lines = originalLines.toMutableList()
        val frontendIndex = lines.indexOfFirst { line ->
            line.matches(Regex("""^frontend:\s*.*$"""))
        }

        if (frontendIndex == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines.add("")
            }
            lines.addAll(frontendExtraModuleSection())
            configuration.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
            return "Frontend extra module configured"
        }

        if (!lines[frontendIndex].matches(Regex("""^frontend:\s*(#.*)?$"""))) {
            return "Frontend extra module needs manual configuration"
        }

        val sectionEnd = lines.indexOfFirstAfter(frontendIndex + 1) { line ->
            line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")
        }.takeUnless { it == -1 } ?: lines.size

        val extraModuleIndex = (frontendIndex + 1 until sectionEnd).firstOrNull { index ->
            lines[index].matches(Regex("""^\s{2}extra_module_url:\s*(#.*)?$"""))
        }

        if (extraModuleIndex == null) {
            lines.add(frontendIndex + 1, "  extra_module_url:")
            lines.add(frontendIndex + 2, "    - ${HomeAssistantMode.localLovelaceResourceUrl}")
        } else {
            lines.add(extraModuleIndex + 1, "    - ${HomeAssistantMode.localLovelaceResourceUrl}")
        }

        configuration.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
        return "Frontend extra module configured"
    }

    private fun frontendExtraModuleSection(): List<String> =
        listOf(
            "frontend:",
            "  extra_module_url:",
            "    - ${HomeAssistantMode.localLovelaceResourceUrl}"
        )

    private inline fun List<String>.indexOfFirstAfter(startIndex: Int, predicate: (String) -> Boolean): Int {
        for (index in startIndex until size) {
            if (predicate(this[index])) {
                return index
            }
        }
        return -1
    }

    private fun isPublishedResourceServed(token: String): Boolean =
        publishedResourceStatus(token) == 200

    private fun publishedResourceStatus(token: String): Int? = runCatching {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${HomeAssistantMode.homeAssistantBaseUrl}/local/${HomeAssistantMode.lovelaceCardFileName}"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.discarding())
            .statusCode()
    }.getOrNull()

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
