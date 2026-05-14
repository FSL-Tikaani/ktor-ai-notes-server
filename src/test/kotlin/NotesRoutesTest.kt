package com.tikaani

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
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
 * Тесты для маршрутов заметок:
 *   GET  /notes
 *   GET  /notes/{id}
 *   POST /notes
 */
class NotesRoutesTest {

    @BeforeTest
    fun setup() = initTestDatabase()

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные функции
    // ─────────────────────────────────────────────────────────────────────────

    /** Регистрирует пользователя и возвращает JWT. */
    private suspend fun registerAndGetToken(
        builder: ApplicationTestBuilder,
        login: String = TEST_LOGIN,
        password: String = TEST_PASSWORD
    ): String {
        builder.jsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$login","password":"$password"}""")
        }
        return makeTestToken(login)
    }

    /** Создаёт дисциплину и возвращает её id. */
    private suspend fun createDiscipline(
        client: HttpClient,
        token: String,
        name: String = "Математика",
        color: String = "purple",
        emoji: String = "∑"
    ): Int {
        val response = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name":"$name","color":"$color","emoji":"$emoji"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.content.toInt()
    }

    /** Создаёт заметку и возвращает тело ответа как JsonObject. */
    private suspend fun createNote(
        client: HttpClient,
        token: String,
        disciplineId: Int,
        topic: String = "Интегралы",
        title: String = "Методы интегрирования",
        content: String = "Метод подстановки...",
        fileType: String = "text"
    ) = client.post("/notes") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Bearer $token")
        setBody("""
            {
              "disciplineId": $disciplineId,
              "topic": "$topic",
              "title": "$title",
              "content": "$content",
              "fileType": "$fileType"
            }
        """.trimIndent())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /notes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GET notes - returns 200 and empty list for new user`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().get("/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertTrue(json.isEmpty())
    }

    @Test
    fun `GET notes - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().get("/notes")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET notes - returns notes sorted newest first`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        createNote(client, token, discId, title = "Первая заметка")
        createNote(client, token, discId, title = "Вторая заметка")
        createNote(client, token, discId, title = "Третья заметка")

        val response = client.get("/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val notes = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(3, notes.size)
        // Сервер возвращает в порядке убывания id (ORDER BY id DESC)
        val title0 = notes[0].jsonObject["title"]?.jsonPrimitive?.content
        assertEquals("Третья заметка", title0)
    }

    @Test
    fun `GET notes - user sees only their own notes`() = testApplication {
        application { module() }
        val client = jsonClient()

        val token1 = registerAndGetToken(this, "owner", "pass1")
        val token2 = registerAndGetToken(this, "other", "pass2")

        val discId = createDiscipline(client, token1)
        createNote(client, token1, discId, title = "Секретная заметка")

        val response = client.get("/notes") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        val notes = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertTrue(notes.isEmpty(), "other user should not see owner's notes")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /notes/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GET notes-id - returns 200 and correct note`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        val createResp = createNote(client, token, discId,
            topic = "Пределы", title = "Предел функции", content = "lim x→0 sin(x)/x = 1")
        val noteId = Json.parseToJsonElement(createResp.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.content

        val response = client.get("/notes/$noteId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Предел функции", json["title"]?.jsonPrimitive?.content)
        assertEquals("Пределы",        json["topic"]?.jsonPrimitive?.content)
        assertEquals("lim x→0 sin(x)/x = 1", json["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET notes-id - returns 404 for non-existent id`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().get("/notes/99999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET notes-id - returns 404 when note belongs to another user`() = testApplication {
        application { module() }
        val client = jsonClient()

        val ownerToken = registerAndGetToken(this, "owner", "pass1")
        val otherToken = registerAndGetToken(this, "thief", "pass2")

        val discId = createDiscipline(client, ownerToken)
        val createResp = createNote(client, ownerToken, discId, title = "Чужая заметка")
        val noteId = Json.parseToJsonElement(createResp.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.content

        val response = client.get("/notes/$noteId") {
            header(HttpHeaders.Authorization, "Bearer $otherToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET notes-id - returns 400 for non-numeric id`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().get("/notes/abc") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET notes-id - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().get("/notes/1")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /notes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `POST notes - creates note and returns 201 with all fields`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token, name = "Физика")

        val response = createNote(
            client, token, discId,
            topic    = "Механика",
            title    = "Законы Ньютона",
            content  = "F = ma",
            fileType = "text"
        )

        assertEquals(HttpStatusCode.Created, response.status)

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Законы Ньютона", json["title"]?.jsonPrimitive?.content)
        assertEquals("Механика",       json["topic"]?.jsonPrimitive?.content)
        assertEquals("F = ma",         json["content"]?.jsonPrimitive?.content)
        assertEquals("text",           json["fileType"]?.jsonPrimitive?.content)
        assertEquals("Физика",         json["disciplineName"]?.jsonPrimitive?.content)
        assertEquals(discId.toString(),json["disciplineId"]?.jsonPrimitive?.content)
        assertTrue(json["id"]?.jsonPrimitive?.content?.toInt()!! > 0)
        assertTrue(!json["createdAt"]?.jsonPrimitive?.content.isNullOrBlank())
    }

    @Test
    fun `POST notes - note appears in GET notes list`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        createNote(client, token, discId, title = "Моя заметка")

        val listResp = client.get("/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val notes = Json.parseToJsonElement(listResp.bodyAsText()).jsonArray
        assertEquals(1, notes.size)
        assertEquals("Моя заметка", notes[0].jsonObject["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST notes - topics list updates in GET disciplines after note creation`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token, name = "Математика")

        createNote(client, token, discId, topic = "Интегралы",  title = "Заметка 1")
        createNote(client, token, discId, topic = "Матрицы",    title = "Заметка 2")
        createNote(client, token, discId, topic = "Интегралы",  title = "Заметка 3") // дубль темы

        val disciplinesResp = client.get("/disciplines") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val disciplines = Json.parseToJsonElement(disciplinesResp.bodyAsText()).jsonArray
        val disc = disciplines.first().jsonObject
        val topics = disc["topics"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertEquals(2, topics.size, "Unique topics: Интегралы, Матрицы")
        assertTrue(topics.contains("Интегралы"))
        assertTrue(topics.contains("Матрицы"))
    }

    @Test
    fun `POST notes - notesCount increments in discipline after note creation`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        createNote(client, token, discId, title = "Заметка 1")
        createNote(client, token, discId, title = "Заметка 2")

        val discResp = client.get("/disciplines") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val disc = Json.parseToJsonElement(discResp.bodyAsText())
            .jsonArray.first().jsonObject
        assertEquals("2", disc["notesCount"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST notes - returns 400 when title is blank`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"disciplineId":$discId,"topic":"T","title":"","content":"C","fileType":"text"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST notes - returns 400 when content is blank`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"disciplineId":$discId,"topic":"T","title":"Title","content":"","fileType":"text"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST notes - returns 400 when disciplineId belongs to another user`() = testApplication {
        application { module() }
        val client = jsonClient()

        val ownerToken = registerAndGetToken(this, "owner", "p1")
        val thiefToken = registerAndGetToken(this, "thief", "p2")

        val discId = createDiscipline(client, ownerToken)

        // Вор пытается создать заметку в чужой дисциплине
        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $thiefToken")
            setBody("""{"disciplineId":$discId,"topic":"T","title":"Stolen","content":"C","fileType":"text"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST notes - returns 400 on non-existent disciplineId`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"disciplineId":9999,"topic":"T","title":"T","content":"C","fileType":"text"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST notes - returns 400 on malformed JSON`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)

        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST notes - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().post("/notes") {
            contentType(ContentType.Application.Json)
            setBody("""{"disciplineId":1,"topic":"T","title":"T","content":"C","fileType":"text"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST notes - supports all three fileTypes`() = testApplication {
        application { module() }
        val client = jsonClient()
        val token = registerAndGetToken(this)
        val discId = createDiscipline(client, token)

        listOf("photo", "pdf", "text").forEachIndexed { i, fileType ->
            val response = createNote(client, token, discId,
                title = "Заметка $i", fileType = fileType)
            assertEquals(HttpStatusCode.Created, response.status,
                "Failed for fileType=$fileType")
        }

        val notes = Json.parseToJsonElement(
            client.get("/notes") { header(HttpHeaders.Authorization, "Bearer $token") }
                .bodyAsText()
        ).jsonArray

        assertEquals(3, notes.size)
    }
}
