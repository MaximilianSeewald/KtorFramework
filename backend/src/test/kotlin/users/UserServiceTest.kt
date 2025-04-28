package com.loudless.users

import com.auth0.jwt.interfaces.DecodedJWT
import com.loudless.database.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class UserServiceTest {

    val username: String = "testUser"
    val username2: String = "testUser2"
    val username3: String = "testUser3"
    val password: String = "testPassword"
    val group: String = "testGroup"

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users)
            Users.deleteAll()
        }
    }

    fun addUserAndReturnId(username: String, password: String, userGroup: String? = null): Int {
        return transaction {
            val insertStatement = Users.insert {
                it[name] = username
                it[hashedPassword] = password
                it[group] = userGroup
            }

            insertStatement[Users.id]
                ?: error("Failed to retrieve inserted ID")
        }
    }

    @Test
    fun testGetUserGroupsByPrincipalReturnsCorrectGroups() {
        // Arrange
        val mockCall = mockk<ApplicationCall>()
        val mockPrincipal = mockk<JWTPrincipal>()

        every { mockCall.principal<JWTPrincipal>() } returns mockPrincipal
        every { mockPrincipal.getClaim("username", String::class) } returns username

        addUserAndReturnId(username, password, group)
        addUserAndReturnId(username2, password, "otherGroup")

        // Act
        val groups = UserService.getUserGroupsByPrincipal(mockCall)

        // Assert
        assertEquals(1, groups.size)
        assertEquals(group, groups.first())
        verify { mockCall.principal<JWTPrincipal>() }
        verify { mockPrincipal.getClaim("username", String::class) }
    }

    @Test
    fun testGetUserNameByQueryReturnsCorrectUsername() {
        // Arrange
        val mockDecodedJWT = mockk<DecodedJWT>()
        every { mockDecodedJWT.getClaim("username").asString() } returns username

        // Act
        val actualUsername = UserService.getUserNameByQuery(mockDecodedJWT)

        // Assert
        assertEquals(username, actualUsername)
        verify { mockDecodedJWT.getClaim("username").asString() }
    }

    @Test
    fun testGetUserGroupsByQueryReturnsCorrectUserGroup() {
        // Arrange
        val mockDecodedJWT = mockk<DecodedJWT>()
        every { mockDecodedJWT.getClaim("username").asString() } returns username
        addUserAndReturnId(username, password, group)

        // Act
        val groups = UserService.getUserGroupsByQuery(mockDecodedJWT)

        // Assert
        assertEquals(1, groups.size)
        assertEquals(group, groups.first())
        verify { mockDecodedJWT.getClaim("username").asString() }
    }

    @Test
    fun testRetrieveAndHandleUsersReturnsSingleUser() = runTest {
        // Arrange
        val mockCall = mockk<ApplicationCall>(relaxed = true)
        val mockPrincipal = mockk<JWTPrincipal>()
        every { mockCall.principal<JWTPrincipal>() } returns mockPrincipal
        every { mockPrincipal.getClaim("username", String::class) } returns username
        addUserAndReturnId(username, password, group)

        // Act
        val users = UserService.retrieveAndHandleUsers(mockCall)

        // Assert
        assertEquals(1, users.size)
        assertEquals(username, users.first().name)
        assertEquals(group, users.first().userGroup)
        verify { mockCall.principal<JWTPrincipal>() }
        verify { mockPrincipal.getClaim("username", String::class) }
    }

    @Test
    fun testRetrieveAndHandleUsersHandlesNoUserFound() = runTest {
        // Arrange
        val mockCall = mockk<ApplicationCall>(relaxed = true)
        val mockPrincipal = mockk<JWTPrincipal>()
        every { mockCall.principal<JWTPrincipal>() } returns mockPrincipal
        every { mockPrincipal.getClaim("username", String::class) } returns username

        // Act
        val users = UserService.retrieveAndHandleUsers(mockCall)

        // Assert
        assertTrue(users.isEmpty())
        coVerify { mockCall.respond(HttpStatusCode.BadRequest, "No User Found") }
    }

    @Test
    fun testRetrieveAndHandleUsersHandlesMultipleUsersFound() = runTest {
        // Arrange
        val mockCall = mockk<ApplicationCall>(relaxed = true)
        val mockPrincipal = mockk<JWTPrincipal>()
        every { mockCall.principal<JWTPrincipal>() } returns mockPrincipal
        every { mockPrincipal.getClaim("username", String::class) } returns username
        addUserAndReturnId(username, password, group)
        addUserAndReturnId(username, password, "otherGroup")

        // Act
        val users = UserService.retrieveAndHandleUsers(mockCall)

        // Assert
        assertTrue(users.isEmpty())
        coVerify { mockCall.respond(HttpStatusCode.BadRequest, "Multiple User Found for this name") }
    }

    @Test
    fun testAddUserInsertsIntoTheDatabase() {
        // Arrange

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

    @Test
    fun testAddUserGroupToUserAddsGroupToUserInDatabase() {
        // Arrange
        val id = addUserAndReturnId(username, password)

        // Act
        UserService.addUserGroupToUser(id, group)

        // Assert
        val groups = transaction {
            Users
                .selectAll()
                .where { Users.name eq username }
                .map { it[Users.group] }
        }
        assertEquals(1, groups.size)
        assertEquals(group, groups.first())
    }

    @Test
    fun testDeleteUserGroupFromUserDeletesGroupFromUserInDatabase() {
        // Arrange
        val id = addUserAndReturnId(username, password, group)

        // Act
        UserService.deleteUserGroupFromUser(id)

        // Assert
        val groups = transaction {
            Users
                .selectAll()
                .where { Users.name eq username }
                .map { it[Users.group] }
        }
        assertEquals(1, groups.size)
        assertEquals(null, groups.first())
    }

    @Test
    fun testDeleteUserGroupFromAllUsersDeletesGroupFromAllUsersWithGroupInDatabase() {
        // Arrange
        val otherGroup = "otherGroup"
        addUserAndReturnId(username, password, group)
        addUserAndReturnId(username2, password, group)
        addUserAndReturnId(username3, password, otherGroup)

        // Act
        UserService.deleteUserGroupFromAllUsers(group)

        // Assert
        val groups = mapOf(
            username to null,
            username2 to null,
            username3 to otherGroup
        )
        groups.forEach { (user, expectedGroup) ->
            val userGroup = transaction {
                Users.selectAll()
                    .where { Users.name eq user }
                    .map { it[Users.group] }
                    .firstOrNull()
            }
            assertEquals(expectedGroup, userGroup)
        }
    }

    @Test
    fun testGetUsersForGroupReturnsUsersWithGroupFromDatabase() {
        // Arrange
        addUserAndReturnId(username, password, group)
        addUserAndReturnId(username2, password, group)
        addUserAndReturnId(username3, password, "otherGroup")

        // Act
        val users = UserService.getUsersForGroup(group)

        // Assert
        assertEquals(2, users.size)
        assertTrue(users.contains(username))
        assertTrue(users.contains(username2))
        assertFalse(users.contains(username3))
    }
}