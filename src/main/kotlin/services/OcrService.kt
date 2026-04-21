package com.tikaani.services

import com.tikaani.BlockData
import com.tikaani.GenerateBoxesStatus
import com.tikaani.TextAnnotationResponse
import com.tikaani.Vertex
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
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

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                gson()
            }
        }

        val dotenv = dotenv()

        val IAM_TOKEN = dotenv["IAM_TOKEN"]
        val FOLDER_ID = dotenv["FOLDER_ID"]

        println(IAM_TOKEN)
        println(FOLDER_ID)


        val response = client.post("https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText") {
            headers {
                append("Authorization", "Bearer $IAM_TOKEN")
                append("x-folder-id", FOLDER_ID)
                append("x-data-logging-enabled", "true")
                append("Content-Type", "application/json")
            }
            setBody(
                mapOf(
                    "mimeType" to "JPEG",
                    "languageCodes" to listOf("ru", "en"),
                    "model" to "handwritten",
                    "content" to base64
                )
            )
        }

        println("Response status: ${response.status}")

        if (response.status != HttpStatusCode.OK) {
            ocrStatus.error = "Error response, code: ${response.status.value}"
            val errorBody = response.body<String>()

            println("Error body: $errorBody")
            return ocrStatus
        }

        val responseText = response.body<String>()
        ocrStatus.isSuccessfully = true
        ocrStatus.extractedText = responseText

    } catch (e: Exception) {
        println("Error with OCR Yandex: ${e.message}")
        ocrStatus.error = e.message.toString()
    }

    return ocrStatus
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