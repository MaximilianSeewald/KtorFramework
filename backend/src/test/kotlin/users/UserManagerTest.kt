package com.loudless.users

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.loudless.SessionManager
import com.loudless.database.DatabaseManager
import com.loudless.database.UserGroups
import com.loudless.database.Users
import com.loudless.models.JoinUserGroupRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Routing
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlinx.serialization.json.Json.Default.encodeToString


class UserManagerTest {
    private val username: String = "testUser"
    private val password: String = "testPassword"
    private val userGroup: String = "testGroup"
    private lateinit var userManager: UserManager

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

    fun addUserGroupAndReturnName(groupName: String, password: String, id: Int = 1): String {
        return transaction {
            UserGroups.insert {
                it[name] = groupName
                it[adminUserId] = id
                it[hashedPassword] = DatabaseManager.hashPassword(password)
            }

            groupName
        }
    }

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(UserGroups)
            UserGroups.deleteAll()
            Users.deleteAll()
        }
        userManager = UserManager()
    }


    @Test
    fun testGetUserInformationWithKnownUserReturnsUser() = testApplication {
        // Arrange
        setupApplication()
        addUserAndReturnId(username, password, userGroup)

        // Act
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer ${createTestToken(username)}")
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains(username))
        assertTrue(responseBody.contains(userGroup))
    }

    @Test
    fun testGetUserInformationWithUnknownUserReturnsBadRequest() = testApplication {
        // Arrange
        setupApplication()
        addUserAndReturnId(username, password, userGroup)
        // Act
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer ${createTestToken("unknownUser")}")
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("No User Found"))
    }

    @Test
    fun testGetUserInformationWithDuplicateUserReturnsBadRequest() = testApplication {
        // Arrange
        setupApplication()
        addUserAndReturnId(username, password, userGroup)
        addUserAndReturnId(username, password, "otherGroup")

        // Act
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer ${createTestToken(username)}")
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("Multiple Users Found"))
    }

    @Test
    fun testJoinUserGroupWithValidDataReturnsOk() = testApplication {
        // Arrange
        setupApplication()
        addUserAndReturnId(username, password, null)
        addUserGroupAndReturnName(userGroup, password, 1)

        val joinUserGroupRequest = JoinUserGroupRequest(userGroup, password)
        val serializedRequest = encodeToString(JoinUserGroupRequest.serializer(), joinUserGroupRequest)

        // Act
        val response = client.post("/user/$username/groups") {
            header(HttpHeaders.Authorization, "Bearer ${createTestToken(username)}")
            contentType(ContentType.Application.Json)
            setBody(serializedRequest)
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val user = transaction {
            Users.selectAll()
                .where { Users.name eq username }
                .map { it[Users.group] }
                .firstOrNull()
        }
        assertEquals(userGroup, user)
    }

    @Test
    fun testJoinUserGroupWithWrongUserNameReturnsBadRequest() = testApplication {
        // Arrange
        setupApplication()
        addUserAndReturnId(username, password, null)

        val joinUserGroupRequest = JoinUserGroupRequest(userGroup, password)
        val serializedRequest = encodeToString(JoinUserGroupRequest.serializer(), joinUserGroupRequest)

        // Act
        val response = client.post("/user/unknownUser/groups") {
            header(HttpHeaders.Authorization, "Bearer ${createTestToken(username)}")
            contentType(ContentType.Application.Json)
            setBody(serializedRequest)
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("User does not match"))
    }


    private fun createTestToken(username: String): String {
        return JWT.create()
            .withAudience("ktor-app")
            .withIssuer("ktor-auth")
            .withClaim("username", username)
            .sign(Algorithm.HMAC256(SessionManager.secretJWTKey))
    }

    private fun TestApplicationBuilder.setupApplication() {
        application {
            SessionManager.installComponents(this)
        }
        routing {
            SessionManager.initRouting(this as Routing)
            authenticate("auth-jwt") {
                userManager.initSafeRoutes(this)
            }
        }
    }
}