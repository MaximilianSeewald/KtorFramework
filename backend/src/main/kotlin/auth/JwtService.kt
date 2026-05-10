package com.loudless.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import com.loudless.config.BackendConfig
import java.util.Date

object JwtService {
    fun verifier(): JWTVerifier = JWT
        .require(Algorithm.HMAC256(BackendConfig.jwtSecret))
        .withAudience(BackendConfig.jwtAudience)
        .withIssuer(BackendConfig.jwtIssuer)
        .build()

    fun createToken(username: String): String = JWT.create()
        .withAudience(BackendConfig.jwtAudience)
        .withIssuer(BackendConfig.jwtIssuer)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + BackendConfig.jwtTokenTtlMs))
        .sign(Algorithm.HMAC256(BackendConfig.jwtSecret))

    fun verifyToken(token: String): DecodedJWT = verifier().verify(token)
}
