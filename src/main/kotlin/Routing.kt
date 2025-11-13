package com.tikaani

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File
import java.lang.Exception
import java.util.UUID

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Server is running!")
        }
        post("/upload") {
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
                        }
                        else -> {
                            println("Error type of partData!")
                        }
                    }
                    partData.dispose()
                }
                call.respondText("Photo was upload!")
                println("File was upload!")
            } catch (e: Exception) {
                call.respondText("Error with uploading file, error:${e.message}", status = HttpStatusCode.InternalServerError)
                println("Error with uploading file, error:${e.message}")
            }
        }
    }
}
