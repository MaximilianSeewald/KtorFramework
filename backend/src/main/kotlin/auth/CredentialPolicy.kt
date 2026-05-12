package com.loudless.auth

object CredentialPolicy {
    const val minimumPasswordLength = 8

    fun passwordValidationMessage(password: String): String? {
        return when {
            password.isBlank() -> "Password is empty"
            password.length < minimumPasswordLength -> "Password must be at least $minimumPasswordLength characters"
            else -> null
        }
    }
}
