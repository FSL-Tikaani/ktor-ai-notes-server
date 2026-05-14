package com.tikaani

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для маршрутов дисциплин:
 *   GET  /disciplines
 *   POST /disciplines
 */
class DisciplinesRoutesTest {

    private lateinit var userToken: String
    private lateinit var otherUserToken: String

    @BeforeTest
    fun setup() {
        initTestDatabase()
    }

    /** Регистрирует пользователя и возвращает JWT-токен. */
    private suspend fun registerAndGetToken(
        builder: io.ktor.server.testing.ApplicationTestBuilder,
        login: String = TEST_LOGIN,
        password: String = TEST_PASSWORD
    ): String {
        val client = builder.jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$login","password":"$password"}""")
        }
        return makeTestToken(login)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /disciplines
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GET disciplines - returns 200 and empty list for new user`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().get("/disciplines") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonArray
        assertTrue(json.isEmpty(), "Expected empty array, got: $body")
    }

    @Test
    fun `GET disciplines - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().get("/disciplines")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET disciplines - returns only current user disciplines, not other users`() = testApplication {
        application { module() }
        val client = jsonClient()

        val token1 = registerAndGetToken(this, "user_one", "pass1")
        val token2 = registerAndGetToken(this, "user_two", "pass2")

        // Создаём дисциплину от имени user_one
        client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name":"Математика","color":"purple","emoji":"∑"}""")
        }

        // user_two не должен видеть дисциплины user_one
        val response = client.get("/disciplines") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertTrue(json.isEmpty(), "user_two should not see user_one's disciplines")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /disciplines
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `POST disciplines - creates discipline and returns 201 with correct fields`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val response = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name":"Физика","color":"blue","emoji":"φ"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Физика", json["name"]?.jsonPrimitive?.content)
        assertEquals("blue",   json["color"]?.jsonPrimitive?.content)
        assertEquals("φ",      json["emoji"]?.jsonPrimitive?.content)
        assertEquals("0",      json["notesCount"]?.jsonPrimitive?.content)
        assertTrue(json["id"]?.jsonPrimitive?.content?.toInt()!! > 0)
    }

    @Test
    fun `POST disciplines - created discipline appears in GET disciplines`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name":"История","color":"amber","emoji":"⊛"}""")
        }

        val listResponse = client.get("/disciplines") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val json = Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray
        assertEquals(1, json.size)
        assertEquals("История", json[0].jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST disciplines - can create multiple disciplines`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val subjects = listOf(
            Triple("Алгебра",   "purple", "∑"),
            Triple("Физика",    "blue",   "φ"),
            Triple("Химия",     "green",  "β"),
        )

        subjects.forEach { (name, color, emoji) ->
            client.post("/disciplines") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody("""{"name":"$name","color":"$color","emoji":"$emoji"}""")
            }
        }

        val listResponse = client.get("/disciplines") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val json = Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray
        assertEquals(3, json.size)
    }

    @Test
    fun `POST disciplines - returns 400 when name is blank`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val response = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name":"","color":"amber","emoji":"⊛"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST disciplines - returns 400 on malformed JSON`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val response = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST disciplines - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().post("/disciplines") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Математика","color":"amber","emoji":"⊛"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST disciplines - topics list is empty on creation`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val response = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name":"Биология","color":"green","emoji":"β"}""")
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val topics = json["topics"]?.jsonArray
        assertTrue(topics != null && topics.isEmpty(), "topics should be empty on creation")
    }
}
