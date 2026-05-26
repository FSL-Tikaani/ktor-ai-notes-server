package com.tikaani.routes

import com.tikaani.CreateNoteRequest
import com.tikaani.database.createNote
import com.tikaani.database.getNoteById
import com.tikaani.database.getNotesByUser
import com.tikaani.database.getUserIdByUsername
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

// Ручки для работы с конспектами (CRUD без update/delete пока)
fun Route.noteRoutes() {

    // Все конспекты текущего юзера
    get("/notes") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")

        call.respond(getNotesByUser(userId))
    }

    // Конкретный конспект по id - своя или публичная
    get("/notes/{id}") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")

        val noteId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid note id")

        // getNoteById сам проверит доступ - чужие приватные вернет как null
        val note = getNoteById(userId, noteId)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Note not found")

        call.respond(note)
    }

    // Создание новой заметки
    post("/notes") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

        val request = try {
            call.receive<CreateNoteRequest>()
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid request body: ${e.message}")
        }

        if (request.title.isBlank() || request.content.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Title and content cannot be blank")
        }

        // null от createNote = пытались создать заметку в чужой дисциплине (или её нет)
        val created = createNote(userId, request)
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Discipline not found or access denied")

        call.respond(HttpStatusCode.Created, created)
    }
}
