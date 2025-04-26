package com.loudless.users

import com.loudless.database.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import kotlin.test.*

class UserServiceTest {

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users)
        }
    }

    @Test
    fun testAddUserInsertsIntoTheDatabase() {
        // Arrange
        val username = "testUser"
        val password = "testPassword"

        // Act
        UserService.addUser(username, password)

        // Assert
        val users = transaction {
            Users
                .selectAll()
                .where { Users.name eq username }
                .map { it[Users.name] }
        }
        assertEquals(1, users.size)
        assertEquals(username, users.first())
    }
}