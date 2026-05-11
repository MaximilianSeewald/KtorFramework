import com.loudless.configureBackend
import com.loudless.auth.JwtService
import com.loudless.database.DatabaseManager
import com.loudless.models.CreateUserGroupRequest
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
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackendIntegrationTest {
    @BeforeTest
    fun setUpIntegrationDatabase() {
        System.setProperty("JWT_SECRET_KEY", "test-secret")
        System.clearProperty("HA_MODE")
        val databasePath = Files.createTempDirectory("ktor-framework-test").resolve("db").toString()
        System.setProperty("ktor.database.path", databasePath)
        DatabaseManager.shoppingListMap.clear()
        DatabaseManager.recipeMap.clear()
        DatabaseManager.init()
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
    fun `shopping list lifecycle works over authenticated http and websocket`() = testApplication {
        application { configureBackend() }
        val client = createJsonClient()
        val username = "shopping_${UUID.randomUUID()}"
        val token = createUserWithGroup(client, username, "shopping_group_${UUID.randomUUID().toString().replace("-", "_")}")
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
        val token = createUserWithGroup(client, username, "recipe_group_${UUID.randomUUID().toString().replace("-", "_")}")
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
        val groupName = "delete_group_${UUID.randomUUID().toString().replace("-", "_")}"
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
    fun `home assistant mode exposes session and blocks local registration`() = testApplication {
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
}
