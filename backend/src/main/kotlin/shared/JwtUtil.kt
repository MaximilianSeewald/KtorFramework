package com.loudless.shared

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.loudless.SessionManager.secretJWTKey
import io.ktor.websocket.*

object JwtUtil {

    fun verifyToken(token: String): DecodedJWT? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secretJWTKey))
                .withAudience("ktor-app")
                .withIssuer("ktor-auth")
                .build()
            verifier.verify(token)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun validateWebSocketToken(
        wsSession: DefaultWebSocketSession,
        token: String?
    ): DecodedJWT? {
        if (token == null) {
            wsSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
            return null
        }

        val decoded = verifyToken(token)
        if (decoded == null) {
            wsSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
        }
        return decoded
    }

    fun getUsername(decodedJWT: DecodedJWT): String {
        return decodedJWT.getClaim("username").asString()
    }
}

