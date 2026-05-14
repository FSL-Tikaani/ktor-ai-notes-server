package com.tikaani

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.util.generateNonce
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UploadRoutesTest {

    private val testUploadDir = Files.createTempDirectory("uploads_test").toFile()

    private val pngBytes = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val pdfBytes = "%PDF-1.4\n".toByteArray()

    @BeforeTest
    fun setup() {
        initTestDatabase()
        System.setProperty("uploads.dir", testUploadDir.absolutePath)
    }

    @AfterTest
    fun cleanup() {
        testUploadDir.deleteRecursively()
        System.clearProperty("uploads.dir")
    }

    private suspend fun registerAndGetToken(
        builder: io.ktor.server.testing.ApplicationTestBuilder,
        login: String = TEST_LOGIN,
        password: String = TEST_PASSWORD
    ): String {
        builder.jsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$login","password":"$password"}""")
        }
        return makeTestToken(login)
    }

    private fun fileMultipart(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ) = MultiPartFormDataContent(
        parts = formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "filename=$fileName")
            })
        },
        boundary = generateNonce()
    )

    @Test
    fun `POST upload - returns 200 for valid PNG with auth token`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().post("/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(fileMultipart(pngBytes, "test.png", "image/png"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "uploaded")
    }

    @Test
    fun `POST upload - returns 200 for valid PDF with auth token`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().post("/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(fileMultipart(pdfBytes, "document.pdf", "application/pdf"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST upload - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().post("/upload") {
            setBody(fileMultipart(pngBytes, "test.png", "image/png"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST upload - returns 401 for expired token`() = testApplication {
        application { module() }

        val response = jsonClient().post("/upload") {
            header(HttpHeaders.Authorization, "Bearer ${makeExpiredToken(TEST_LOGIN)}")
            setBody(fileMultipart(pngBytes, "test.png", "image/png"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST upload - returns 500 when no file part provided`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().post("/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    parts = formData { append("other_field", "some_value") },
                    boundary = generateNonce()
                )
            )
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `POST upload - rejects non-multipart content type`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().post("/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"file":"not_a_file"}""")
        }

        // Ktor возвращает разные статусы в зависимости от версии: 400, 415, или 500.
        // Проверяем только что это не 200 (успех) и не 401 (авторизация)
        assertTrue(
            response.status.value != HttpStatusCode.OK.value &&
                    response.status.value != HttpStatusCode.Unauthorized.value,
            "Expected server to reject non-multipart body, but got ${response.status}"
        )
    }

    @Test
    fun `POST upload-with-transcript - returns 401 without token`() = testApplication {
        application { module() }

        val response = jsonClient().post("/upload-with-transcript") {
            setBody(fileMultipart(pngBytes, "handwriting.png", "image/png"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST upload-with-transcript - returns 401 for expired token`() = testApplication {
        application { module() }

        val response = jsonClient().post("/upload-with-transcript") {
            header(HttpHeaders.Authorization, "Bearer ${makeExpiredToken(TEST_LOGIN)}")
            setBody(fileMultipart(pngBytes, "handwriting.png", "image/png"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST upload-with-transcript - authenticated request reaches upload step`() = testApplication {
        application { module() }
        val token = registerAndGetToken(this)

        val response = jsonClient().post("/upload-with-transcript") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(fileMultipart(pngBytes, "handwriting.png", "image/png"))
        }

        val status = response.status.value
        assertTrue(
            status != 401 && status != 403,
            "Expected auth to pass, got HTTP $status"
        )
    }

    @Test
    fun `POST upload-with-transcript - returns 401 with wrong Bearer scheme`() = testApplication {
        application { module() }

        val response = jsonClient().post("/upload-with-transcript") {
            header(HttpHeaders.Authorization, "Basic dXNlcjpwYXNz")
            setBody(fileMultipart(pngBytes, "test.png", "image/png"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}