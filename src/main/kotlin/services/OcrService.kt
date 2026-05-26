package com.tikaani.services

import com.tikaani.BlockData
import com.tikaani.Env
import com.tikaani.GenerateBoxesStatus
import com.tikaani.TextAnnotationResponse
import com.tikaani.Vertex
import com.tikaani.FullTextAnnotation
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.collections.forEach

// Дублирующий контейнер - можно было бы использовать тот что в Models.kt,
// но тут он private к этому сервису и менее громоздкий
data class OCRStatus(
    var isSuccessfully: Boolean = false,
    var extractedText: String = "",
    var error: String = ""
)

// Отправляет файл в Яндекс OCR и забирает у них JSON с распознанным текстом
suspend fun getOCRFromYandex(fileName: String): OCRStatus {
    val ocrStatus = OCRStatus()

    try {
        val file = File("UploadsData/$fileName")
        if (!file.exists()) {
            ocrStatus.error = "File not exists!"
            return ocrStatus
        }

        // Яндекс API принимает файл только base64 в теле json
        val base64 = Base64.getEncoder().encodeToString(file.readBytes())

        // Маппинг расширения в формат который ожидает Яндекс (PNG/PDF/...).
        // По умолчанию JPEG - покрывает большинство фото с телефона
        val ext = fileName.substringAfterLast('.', "jpg").lowercase()
        val yandexMimeType = when (ext) {
            "png"        -> "PNG"
            "pdf"        -> "PDF"
            "tiff", "tif"-> "TIFF"
            "bmp"        -> "BMP"
            "gif"        -> "GIF"
            "webp"       -> "WEBP"
            else         -> "JPEG"
        }

        val client = HttpClient(CIO)

        val apiKey   = Env.require("YANDEX_API_KEY")
        val folderId = Env.require("FOLDER_ID")
        // По умолчанию false - чтобы Яндекс не сохранял наши картинки у себя
        val logData  = Env.getBool("YANDEX_OCR_LOG_DATA", false)

        // model=handwritten - заточена под рукописный текст
        val body = """{"mimeType":"$yandexMimeType","languageCodes":["ru","en"],"model":"handwritten","content":"$base64"}"""

        val response = client.post("https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText") {
            headers {
                append("Authorization", "Api-Key $apiKey")
                append("x-folder-id", folderId)
                append("x-data-logging-enabled", logData.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        println("OCR Response status: ${response.status}")

        val responseText = response.bodyAsText()

        if (response.status != HttpStatusCode.OK) {
            ocrStatus.error = "OCR error ${response.status.value}: $responseText"
            println("OCR Error body: $responseText")
            return ocrStatus
        }

        println("OCR Response body: $responseText")
        ocrStatus.isSuccessfully = true
        // Сюда кладем сырой json - дальше его уже парсит extractTextFromOcrJson
        ocrStatus.extractedText = responseText

    } catch (e: Exception) {
        println("Error with OCR Yandex: ${e.message}")
        ocrStatus.error = e.message.toString()
    }

    return ocrStatus
}

// Достает из json-ответа Яндекса распознанный текст одной строкой.
// fullText есть не всегда - на этот случай смотрим в fullTextAnnotation
fun extractTextFromOcrJson(rawJson: String): String {
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<TextAnnotationResponse>(rawJson)
        val fullText = response.result.textAnnotation.fullText.trim()
        if (fullText.isNotEmpty()) return fullText
        return response.result.textAnnotation.fullTextAnnotation?.text?.trim() ?: ""
    } catch (e: Exception) {
        println("OCR text extraction error: ${e.message}")
        ""
    }
}

// Рисует на картинке красные рамки вокруг блоков и строк распознанного текста.
// В рантайме не используется - оставлено для отладки чтобы посмотреть что вообще нашел OCR
fun drawBoundingBoxes(fileName: String, blocks: List<BlockData>): GenerateBoxesStatus{
    val status = GenerateBoxesStatus()
    try {
        val image: BufferedImage = ImageIO.read(File("UploadsData/$fileName"))
        val graphics: Graphics2D = image.createGraphics()

        graphics.color = Color.RED
        graphics.stroke = BasicStroke(1.0f)

        blocks.forEach { block ->
            // Сначала рамка вокруг всего блока
            drawPolygon(graphics, block.boundingBox.vertices)

            // Потом мелкие рамки на каждую строку внутри блока
            block.lines.forEach { line ->
                drawPolygon(graphics, line.boundingBox.vertices)
            }
        }

        graphics.dispose()
        ImageIO.write(image, "jpg", File("OutputData/$fileName"))
        status.isSuccessfully = true
    }
    catch (e: java.lang.Exception){
        status.error = e.message.toString()
    }

    return status
}

// Удобная обертка - парсит json и сразу рисует рамки. Тоже для отладки
fun getModifiedPhoto(extractedText: String, fileName: String){
    val jsonString = extractedText

    val jsonParser = Json {
        ignoreUnknownKeys = true
    }

    val ocrResponse = jsonParser.decodeFromString<TextAnnotationResponse>(jsonString)

    val blocks = ocrResponse.result.textAnnotation.blocks

    val result = drawBoundingBoxes(fileName, blocks)

    if (result.isSuccessfully) {
        println("Успешно нарисовали bounding boxes!")
    } else {
        println("Ошибка: ${result.error}")
    }
}


// Маленький хелпер - превращает список вершин в полигон на Graphics2D
private fun drawPolygon(graphics: Graphics2D, vertices: List<Vertex>) {
    val xPoints = vertices.map { it.x.toInt() }.toIntArray()
    val yPoints = vertices.map { it.y.toInt() }.toIntArray()
    graphics.drawPolygon(xPoints, yPoints, vertices.size)
}
