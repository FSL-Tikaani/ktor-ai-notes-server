package com.tikaani.services

import com.tikaani.UploadFileStatus
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.server.application.ApplicationCall
import java.io.File
import java.util.UUID

suspend fun uploadFileToServer(call: ApplicationCall): UploadFileStatus {
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
