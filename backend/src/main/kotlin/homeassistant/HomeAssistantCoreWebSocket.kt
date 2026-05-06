package com.loudless.homeassistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

class HomeAssistantCoreWebSocket(
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
