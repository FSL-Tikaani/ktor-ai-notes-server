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

// Тут собираем все маршруты приложения в одно дерево
fun Application.configureRouting() {
    routing {
        // Открытые ручки - без токена
        get("/") {
            call.respondText("Server is running!")
        }
        authRoutes()

        // Отдача загруженных файлов (фото и pdf конспектов).
        // Без авторизации - чтобы картинки можно было показывать через обычный Image-loader
        get("/files/{fileName}") {
            val fileName = call.parameters["fileName"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing file name")
            val file = File("UploadsData", fileName)
            if (!file.exists()) return@get call.respond(HttpStatusCode.NotFound, "File not found")
            call.respondFile(file)
        }

        // Все что ниже - только для залогиненых, проверяем JWT
        authenticate("auth-jwt") {
            // Простой пинг для клиента - проверить что токен еще живой
            get("/auth/check-token") {
                call.respond(HttpStatusCode.OK)
            }

            // Заполнение профиля идет уже после регистрации, поэтому за JWT
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
