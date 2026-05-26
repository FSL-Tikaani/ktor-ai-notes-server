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

// Принимает multipart-запрос с файлом и сохраняет его в UploadsData/
suspend fun uploadFileToServer(call: ApplicationCall): UploadFileStatus {
    val status = UploadFileStatus()
    try {
        val multipartData = call.receiveMultipart()
        multipartData.forEachPart { partData ->
            when (partData) {
                is PartData.FileItem -> {
                    // Достаем расширение из исходного имени или ставим "bin" если ничего нет.
                    // Само имя генерим как UUID - чтобы файлы не затирали друг друга
                    val ext = partData.originalFileName
                        ?.substringAfterLast('.', "bin") ?: "bin"
                    val fileName = "${UUID.randomUUID()}.$ext"
                    val uploadDir = File("UploadsData")
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs()
                    }

                    val file = File(uploadDir, fileName)
                    // Поточная копия чтобы не держать большой файл в памяти
                    partData.provider().copyAndClose(file.writeChannel())

                    status.isSuccessfully = true
                    status.fileName = fileName
                }
                else -> {
                    // Текстовые поля и прочее в этом эндпоинте не используем
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
