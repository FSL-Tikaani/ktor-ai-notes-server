import com.tikaani.module
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import io.ktor.util.generateNonce
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

class UploadTest {
    private val testDir = Files.createTempDirectory("uploads_test").toFile()

    @Test
    fun testFileUpload() = testApplication {
        application {
            module()
        }

        // Генерируем тестовый файл фото
        val fileName = "test_image.png"
        val fileBytes = byteArrayOf(-119, 80, 78, 71)

        val response: HttpResponse = client.post("/upload") {
            setBody(
                MultiPartFormDataContent (
                    parts = formData {
                        append("file", fileBytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/png")
                            append(HttpHeaders.ContentDisposition, "fileName=$fileName")
                        })
                    },
                    // Уникальная строка-разделитель
                    boundary = generateNonce()
                )
            )
        }
    }

    @AfterTest
    fun cleanup() {
        testDir.deleteRecursively()
    }
}