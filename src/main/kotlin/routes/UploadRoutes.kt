package com.tikaani.routes

import com.tikaani.UploadWithTranscriptResponse
import com.tikaani.services.extractTextFromOcrJson
import com.tikaani.services.getOCRFromYandex
import com.tikaani.services.uploadFileToServer
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

// Загрузка файлов - просто сохранить + версия с распознаванием текста
fun Route.uploadRoutes() {
    post("/upload") {
        handleUpload(call)
    }

    // Универсальная ручка для клиента: грузим фото/pdf и сразу получаем распознанный текст
    post("/upload-with-transcript") {
        handleUploadWithTranscript(call)
    }
}

// Простая загрузка - сохраняем файл и говорим что все ок
suspend fun handleUpload(call: ApplicationCall) {
    val status = uploadFileToServer(call)
    if (status.isSuccessfully) {
        call.respondText("Photo was uploaded!")
    } else {
        call.respond(HttpStatusCode.InternalServerError, "Error: ${status.error}")
    }
}

// Грузим файл и сразу прогоняем через Яндекс OCR.
// Если OCR упал - всё равно вернем 200 с пустым текстом, потому что файл-то уже лежит
// и клиент сможет дать юзеру ввести текст руками
suspend fun handleUploadWithTranscript(call: ApplicationCall) {
    val statusUpload = uploadFileToServer(call)

    if (!statusUpload.isSuccessfully) {
        call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${statusUpload.error}")
        return
    }

    val statusOcr = getOCRFromYandex(statusUpload.fileName)
    val ocrText = if (statusOcr.isSuccessfully) extractTextFromOcrJson(statusOcr.extractedText) else ""

    call.respond(UploadWithTranscriptResponse(
        fileName = statusUpload.fileName,
        ocrText  = ocrText,
    ))
}
