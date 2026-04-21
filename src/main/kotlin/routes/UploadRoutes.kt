package com.tikaani.routes

import com.tikaani.services.getModifiedPhoto
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

    if (statusOcr.isSuccessfully) {
        getModifiedPhoto(statusOcr.extractedText, statusUpload.fileName)
        call.respondText("OCR complete! Text: ${statusOcr.extractedText}")
    } else {
        call.respond(HttpStatusCode.BadRequest, "OCR failed: ${statusOcr.error}")
    }
}