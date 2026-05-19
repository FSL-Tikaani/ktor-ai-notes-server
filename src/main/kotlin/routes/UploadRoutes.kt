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

fun Route.uploadRoutes() {
    post("/upload") {
        handleUpload(call)
    }

    post("/upload-with-transcript") {
        handleUploadWithTranscript(call)
    }
}

suspend fun handleUpload(call: ApplicationCall) {
    val status = uploadFileToServer(call)
    if (status.isSuccessfully) {
        call.respondText("Photo was uploaded!")
    } else {
        call.respond(HttpStatusCode.InternalServerError, "Error: ${status.error}")
    }
}

suspend fun handleUploadWithTranscript(call: ApplicationCall) {
    val statusUpload = uploadFileToServer(call)

    if (!statusUpload.isSuccessfully) {
        call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${statusUpload.error}")
        return
    }

    val statusOcr = getOCRFromYandex(statusUpload.fileName)
    val ocrText = if (statusOcr.isSuccessfully) extractTextFromOcrJson(statusOcr.extractedText) else ""

    // Файл сохранён — возвращаем 200 даже если OCR не сработал
    call.respond(UploadWithTranscriptResponse(
        fileName = statusUpload.fileName,
        ocrText  = ocrText,
    ))
}