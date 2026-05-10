package com.loudless.shared

import com.auth0.jwt.interfaces.DecodedJWT
import com.loudless.auth.JwtService
import io.ktor.websocket.*
import org.slf4j.LoggerFactory

object JwtUtil {
    private val LOGGER = LoggerFactory.getLogger(JwtUtil::class.java)

    fun verifyToken(token: String): DecodedJWT? {
        return try {
            val decoded = JwtService.verifyToken(token)
            LOGGER.debug("Verified websocket token")
            decoded
        } catch (e: Exception) {
            LOGGER.warn("Rejected websocket token", e)
            null
        }
    }

    suspend fun validateWebSocketToken(
        wsSession: DefaultWebSocketSession,
        token: String?
    ): DecodedJWT? {
        if (token == null) {
            LOGGER.warn("Rejected websocket connection because token was missing")
            wsSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
            return null
        }

        val decoded = verifyToken(token)
        if (decoded == null) {
            LOGGER.warn("Rejected websocket connection because token was invalid")
            wsSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
        }
        return decoded
    }

    fun getUsername(decodedJWT: DecodedJWT): String {
        return decodedJWT.getClaim("username").asString()
    }
}

