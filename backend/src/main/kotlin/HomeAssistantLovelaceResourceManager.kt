package com.loudless

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

@Serializable
data class LovelaceResourceRequest(val url: String)

class HomeAssistantLovelaceResourceManager {
    private val json = Json { ignoreUnknownKeys = true }

    fun initRoutes(route: Route) {
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
        val connection = HomeAssistantWebSocket(token, json)
        try {
            connection.connect()
            val resources = connection.command("lovelace/resources")
                .jsonObject["result"]
                ?.jsonArray
                ?: JsonArray(emptyList())

            val existing = resources.firstOrNull { resource ->
                val url = resource.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
                url.substringBefore("?") == resourceUrl.substringBefore("?")
            }

            if (existing == null) {
                connection.command(
                    "lovelace/resources/create",
                    mapOf(
                        "res_type" to "module",
                        "url" to resourceUrl
                    )
                )
                "Lovelace resource installed"
            } else {
                val existingUrl = existing.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                if (existingUrl == resourceUrl) {
                    "Lovelace resource is already installed"
                } else {
                    val resourceId = existing.jsonObject["id"] ?: existing.jsonObject["resource_id"]
                    if (resourceId == null) {
                        throw IllegalStateException("Existing Lovelace resource has no id")
                    }
                    connection.command(
                        "lovelace/resources/update",
                        mapOf(
                            "resource_id" to resourceId,
                            "res_type" to "module",
                            "url" to resourceUrl
                        )
                    )
                    "Lovelace resource updated"
                }
            }
        } finally {
            connection.close()
        }
    }

    private class HomeAssistantWebSocket(
        private val token: String,
        private val json: Json
    ) : WebSocket.Listener {
        private val client = HttpClient.newHttpClient()
        private lateinit var socket: WebSocket
        private var nextId = 1
        private val authFuture = CompletableFuture<Unit>()
        private val responseFutures = mutableMapOf<Int, CompletableFuture<JsonElement>>()
        private val textBuffer = StringBuilder()

        fun connect() {
            socket = client
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://supervisor/core/websocket"), this)
                .get(10, TimeUnit.SECONDS)

            socket.sendText("""{"type":"auth","access_token":"$token"}""", true)
                .get(10, TimeUnit.SECONDS)
            authFuture.get(10, TimeUnit.SECONDS)
        }

        fun command(type: String, payload: Map<String, Any> = emptyMap()): JsonElement {
            val id = nextId++
            val future = CompletableFuture<JsonElement>()
            responseFutures[id] = future

            val body = buildJsonObject(id, type, payload)
            socket.sendText(body, true).get(10, TimeUnit.SECONDS)
            val response = future.get(10, TimeUnit.SECONDS)
            val success = response.jsonObject["success"]?.jsonPrimitive?.booleanOrNull
            if (success == false) {
                val message = response.jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                throw IllegalStateException(message ?: "Home Assistant websocket command failed")
            }
            return response
        }

        fun close() {
            if (::socket.isInitialized) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            }
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            textBuffer.append(data)
            if (!last) {
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            val message = json.parseToJsonElement(textBuffer.toString()).jsonObject
            textBuffer.clear()
            when (message["type"]?.jsonPrimitive?.contentOrNull) {
                "auth_required" -> Unit
                "auth_ok" -> authFuture.complete(Unit)
                "auth_invalid" -> authFuture.completeExceptionally(IllegalStateException("Home Assistant authentication failed"))
                "result" -> {
                    val id = message["id"]?.jsonPrimitive?.int
                    if (id != null) {
                        responseFutures.remove(id)?.complete(message)
                    }
                }
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            authFuture.completeExceptionally(error)
            responseFutures.values.forEach { it.completeExceptionally(error) }
        }

        private fun buildJsonObject(id: Int, type: String, payload: Map<String, Any>): String {
            val fields = mutableListOf(
                """"id":$id""",
                """"type":"${escape(type)}""""
            )
            payload.forEach { (key, value) ->
                val encodedValue = when (value) {
                    is String -> """"${escape(value)}""""
                    is JsonElement -> value.toString()
                    else -> value.toString()
                }
                fields.add(""""${escape(key)}":$encodedValue""")
            }
            return "{${fields.joinToString(",")}}"
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
