import com.loudless.configureBackend
import com.loudless.auth.JwtService
import com.loudless.config.AppConfigLoader
import com.loudless.database.DatabaseManager
import com.loudless.database.Recipe as RecipeTable
import com.loudless.database.ShoppingList
import com.loudless.database.UserGroups
import com.loudless.database.Users
import com.loudless.models.CreateUserGroupRequest
import com.loudless.models.JoinUserGroupRequest
import com.loudless.models.LoginRequest
import com.loudless.models.Recipe
import com.loudless.models.ShoppingListItem
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val REQUEST_ID_HEADER = "X-Request-ID"

class BackendIntegrationTest {
    @BeforeTest
    fun setUpIntegrationDatabase() {
        System.setProperty("JWT_SECRET_KEY", "test-secret")
        System.clearProperty("HA_MODE")
        System.clearProperty("DATABASE_PATH")
        System.clearProperty("DATABASE_BACKUP_PATH")
        System.clearProperty("H2_MODE")
        AppConfigLoader.reset()
        val databasePath = Files.createTempDirectory("ktor-framework-test").resolve("db").toString()
        System.setProperty("ktor.database.path", databasePath)
        DatabaseManager.shoppingListMap.clear()
        DatabaseManager.recipeMap.clear()
        DatabaseManager.init()
    }

    @AfterTest
    fun tearDownIntegrationDatabase() {
        DatabaseManager.close()
        System.clearProperty("JWT_SECRET_KEY")
        System.clearProperty("JWT_TOKEN_TTL_MS")
        System.clearProperty("HA_MODE")
        System.clearProperty("DATABASE_PATH")
        System.clearProperty("DATABASE_BACKUP_PATH")
        System.clearProperty("H2_MODE")
        System.clearProperty("ktor.database.path")
        AppConfigLoader.reset()
    }

    @Test
    fun `user auth lifecycle supports signup login verify and duplicate rejection`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "user_${UUID.randomUUID()}"

        assertEquals(HttpStatusCode.Created, signup(client, username, "password").status)
        assertEquals(HttpStatusCode.BadRequest, signup(client, username, "password").status)

        val failedLogin = login(client, username, "wrong")
        assertEquals(HttpStatusCode.Unauthorized, failedLogin.response.status)

        val login = login(client, username, "password")
        assertEquals(HttpStatusCode.OK, login.response.status)
        assertNotNull(login.token)

