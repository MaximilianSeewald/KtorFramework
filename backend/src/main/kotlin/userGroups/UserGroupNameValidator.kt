package com.loudless.userGroups

object UserGroupNameValidator {
    private val allowedPattern = Regex("^[A-Za-z][A-Za-z0-9_]{2,47}$")
    private val reservedNames = setOf(
        "INFORMATION_SCHEMA",
        "SCHEMA",
        "SELECT",
        "TABLE",
        "USER",
        "USERS",
        "USERGROUPS",
        "WHERE",
    )

    fun validationMessage(name: String): String? {
        val trimmedName = name.trim()
        return when {
            trimmedName != name -> "User group name must not contain leading or trailing whitespace"
            !allowedPattern.matches(name) -> "User group name must be 3-48 characters, start with a letter, and contain only letters, digits, or underscores"
            name.endsWith("_recipe", ignoreCase = true) -> "User group name must not end with _recipe"
            name.uppercase() in reservedNames -> "User group name is reserved"
            else -> null
        }
    }

    fun isValid(name: String): Boolean = validationMessage(name) == null

    fun requireValid(name: String) {
        validationMessage(name)?.let {
            throw IllegalStateException("Invalid persisted user group name '$name': $it. Rename or remove this group before startup.")
        }
    }
}
