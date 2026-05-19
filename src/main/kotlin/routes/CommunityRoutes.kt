package com.tikaani.routes

import com.tikaani.database.addFavorite
import com.tikaani.database.getPublicNotes
import com.tikaani.database.getUserIdByUsername
import com.tikaani.database.removeFavorite
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.communityRoutes() {

    /** GET /community/notes?q= — публичные конспекты (с поиском) */
    get("/community/notes") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")

        val query = call.request.queryParameters["q"] ?: ""
        call.respond(getPublicNotes(userId, query))
    }

    /** POST /community/favorites/{noteId} — добавить в избранное */
    post("/community/favorites/{noteId}") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

        val noteId = call.parameters["noteId"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid note id")

        addFavorite(userId, noteId)
        call.respond(HttpStatusCode.OK)
    }

    /** DELETE /community/favorites/{noteId} — убрать из избранного */
    delete("/community/favorites/{noteId}") {
        val username = call.principal<JWTPrincipal>()
            ?.payload?.getClaim("username")?.asString()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

        val userId = getUserIdByUsername(username)
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, "User not found")

        val noteId = call.parameters["noteId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid note id")

        removeFavorite(userId, noteId)
        call.respond(HttpStatusCode.OK)
    }
}
