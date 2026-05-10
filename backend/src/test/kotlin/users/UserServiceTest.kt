package com.loudless.users

import com.loudless.database.DatabaseManager
import com.loudless.database.Users
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserServiceTest {
    @BeforeTest
    fun setUpDatabase() {
        val databasePath = Files.createTempDirectory("ktor-framework-user-service-test").resolve("db").toString()
        System.setProperty("ktor.database.path", databasePath)
        System.clearProperty("HA_MODE")
        DatabaseManager.shoppingListMap.clear()
        DatabaseManager.recipeMap.clear()
        DatabaseManager.init()
    }

    @Test
    fun `find users by username returns one matching user`() {
        val username = "unit_${UUID.randomUUID()}"
        UserService.addUser(username, "password")

        val users = UserService.findUsersByUsername(username)

        assertEquals(1, users.size)
        assertEquals(username, users[0].name)
    }

    @Test
    fun `find users by username returns empty list for missing user`() {
        val users = UserService.findUsersByUsername("missing_${UUID.randomUUID()}")

        assertTrue(users.isEmpty())
    }

    @Test
    fun `find users by username exposes duplicate records for caller validation`() {
        val username = "duplicate_${UUID.randomUUID()}"
        transaction {
            Users.insert {
                it[name] = username
                it[hashedPassword] = DatabaseManager.hashPassword("one")
            }
            Users.insert {
                it[name] = username
                it[hashedPassword] = DatabaseManager.hashPassword("two")
            }
        }

        val users = UserService.findUsersByUsername(username)

        assertEquals(2, users.size)
    }
}
