package com.loudless.shared

import com.loudless.config.BackendConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import java.util.concurrent.ConcurrentHashMap

object RateLimiter {
    private data class Bucket(
        val windowStartedAtMs: Long,
        val count: Int,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    suspend fun check(call: ApplicationCall, bucketName: String): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = BackendConfig.rateLimitWindowSeconds * 1000
        val maxRequests = BackendConfig.rateLimitMaxRequests
        val key = "$bucketName:${clientIp(call)}"
        val bucket = buckets.compute(key) { _, current ->
            if (current == null || now - current.windowStartedAtMs >= windowMs) {
                Bucket(now, 1)
            } else {
                current.copy(count = current.count + 1)
            }
        } ?: Bucket(now, 1)

        if (bucket.count <= maxRequests) {
            return true
        }

        call.respond(HttpStatusCode.TooManyRequests, mapOf("message" to "Too many requests"))
        return false
    }

    fun reset() {
        buckets.clear()
    }

    private fun clientIp(call: ApplicationCall): String {
        return call.request.header("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: call.request.origin.remoteHost
    }
}
