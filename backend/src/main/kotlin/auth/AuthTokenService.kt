package com.loudless.auth

object AuthTokenService {
    fun createToken(username: String): String {
        return JwtService.createToken(username)
    }
}