        val verify = client.get("/api/verify") {
            bearer(login.token)
        }
        assertEquals(HttpStatusCode.OK, verify.status)
        val verifyBody = Json.parseToJsonElement(verify.bodyAsText()).jsonObject
        assertEquals(true, verifyBody["valid"]?.jsonPrimitive?.boolean)
        assertEquals(username, verifyBody["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content)

        val missingTokenVerify = client.get("/api/verify")
        assertEquals(HttpStatusCode.Unauthorized, missingTokenVerify.status)
    }

    @Test
    fun `verify rejects malformed expired and missing-user tokens`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/verify").status)

        val malformed = client.get("/api/verify") {
            bearer("not-a-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, malformed.status)

        System.setProperty("JWT_TOKEN_TTL_MS", "-1000")
        val expiredToken = JwtService.createToken("expired-user")
        System.clearProperty("JWT_TOKEN_TTL_MS")
        val expired = client.get("/api/verify") {
            bearer(expiredToken)
        }
        assertEquals(HttpStatusCode.Unauthorized, expired.status)

        val missingUserToken = JwtService.createToken("missing-user")
        val missingUser = client.get("/api/verify") {
            bearer(missingUserToken)
        }
        assertEquals(HttpStatusCode.Unauthorized, missingUser.status)
    }

    @Test
    fun `cors is not installed by default`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()

        val response = client.options("/api/login") {
            header(HttpHeaders.Origin, "https://random.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }

        assertFalse(response.headers.contains(HttpHeaders.AccessControlAllowOrigin))
    }

    @Test
    fun `cors allows only origins configured in config file`() = testApplication {
        val configPath = Files.createTempFile("ktor-framework-cors-test", ".properties")
        Files.writeString(configPath, "CORS_ALLOWED_ORIGINS=https://allowed.example")
        AppConfigLoader.configPath = configPath

        application { configureBackend() }
        val client = createJsonClient()

        val allowed = client.options("/api/login") {
            header(HttpHeaders.Origin, "https://allowed.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals("https://allowed.example", allowed.headers[HttpHeaders.AccessControlAllowOrigin])

        val blocked = client.options("/api/login") {
            header(HttpHeaders.Origin, "https://blocked.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertFalse(blocked.headers.contains(HttpHeaders.AccessControlAllowOrigin))
    }

    @Test
    fun `development config enables local Angular cors origins`() = testApplication {
        val configPath = Files.createTempFile("ktor-framework-cors-dev-test", ".properties")
        Files.writeString(configPath, "APP_ENV=development")
        AppConfigLoader.configPath = configPath

        application { configureBackend() }
        val client = createJsonClient()

        val response = client.options("/api/login") {
            header(HttpHeaders.Origin, "http://localhost:4200")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }

        assertEquals("http://localhost:4200", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `health endpoints report liveness and readiness`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()

        val live = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, live.status)
        val liveBody = Json.parseToJsonElement(live.bodyAsText()).jsonObject
        assertEquals("UP", liveBody["status"]?.jsonPrimitive?.content)

        val ready = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, ready.status)
        val readyBody = Json.parseToJsonElement(ready.bodyAsText()).jsonObject
        assertEquals("UP", readyBody["status"]?.jsonPrimitive?.content)
        assertEquals(
            "UP",
            readyBody["checks"]?.jsonObject?.get("database")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `readiness reports unavailable when database is closed`() = testApplication {
        application { configureBackend() }
        DatabaseManager.close()
        val client = createJsonClient()

        val ready = client.get("/health/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, ready.status)
        val readyBody = Json.parseToJsonElement(ready.bodyAsText()).jsonObject
        assertEquals("DOWN", readyBody["status"]?.jsonPrimitive?.content)
        assertEquals(
            "DOWN",
            readyBody["checks"]?.jsonObject?.get("database")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `unexpected exceptions return consistent json with request id`() = testApplication {
        application {
            configureBackend()
            routing {
                get("/test/unhandled") {
                    throw RuntimeException("boom")
                }
            }
        }
        val client = createJsonClient()
        val requestId = "test-request-${shortId()}"

        val response = client.get("/test/unhandled") {
            header(REQUEST_ID_HEADER, requestId)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(requestId, response.headers[REQUEST_ID_HEADER])
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Internal server error", body["message"]?.jsonPrimitive?.content)
        assertEquals(requestId, body["requestId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `request id response header is generated or preserved`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()

        val generated = client.get("/health/live")
        assertNotNull(generated.headers[REQUEST_ID_HEADER])

        val inboundRequestId = "client-request-${shortId()}"
        val preserved = client.get("/health/live") {
            header(REQUEST_ID_HEADER, inboundRequestId)
        }
        assertEquals(inboundRequestId, preserved.headers[REQUEST_ID_HEADER])
    }

    @Test
    fun `shopping list lifecycle works over authenticated http and websocket`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "shopping_${UUID.randomUUID()}"
        val token = createUserWithGroup(client, username, "shopping_group_${shortId()}")
        val itemId = UUID.randomUUID().toString()

        val add = client.post("/api/shoppingList") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(ShoppingListItem("Milk", "1", itemId, false))
        }
        assertEquals(HttpStatusCode.OK, add.status)

        val edit = client.put("/api/shoppingList") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(ShoppingListItem("Milk", "2", itemId, true))
        }
        assertEquals(HttpStatusCode.OK, edit.status)

        val malformedEdit = client.put("/api/shoppingList") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(ShoppingListItem("Milk", "2", "not-a-uuid", true))
        }
        assertEquals(HttpStatusCode.BadRequest, malformedEdit.status)

        val missingDeleteId = client.delete("/api/shoppingList") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.BadRequest, missingDeleteId.status)

        client.webSocket("/api/shoppingListWS?token=$token") {
            val frame = withTimeout(5000) { incoming.receive() }
            val text = (frame as Frame.Text).readText()
            assertTrue(text.contains("Milk"))
        }

        val delete = client.delete("/api/shoppingList?id=$itemId") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.OK, delete.status)
    }

    @Test
    fun `recipe lifecycle works over authenticated http and websocket`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "recipe_${UUID.randomUUID()}"
        val token = createUserWithGroup(client, username, "recipe_group_${shortId()}")
        val recipeId = UUID.randomUUID().toString()

        val add = client.post("/api/recipe") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(Recipe(recipeId, "Soup", emptyList()))
        }
        assertEquals(HttpStatusCode.OK, add.status)

        val edit = client.put("/api/recipe") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(Recipe(recipeId, "Better Soup", emptyList()))
        }
        assertEquals(HttpStatusCode.OK, edit.status)

        val malformedDelete = client.delete("/api/recipe?id=not-a-uuid") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.BadRequest, malformedDelete.status)

        client.webSocket("/api/recipeWS?token=$token") {
            val frame = withTimeout(5000) { incoming.receive() }
            val text = (frame as Frame.Text).readText()
            assertTrue(text.contains("Better Soup"))
        }

        val delete = client.delete("/api/recipe?id=$recipeId") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.OK, delete.status)
    }

    @Test
    fun `user group lifecycle creates and removes dynamic resource tables`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "group_${UUID.randomUUID()}"
        val groupName = "delete_group_${shortId()}"
        val token = createUserWithGroup(client, username, groupName)

        val missingName = client.delete("/api/usergroups") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.BadRequest, missingName.status)

        val delete = client.delete("/api/usergroups?name=$groupName") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.OK, delete.status)
        assertTrue(DatabaseManager.shoppingListMap[groupName] == null)
        assertTrue(DatabaseManager.recipeMap[groupName] == null)
    }

