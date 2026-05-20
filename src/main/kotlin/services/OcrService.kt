package com.tikaani.services

import com.tikaani.BlockData
import com.tikaani.GenerateBoxesStatus
import com.tikaani.TextAnnotationResponse
import com.tikaani.Vertex
import com.tikaani.FullTextAnnotation
import io.github.cdimascio.dotenv.dotenv
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

data class OCRStatus(
    var isSuccessfully: Boolean = false,
    var extractedText: String = "",
    var error: String = ""
)

suspend fun getOCRFromYandex(fileName: String): OCRStatus {
    val ocrStatus = OCRStatus()

    try {
        val file = File("UploadsData/$fileName")
        if (!file.exists()) {
            ocrStatus.error = "File not exists!"
            return ocrStatus
        }

        val base64 = Base64.getEncoder().encodeToString(file.readBytes())

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

        val dotenv = dotenv { ignoreIfMissing = true }
        val API_KEY  = dotenv["YANDEX_API_KEY"]
        val FOLDER_ID = dotenv["FOLDER_ID"]

        val body = """{"mimeType":"$yandexMimeType","languageCodes":["ru","en"],"model":"handwritten","content":"$base64"}"""

        val response = client.post("https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText") {
            headers {
                append("Authorization", "Api-Key $API_KEY")
                append("x-folder-id", FOLDER_ID)
                append("x-data-logging-enabled", "true")
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
        ocrStatus.extractedText = responseText

    } catch (e: Exception) {
        println("Error with OCR Yandex: ${e.message}")
        ocrStatus.error = e.message.toString()
    }

    return ocrStatus
}

/** Извлекает полный текст из JSON-ответа Яндекс OCR. */
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

fun drawBoundingBoxes(fileName: String, blocks: List<BlockData>): GenerateBoxesStatus{
    val status = GenerateBoxesStatus()
    try {
        val image: BufferedImage = ImageIO.read(File("UploadsData/$fileName"))
        val graphics: Graphics2D = image.createGraphics()

        // Настройки рисования
        graphics.color = Color.RED
        graphics.stroke = BasicStroke(1.0f)

        blocks.forEach { block ->
            // Рисуем boundingBox для блока
            drawPolygon(graphics, block.boundingBox.vertices)

            // Рисуем boundingBox для каждой линии
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

fun getModifiedPhoto(extractedText: String, fileName: String){
    // Парсим JSON ответ от API
    val jsonString = extractedText

    val jsonParser = Json {
        ignoreUnknownKeys = true
    }

    val ocrResponse = jsonParser.decodeFromString<TextAnnotationResponse>(jsonString)

    // Получаем блоки данных
    val blocks = ocrResponse.result.textAnnotation.blocks

    // Вызываем функцию рисования
    val result = drawBoundingBoxes(fileName, blocks)

    if (result.isSuccessfully) {
        println("Успешно нарисовали bounding boxes!")
    } else {
        println("Ошибка: ${result.error}")
    }
}


private fun drawPolygon(graphics: Graphics2D, vertices: List<Vertex>) {
    val xPoints = vertices.map { it.x.toInt() }.toIntArray()
    val yPoints = vertices.map { it.y.toInt() }.toIntArray()
    graphics.drawPolygon(xPoints, yPoints, vertices.size)
}