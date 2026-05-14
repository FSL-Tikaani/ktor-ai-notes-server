package com.tikaani.routes

import com.tikaani.DisciplineRequest
import com.tikaani.database.createDiscipline
import com.tikaani.database.getDisciplinesByUser
import com.tikaani.database.getUserIdByUsername
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.disciplineRoutes() {

    /** GET /disciplines — список дисциплин текущего пользователя */
    get("/disciplines") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")

        call.respond(getDisciplinesByUser(userId))
    }

    /** POST /disciplines — создать дисциплину */
    post("/disciplines") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

        val request = try {
            call.receive<DisciplineRequest>()
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid request body")
        }

        if (request.name.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Name cannot be blank")
        }

        val created = createDiscipline(userId, request)
            ?: return@post call.respond(HttpStatusCode.InternalServerError, "Failed to create discipline")

        call.respond(HttpStatusCode.Created, created)
    }
}