    @Test
    fun `invalid user group names are rejected`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "invalid_group_${UUID.randomUUID()}"
        assertEquals(HttpStatusCode.Created, signup(client, username, "password").status)
        val login = login(client, username, "password")
        val token = requireNotNull(login.token)

        val createGroup = client.post("/api/usergroups") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(CreateUserGroupRequest("bad-group", "group-password"))
        }
        assertEquals(HttpStatusCode.BadRequest, createGroup.status)

        val joinGroup = client.post("/api/user/$username/groups") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(JoinUserGroupRequest("bad-group", "group-password"))
        }
        assertEquals(HttpStatusCode.BadRequest, joinGroup.status)
    }

    @Test
    fun `startup fails for persisted invalid group names`() {
        transaction {
            val userId = Users.insert {
                it[name] = "invalid_group_owner"
                it[hashedPassword] = DatabaseManager.hashPassword("password")
            }[Users.id]
            UserGroups.insert {
                it[name] = "bad-group"
                it[hashedPassword] = DatabaseManager.hashPassword("group-password")
                it[adminUserId] = userId
            }
        }

        assertFailsWith<IllegalStateException> {
            DatabaseManager.init()
        }
    }

    @Test
    fun `home assistant startup migrates legacy dashed group name`() {
        transaction {
            val userId = Users.insert {
                it[name] = "legacy_ha_user"
                it[group] = "ha-instance"
                it[hashedPassword] = DatabaseManager.hashPassword("password")
            }[Users.id]
            UserGroups.insert {
                it[name] = "ha-instance"
                it[hashedPassword] = DatabaseManager.hashPassword("group-password")
                it[adminUserId] = userId
            }
            SchemaUtils.create(ShoppingList("ha-instance"), RecipeTable("ha-instance_recipe"))
        }

        System.setProperty("HA_MODE", "true")
        DatabaseManager.init()

        transaction {
            assertTrue(UserGroups.selectAll().where { UserGroups.name eq "ha-instance" }.empty())
            assertFalse(UserGroups.selectAll().where { UserGroups.name eq "ha_instance" }.empty())
            assertTrue(Users.selectAll().where { Users.group eq "ha-instance" }.empty())
        }
        assertTrue(DatabaseManager.shoppingListMap.containsKey("ha_instance"))
        assertTrue(DatabaseManager.recipeMap.containsKey("ha_instance"))
    }

    @Test
    fun `home assistant mode exposes session and blocks local auth and group management`() = testApplication {
        System.setProperty("HA_MODE", "true")
        DatabaseManager.shoppingListMap.clear()
        DatabaseManager.recipeMap.clear()
        DatabaseManager.init()
        application { configureBackend() }
        val client = createJsonClient()

        val session = client.get("/api/ha/session")
        assertEquals(HttpStatusCode.OK, session.status)
        val token = Json.parseToJsonElement(session.bodyAsText())
            .jsonObject["token"]
            ?.jsonPrimitive
            ?.content
        assertNotNull(token)

        val verify = client.get("/api/verify") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.OK, verify.status)
        val verifyBody = Json.parseToJsonElement(verify.bodyAsText()).jsonObject
        assertEquals(true, verifyBody["valid"]?.jsonPrimitive?.boolean)
        assertEquals("ha-user", verifyBody["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content)

        val signup = signup(client, "ha_disabled_${UUID.randomUUID()}", "password")
        assertEquals(HttpStatusCode.Forbidden, signup.status)

        val login = login(client, "ha-user", "home-assistant-instance-user")
        assertEquals(HttpStatusCode.Forbidden, login.response.status)

        val passwordChange = client.post("/api/user/ha-user/password") {
            bearer(token)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("oldPassword=old&newPassword=new")
        }
        assertEquals(HttpStatusCode.Forbidden, passwordChange.status)

        val createGroup = client.post("/api/usergroups") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(CreateUserGroupRequest("ha_disabled_group", "password"))
        }
        assertEquals(HttpStatusCode.Forbidden, createGroup.status)

        val joinGroup = client.post("/api/user/ha-user/groups") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(JoinUserGroupRequest("ha_instance", "password"))
        }
        assertEquals(HttpStatusCode.Forbidden, joinGroup.status)

        val leaveGroup = client.delete("/api/user/ha-user/groups/ha_instance") {
            bearer(token)
        }
        assertEquals(HttpStatusCode.Forbidden, leaveGroup.status)
    }

    @Test
    fun `signup and user group creation reject short passwords`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "policy_${UUID.randomUUID()}"

        val shortSignup = signup(client, username, "short")
        assertEquals(HttpStatusCode.BadRequest, shortSignup.status)

        assertEquals(HttpStatusCode.Created, signup(client, username, "long-enough").status)
        val login = login(client, username, "long-enough")
        val token = requireNotNull(login.token)

        val shortGroupPassword = client.post("/api/usergroups") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(CreateUserGroupRequest("policy_group_${shortId()}", "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, shortGroupPassword.status)
    }

    private fun ApplicationTestBuilder.createJsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets)
    }

    private suspend fun signup(
        client: HttpClient,
        username: String,
        password: String
    ) = client.post("/api/user") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody("username=$username&password=$password")
    }

    private suspend fun login(
        client: HttpClient,
        username: String,
        password: String
    ): LoginResult {
        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        val token = if (response.status == HttpStatusCode.OK) {
            Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["token"]
                ?.jsonPrimitive
                ?.content
        } else {
            null
        }
        return LoginResult(response, token)
    }

    private suspend fun createUserWithGroup(
        client: HttpClient,
        username: String,
        groupName: String
    ): String {
        assertEquals(HttpStatusCode.Created, signup(client, username, "password").status)
        val login = login(client, username, "password")
        val token = requireNotNull(login.token)
        val createGroup = client.post("/api/usergroups") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(CreateUserGroupRequest(groupName, "group-password"))
        }
        assertEquals(HttpStatusCode.OK, createGroup.status)
        return token
    }

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private data class LoginResult(
        val response: HttpResponse,
        val token: String?
    )

    private fun shortId(): String = UUID.randomUUID().toString().replace("-", "")
}
