package com.tikaani

import com.tikaani.routes.aiRoutes
import com.tikaani.routes.authRoutes
import com.tikaani.routes.communityRoutes
import com.tikaani.routes.disciplineRoutes
import com.tikaani.routes.handleRegistrationUserData
import com.tikaani.routes.noteRoutes
import com.tikaani.routes.uploadRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        // Без авторизации
        get("/") {
            call.respondText("Server is running!")
        }
        authRoutes()

        // Раздача загруженных файлов (фото/pdf) без авторизации
        get("/files/{fileName}") {
            val fileName = call.parameters["fileName"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing file name")
            val file = File("UploadsData", fileName)
            if (!file.exists()) return@get call.respond(HttpStatusCode.NotFound, "File not found")
            call.respondFile(file)
        }

        // Только с авторизацией
        authenticate("auth-jwt") {
            get("/auth/check-token") {
                call.respond(HttpStatusCode.OK)
            }

            post("auth/registerUserData") {
                handleRegistrationUserData(call)
            }

            uploadRoutes()
            disciplineRoutes()
            noteRoutes()
            communityRoutes()
            aiRoutes()
        }
    }
}
