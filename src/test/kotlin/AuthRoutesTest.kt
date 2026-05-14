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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRoutesTest {

    @BeforeTest
    fun setup() = initTestDatabase()

    @Test
    fun `register - returns 200 and token on valid credentials`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "token")
    }

    @Test
    fun `register - returns 409 or 400 when user already exists`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Первая регистрация
        val firstReg = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }
        assertEquals(HttpStatusCode.OK, firstReg.status)

        // Повторная регистрация
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }

        // В зависимости от реализации может быть 409 или 400
        assertTrue(
            response.status == HttpStatusCode.Conflict || response.status == HttpStatusCode.BadRequest,
            "Expected 409 Conflict or 400 BadRequest, got ${response.status}"
        )
    }

    @Test
    fun `register - returns 400 when login is blank`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"","password":"$TEST_PASSWORD"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register - returns 400 when password is blank`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register - returns 400 on malformed JSON body`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""not-json-at-all""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `login - returns 200 and token for registered user`() = testApplication {
        application { module() }
        val client = jsonClient()

        // Сначала регистрируем
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "token")
        assertNotNull(body.substringAfter("\"token\":\"").substringBefore("\"").takeIf { it.isNotBlank() })
    }

    @Test
    fun `login - returns 401 for wrong password`() = testApplication {
        application { module() }
        val client = jsonClient()

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"wrongpassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login - returns 401 for non-existent user`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"nobody","password":"nopass"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login - returns 400 when login is blank`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"","password":"$TEST_PASSWORD"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `login - returns 400 on malformed JSON`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("garbage")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `check-token - returns 200 for valid JWT`() = testApplication {
        application { module() }
        val client = jsonClient()

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$TEST_LOGIN","password":"$TEST_PASSWORD"}""")
        }

        val token = makeTestToken(TEST_LOGIN)

        val response = client.get("/auth/check-token") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `check-token - returns 401 when no token provided`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/auth/check-token")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `check-token - returns 401 for expired token`() = testApplication {
        application { module() }
        val client = jsonClient()

        val expiredToken = makeExpiredToken(TEST_LOGIN)

        val response = client.get("/auth/check-token") {
            header(HttpHeaders.Authorization, "Bearer $expiredToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `check-token - returns 401 for tampered token`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/auth/check-token") {
            header(HttpHeaders.Authorization, "Bearer eyJhbGciOiJIUzI1NiJ9.fake.payload")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `check-token - returns 401 for malformed Authorization header`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/auth/check-token") {
            header(HttpHeaders.Authorization, "NotBearer sometoken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}