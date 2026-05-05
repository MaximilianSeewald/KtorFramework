package com.loudless.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager.secretJWTKey
import java.util.Date

object AuthTokenService {
    private const val validityInMs = 1000000000

    fun createToken(username: String): String {
        return JWT.create()
            .withAudience("ktor-app")
            .withIssuer("ktor-auth")
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(Algorithm.HMAC256(secretJWTKey))
    }
}
