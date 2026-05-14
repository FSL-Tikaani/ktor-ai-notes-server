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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Интеграционные тесты — полный пользовательский сценарий от регистрации
 * до чтения заметки, включая проверку изоляции данных между пользователями.
 */
class IntegrationTest {

    @BeforeTest
    fun setup() = initTestDatabase()

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 1: Полный флоу создания заметки
    // register → login → create discipline → create note → read note
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `full flow - register, login, create discipline and note, then read back`() = testApplication {
        application { module() }
        val client = jsonClient()

        // 1. Регистрация
        val regResp = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"alice","password":"secure123"}""")
        }
        assertEquals(HttpStatusCode.OK, regResp.status)
        val regJson = Json.parseToJsonElement(regResp.bodyAsText()).jsonObject
        val regToken = regJson["token"]!!.jsonPrimitive.content
        assertTrue(regToken.isNotBlank())

        // 2. Логин тем же пользователем
        val loginResp = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"alice","password":"secure123"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResp.status)
        val loginToken = Json.parseToJsonElement(loginResp.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content
        assertTrue(loginToken.isNotBlank())

        // Используем токен из логина
        val token = loginToken

        // 3. Проверка токена
        val checkResp = client.get("/auth/check-token") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, checkResp.status)

        // 4. Список дисциплин пустой
        val emptyDisc = Json.parseToJsonElement(
            client.get("/disciplines") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()
        ).jsonArray
        assertTrue(emptyDisc.isEmpty())

        // 5. Создаём дисциплину
        val discResp = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name":"Математика","color":"purple","emoji":"∑"}""")
        }
        assertEquals(HttpStatusCode.Created, discResp.status)
        val discId = Json.parseToJsonElement(discResp.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.int

        // 6. Создаём заметку
        val noteResp = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""
                {
                  "disciplineId": $discId,
                  "topic": "Интегралы",
                  "title": "Метод подстановки",
                  "content": "Если t=g(x), то ∫f(g(x))g'(x)dx = ∫f(t)dt",
                  "fileType": "text"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, noteResp.status)
        val noteJson = Json.parseToJsonElement(noteResp.bodyAsText()).jsonObject
        val noteId = noteJson["id"]!!.jsonPrimitive.int
        assertEquals("Метод подстановки", noteJson["title"]?.jsonPrimitive?.content)
        assertEquals("Математика",        noteJson["disciplineName"]?.jsonPrimitive?.content)

        // 7. Читаем заметку по id
        val readResp = client.get("/notes/$noteId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, readResp.status)
        val readJson = Json.parseToJsonElement(readResp.bodyAsText()).jsonObject
        assertEquals("Метод подстановки", readJson["title"]?.jsonPrimitive?.content)
        assertEquals("Интегралы",         readJson["topic"]?.jsonPrimitive?.content)

        // 8. Список заметок — одна штука
        val notesList = Json.parseToJsonElement(
            client.get("/notes") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()
        ).jsonArray
        assertEquals(1, notesList.size)

        // 9. Дисциплина теперь показывает notesCount=1 и тему "Интегралы"
        val discList = Json.parseToJsonElement(
            client.get("/disciplines") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()
        ).jsonArray
        assertEquals(1, discList.size)
        val disc = discList[0].jsonObject
        assertEquals("1", disc["notesCount"]?.jsonPrimitive?.content)
        val topics = disc["topics"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(topics.contains("Интегралы"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 2: Изоляция данных между двумя пользователями
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `data isolation - two users cannot see each other's data`() = testApplication {
        application { module() }
        val client = jsonClient()

        suspend fun registerAndToken(login: String): String {
            client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"login":"$login","password":"pass"}""")
            }
            return makeTestToken(login)
        }

        val tokenA = registerAndToken("alice_iso")
        val tokenB = registerAndToken("bob_iso")

        // Alice создаёт дисциплину и заметку
        val discAResp = client.post("/disciplines") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            setBody("""{"name":"Alice Disc","color":"amber","emoji":"⊛"}""")
        }
        val discAId = Json.parseToJsonElement(discAResp.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.int

        val noteAResp = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            setBody("""{"disciplineId":$discAId,"topic":"T","title":"Alice Note","content":"secret","fileType":"text"}""")
        }
        val noteAId = Json.parseToJsonElement(noteAResp.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.int

        // Bob не видит дисциплины Alice
        val bobDiscs = Json.parseToJsonElement(
            client.get("/disciplines") {
                header(HttpHeaders.Authorization, "Bearer $tokenB")
            }.bodyAsText()
        ).jsonArray
        assertTrue(bobDiscs.isEmpty(), "Bob must not see Alice's disciplines")

        // Bob не видит заметки Alice
        val bobNotes = Json.parseToJsonElement(
            client.get("/notes") {
                header(HttpHeaders.Authorization, "Bearer $tokenB")
            }.bodyAsText()
        ).jsonArray
        assertTrue(bobNotes.isEmpty(), "Bob must not see Alice's notes")

        // Bob не может получить конкретную заметку Alice
        val bobGetNote = client.get("/notes/$noteAId") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.NotFound, bobGetNote.status)

        // Bob не может создать заметку в дисциплине Alice
        val bobStealNote = client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            setBody("""{"disciplineId":$discAId,"topic":"T","title":"Stolen","content":"C","fileType":"text"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bobStealNote.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 3: Множество дисциплин и заметок
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `multiple disciplines and notes - correct aggregation`() = testApplication {
        application { module() }
        val client = jsonClient()

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"carol","password":"pass"}""")
        }
        val token = makeTestToken("carol")

        // Создаём 3 дисциплины
        val discIds = listOf(
            "Физика"   to "blue",
            "Химия"    to "green",
            "История"  to "amber",
        ).map { (name, color) ->
            val r = client.post("/disciplines") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody("""{"name":"$name","color":"$color","emoji":"x"}""")
            }
            Json.parseToJsonElement(r.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
        }

        // 3 заметки в Физике, 1 в Химии, 0 в Истории
        repeat(3) { i ->
            client.post("/notes") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody("""{"disciplineId":${discIds[0]},"topic":"Механика","title":"Физика $i","content":"c","fileType":"text"}""")
            }
        }
        client.post("/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"disciplineId":${discIds[1]},"topic":"Органика","title":"Химия","content":"c","fileType":"text"}""")
        }

        // Проверяем количество заметок в каждой дисциплине
        val discs = Json.parseToJsonElement(
            client.get("/disciplines") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()
        ).jsonArray

        val counts = discs.associate {
            val obj = it.jsonObject
            obj["name"]!!.jsonPrimitive.content to obj["notesCount"]!!.jsonPrimitive.int
        }

        assertEquals(3, counts["Физика"])
        assertEquals(1, counts["Химия"])
        assertEquals(0, counts["История"])

        // Всего 4 заметки
        val totalNotes = Json.parseToJsonElement(
            client.get("/notes") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()
        ).jsonArray
        assertEquals(4, totalNotes.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 4: check-token после регистрации всегда работает
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `check-token is valid immediately after registration`() = testApplication {
        application { module() }
        val client = jsonClient()

        val regResp = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"dave","password":"pass"}""")
        }
        val token = Json.parseToJsonElement(regResp.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        val checkResp = client.get("/auth/check-token") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, checkResp.status)
    }
}
