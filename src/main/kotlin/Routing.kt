package com.tikaani


import com.tikaani.routes.authRoutes
import com.tikaani.routes.uploadRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    routing {
        // Доступны без авторизации
        get("/") {
            call.respondText("Server is running!")
        }
        authRoutes()
        // Доступны только с авторизацией
        authenticate("auth-jwt") {
            // Вернем 200 если токен, который приходит в заголовке, действителен
            get("/auth/check-token"){
                call.respond(HttpStatusCode.OK)
            }
            uploadRoutes()
        }
    }
}
