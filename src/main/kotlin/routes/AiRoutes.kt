package com.tikaani.routes

import com.tikaani.AiAskRequest
import com.tikaani.AiResponse
import com.tikaani.AiTextRequest
import com.tikaani.services.askAboutNote
import com.tikaani.services.formatNoteWithAi
import com.tikaani.services.summarizeNote
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Ручки для всех ИИ-фишек - форматирование, сжатие, вопросы к конспекту
fun Route.aiRoutes() {

    // Берем сырой текст из OCR и просим ИИ сделать из него нормальный конспект
    // (заголовки, исправить ошибки распознавания и тд)
    post("/ai/format") {
        val request = call.receive<AiTextRequest>()
        if (request.rawText.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "rawText cannot be blank")
            return@post
        }
        val result = formatNoteWithAi(request.rawText)
        call.respond(AiResponse(result))
    }

    // Короткая выжимка из конспекта - буллетами
    post("/ai/summarize") {
        val request = call.receive<AiTextRequest>()
        if (request.rawText.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "rawText cannot be blank")
            return@post
        }
        val result = summarizeNote(request.rawText)
        call.respond(AiResponse(result))
    }

    // Юзер задает вопрос - ИИ отвечает по содержимому конспекта.
    // content приходит с клиента вместе с вопросом - чтобы не дергать БД лишний раз
    post("/ai/ask") {
        val request = call.receive<AiAskRequest>()
        if (request.content.isBlank() || request.question.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "content and question cannot be blank")
            return@post
        }
        val result = askAboutNote(request.content, request.question)
        call.respond(AiResponse(result))
    }
}
