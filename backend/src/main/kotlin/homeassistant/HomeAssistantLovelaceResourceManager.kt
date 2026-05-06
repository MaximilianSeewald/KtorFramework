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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class LovelaceResourceRequest(val ingressBaseUrl: String)

@Serializable
data class LovelaceResourceStatus(val url: String)

@Serializable
data class LovelaceResourceCheckResponse(
    val published: Boolean,
    val frontendExtraModule: Boolean,
    val frontendExtraModulePath: String,
    val resourceUrl: String,
    val resources: List<LovelaceResourceStatus>
)

class HomeAssistantLovelaceResourceManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val wwwDirectory = File("/homeassistant/www")
    private val configurationFile = File(HomeAssistantMode.configurationFilePath)
    private val publishedCardFile = File(wwwDirectory, HomeAssistantMode.versionedLovelaceCardFileName)
    private val fallbackCardFile = File(wwwDirectory, HomeAssistantMode.fallbackLovelaceCardFileName)
    private val resourceUrls = listOf(
        HomeAssistantMode.localLovelaceResourceUrl,
        HomeAssistantMode.fallbackLovelaceResourceUrl
    )

    suspend fun installOrUpdateFromEnvironment(): String {
        val token = HomeAssistantMode.requireSupervisorToken()
        val ingressBaseUrl = HomeAssistantMode.ingressBaseUrl
            ?: throw IllegalStateException("Home Assistant ingress URL is not available")

        publishCardResource(ingressBaseUrl)
        return syncLovelaceResource(token)
    }

    fun publishCardResourceFromEnvironment(): String {
        publishCardResource(HomeAssistantMode.ingressBaseUrl)
        return "Lovelace card resource published"
    }

    fun initRoutes(route: Route) {
        route.get("/ha/lovelace-resource") {
            if (!HomeAssistantMode.enabled) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            runCatching {
                LovelaceResourceCheckResponse(
                    published = publishedCardFile.isFile,
                    frontendExtraModule = isFrontendExtraModuleConfigured(),
                    frontendExtraModulePath = configurationFile.absolutePath,
                    resourceUrl = HomeAssistantMode.localLovelaceResourceUrl,
                    resources = listKtorResources(HomeAssistantMode.requireSupervisorToken())
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

            runCatching {
                publishCardResource(request.ingressBaseUrl)
                syncLovelaceResource(HomeAssistantMode.requireSupervisorToken())
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
        if (!wwwDirectory.exists() && !wwwDirectory.mkdirs()) {
            throw IllegalStateException("Could not create /homeassistant/www")
        }

        removeStalePublishedCardFiles()
        val cardModule = source.readText().withIngressBaseUrl(ingressBaseUrl)
        publishedCardFile.writeText(cardModule, Charsets.UTF_8)
        fallbackCardFile.writeText(cardModule, Charsets.UTF_8)
        ensureFrontendExtraModule()
    }

    private fun String.withIngressBaseUrl(ingressBaseUrl: String?): String {
        val normalizedIngressBaseUrl = ingressBaseUrl?.replace(Regex("/+$"), "/") ?: return this
        return replace("__KTOR_INGRESS_BASE_URL__", normalizedIngressBaseUrl)
    }

    private fun removeStalePublishedCardFiles() {
        wwwDirectory
            .listFiles { file -> file.name.startsWith("ktor-lovelace-cards") && file.name.endsWith(".js") }
            ?.filterNot { file ->
                file.name in setOf(
                    HomeAssistantMode.versionedLovelaceCardFileName,
                    HomeAssistantMode.fallbackLovelaceCardFileName
                )
            }
            ?.forEach { file -> file.delete() }
    }

    private fun isFrontendExtraModuleConfigured(): Boolean =
        configurationFile.isFile && configurationFile.readLines(Charsets.UTF_8).containsAllCurrentResourceEntries()

    private fun ensureFrontendExtraModule(): String {
        configurationFile.parentFile?.mkdirs()

        val originalLines = configurationFile.takeIf { it.isFile }?.readLines(Charsets.UTF_8).orEmpty()
        if (originalLines.containsAllCurrentResourceEntries()) {
            return "Frontend extra module already configured"
        }

        val lines = originalLines
            .filterNot { line -> line.contains("ktor-lovelace-cards") }
            .toMutableList()
        val frontendIndex = lines.indexOfFirst { line -> line.matches(Regex("""^frontend:\s*.*$""")) }

        when {
            frontendIndex == -1 -> appendFrontendSection(lines)
            !lines[frontendIndex].matches(Regex("""^frontend:\s*(#.*)?$""")) ->
                return "Frontend extra module needs manual configuration"
            else -> addResourceToFrontendSection(lines, frontendIndex)
        }

        configurationFile.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
        return "Frontend extra module configured"
    }

    private fun isCurrentResourceEntry(line: String): Boolean =
        line.resourceEntryValue() in resourceUrls

    private fun List<String>.containsAllCurrentResourceEntries(): Boolean {
        val entries = map { line -> line.resourceEntryValue() }.toSet()
        return resourceUrls.all(entries::contains)
    }

    private fun String.resourceEntryValue(): String =
        trim().removePrefix("-").trim().trim('"', '\'')

    private fun appendFrontendSection(lines: MutableList<String>) {
        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("frontend:")
        lines.add("  extra_module_url:")
        resourceUrls.forEach { resourceUrl -> lines.add("    - $resourceUrl") }
    }

    private fun addResourceToFrontendSection(lines: MutableList<String>, frontendIndex: Int) {
        val sectionEnd = lines.indexOfFirstAfter(frontendIndex + 1) { line ->
            line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")
        }.takeUnless { it == -1 } ?: lines.size
        val extraModuleIndex = (frontendIndex + 1 until sectionEnd).firstOrNull { index ->
            lines[index].matches(Regex("""^\s{2}extra_module_url:\s*(#.*)?$"""))
        }

        if (extraModuleIndex == null) {
            lines.add(frontendIndex + 1, "  extra_module_url:")
            resourceUrls.reversed().forEach { resourceUrl ->
                lines.add(frontendIndex + 2, "    - $resourceUrl")
            }
        } else {
            resourceUrls.reversed().forEach { resourceUrl ->
                lines.add(extraModuleIndex + 1, "    - $resourceUrl")
            }
        }
    }

    private inline fun List<String>.indexOfFirstAfter(startIndex: Int, predicate: (String) -> Boolean): Int {
        for (index in startIndex until size) {
            if (predicate(this[index])) {
                return index
            }
        }
        return -1
    }

    private suspend fun syncLovelaceResource(token: String): String = withContext(Dispatchers.IO) {
        val connection = HomeAssistantCoreWebSocket(token, json)
        try {
            connection.connect()
            val resources = connection.listLovelaceResources().filter(::isKtorLovelaceResource)
            resourceUrls.forEach { resourceUrl ->
                val existing = resources.firstOrNull { resource -> resource.url == resourceUrl }
                if (existing == null) {
                    connection.createResource(resourceUrl)
                }
            }

            resources
                .filterNot { resource -> resource.url in resourceUrls }
                .forEach { resource ->
                    connection.deleteResource(resource)
                }
            "Lovelace resource synced; ${ensureFrontendExtraModule()}"
        } finally {
            connection.close()
        }
    }

    private suspend fun listKtorResources(token: String): List<LovelaceResourceStatus> = withContext(Dispatchers.IO) {
        val connection = HomeAssistantCoreWebSocket(token, json)
        try {
            connection.connect()
            connection.listLovelaceResources()
                .filter(::isKtorLovelaceResource)
                .map { resource -> LovelaceResourceStatus(resource.url) }
        } finally {
            connection.close()
        }
    }

    private fun HomeAssistantCoreWebSocket.listLovelaceResources(): List<JsonElement> =
        command("lovelace/resources")
            .jsonObject["result"]
            ?.jsonArray
            ?.toList()
            .orEmpty()

    private val JsonElement.url: String
        get() = jsonObject["url"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun isKtorLovelaceResource(resource: JsonElement): Boolean =
        resource.url.contains("ktor-lovelace-cards")

    private fun HomeAssistantCoreWebSocket.createResource(resourceUrl: String) {
        command(
            "lovelace/resources/create",
            mapOf(
                "res_type" to "module",
                "url" to resourceUrl
            )
        )
    }

    private fun HomeAssistantCoreWebSocket.deleteResource(resource: JsonElement) {
        command("lovelace/resources/delete", mapOf("resource_id" to resource.resourceId()))
    }

    private fun JsonElement.resourceId(): JsonElement =
        jsonObject["id"] ?: jsonObject["resource_id"]
        ?: throw IllegalStateException("Existing Lovelace resource has no id")
}
