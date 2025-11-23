package com.tikaani


import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.gson.gson
import io.ktor.server.application.*
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.serialization.json.Json
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Exception
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Server is running!")
        }
        post("/upload") {
            val statusUpload = uploadFileToServer(call)

            if (statusUpload.isSuccessfully){
                call.respondText("Photo was upload!")
                println("Uploaded file:${statusUpload.fileName}")
            }else{
                call.respondText("Error with uploading file, error:${statusUpload.error}", status = HttpStatusCode.InternalServerError)
                println("Error with uploading file, error:${statusUpload.error}")
            }
        }

        post("/upload-with-transcript") {
            val statusUpload = uploadFileToServer(call)

            if (statusUpload.isSuccessfully){
                println("Uploaded file:${statusUpload.fileName}")

                // Делаем запрос на OCR
                val statusOcr = getOCRFromYandex(statusUpload.fileName)

                if (statusOcr.isSuccessfully){
                    call.respondText("OCR complete! Extracted Text: ${statusOcr.extractedText}")
                    getModifiedPhoto(statusOcr.extractedText, statusUpload.fileName)
                    println("OCR complete! Extracted Text: ${statusOcr.extractedText}")
                }else{
                    call.respondText("OCR failed! Error: ${statusOcr.error}")
                    println("OCR failed! Error: ${statusOcr.error}")
                }
            }else{
                call.respondText("Error with uploading file, error:${statusUpload.error}", status = HttpStatusCode.InternalServerError)
                println("Error with uploading file, error:${statusUpload.error}")
            }
        }

    }
}

fun getModifiedPhoto(extractedText: String, fileName: String){
    // 1. Парсим JSON ответ от API
    val jsonString = extractedText

    val jsonParser = Json {
        ignoreUnknownKeys = true
    }


    val ocrResponse = jsonParser.decodeFromString<TextAnnotationResponse>(jsonString)

// 2. Получаем блоки данных
    val blocks = ocrResponse.result.textAnnotation.blocks

// 3. Вызываем функцию рисования
    val result = drawBoundingBoxes(fileName, blocks)

// 4. Проверяем результат
    if (result.isSuccessfully) {
        println("Успешно нарисовали bounding boxes!")
    } else {
        println("Ошибка: ${result.error}")
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
    catch (e: Exception){
        status.error = e.message.toString()
    }

    return status
}

private fun drawPolygon(graphics: Graphics2D, vertices: List<Vertex>) {
    val xPoints = vertices.map { it.x.toInt() }.toIntArray()
    val yPoints = vertices.map { it.y.toInt() }.toIntArray()
    graphics.drawPolygon(xPoints, yPoints, vertices.size)
}


suspend fun uploadFileToServer(call: RoutingCall): UploadFileStatus{
    val status = UploadFileStatus()
    try {
        val multipartData = call.receiveMultipart()
        multipartData.forEachPart { partData ->
            when (partData) {
                is PartData.FileItem -> {
                    val fileName = partData.originalFileName ?: UUID.randomUUID().toString()
                    val uploadDir = File("UploadsData")
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs()
                    }

                    val file = File(uploadDir, fileName)
                    partData.provider().copyAndClose(file.writeChannel())

                    status.isSuccessfully = true
                    status.fileName = fileName
                }
                else -> {
                    status.error = "Error type of partData"
                }
            }
            partData.dispose()
        }
    } catch (e: Exception) {
        status.error = e.message.toString()
    }

    return status
}

suspend fun getOCRFromGoogleAPI(fileName: String): OCRStatus {
    val ocrStatus = OCRStatus()
    try {
        val file = File("UploadsData/$fileName")

        val client = ImageAnnotatorClient.create()
        val imageBytes = file.readBytes()
        val image = Image.newBuilder().setContent(ByteString.copyFrom(imageBytes)).build()
        val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()
        val request = AnnotateImageRequest
                .newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .setImageContext(ImageContext.newBuilder().addLanguageHints("ru").build())
                .build()

        val response = client.batchAnnotateImages(listOf(request)).responsesList[0]
        if (response.hasError()) {
            println("OCR error: ${response.error.message}")
            ocrStatus.error = response.error.message.toString()
        } else {
            ocrStatus.isSuccessfully = true
            ocrStatus.extractedText = response.fullTextAnnotation.text
        }
    }

    catch (e: Exception){
        println("Error with OCR Google: ${e.message.toString()}")
        ocrStatus.error = e.message.toString()
    }

    return ocrStatus
}

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
