package com.loudless

import at.favre.lib.crypto.bcrypt.BCrypt
import com.loudless.DatabaseManager.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object UserService {

    suspend fun getAllUsers() :List<User> = newSuspendedTransaction{
        Users.selectAll().map { toUsers(it) }
    }

    private fun toUsers(row: ResultRow) : User {
        return User(
            id = row[Users.id],
            name = row[Users.name],
            hashedPassword = row[Users.hashedPassword]
        )
    }

    fun hashPassword(plainPassword: String): String {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
    }

    fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword)
        return result.verified
    }
}