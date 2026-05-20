package com.tikaani.services

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val groqClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private val GROQ_API_KEY: String by lazy {
    dotenv { ignoreIfMissing = true }["GROQ_API_KEY"]
}

private const val GROQ_MODEL = "llama-3.3-70b-versatile"
private const val GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions"

// ─── Внутренние модели Groq API ───────────────────────────────────────────────

@Serializable
private data class GroqMessage(val role: String, val content: String)

@Serializable
private data class GroqRequestBody(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7,
)

@Serializable
private data class GroqChoice(val message: GroqMessage)

@Serializable
private data class GroqResponse(val choices: List<GroqChoice>)

// ─── Базовая функция вызова ───────────────────────────────────────────────────

private suspend fun callGroq(systemPrompt: String, userMessage: String): String {
    return try {
        val response = groqClient.post(GROQ_URL) {
            header("Authorization", "Bearer $GROQ_API_KEY")
            contentType(ContentType.Application.Json)
            setBody(GroqRequestBody(
                model    = GROQ_MODEL,
                messages = listOf(
                    GroqMessage("system", systemPrompt),
                    GroqMessage("user",   userMessage),
                )
            ))
        }
        response.body<GroqResponse>().choices.firstOrNull()?.message?.content?.trim() ?: ""
    } catch (e: Exception) {
        println("Groq error: ${e.message}")
        ""
    }
}

// ─── Публичные функции ────────────────────────────────────────────────────────

/**
 * Принимает сырой OCR-текст и возвращает красивый структурированный конспект.
 */
suspend fun formatNoteWithAi(rawText: String): String = callGroq(
    systemPrompt = """Ты ассистент студента. Тебе дан сырой текст после OCR-распознавания рукописи или PDF.
Твоя задача — преобразовать его в чистый, структурированный конспект:
- Исправь ошибки и артефакты OCR
- Добавь заголовки и подзаголовки где уместно
- Сохрани все формулы, термины и числа
- Сохрани язык оригинала
Верни только готовый конспект без пояснений и предисловий.""",
    userMessage = rawText
)

/**
 * Генерирует краткую сводку конспекта (3–5 ключевых пунктов).
 */
suspend fun summarizeNote(content: String): String = callGroq(
    systemPrompt = """Ты ассистент студента. Создай краткую сводку конспекта в виде 3–5 ключевых пунктов.
Каждый пункт начинай с "• ". Будь лаконичен. Сохрани язык конспекта.
Верни только список пунктов без заголовков и пояснений.""",
    userMessage = content
)

/**
 * Отвечает на вопрос студента строго по содержанию конспекта.
 */
suspend fun askAboutNote(content: String, question: String): String = callGroq(
    systemPrompt = """Ты ассистент студента. Отвечай на вопросы строго по содержанию конспекта ниже.
Если ответ не содержится в конспекте — честно скажи об этом.
Отвечай на том же языке, на котором задан вопрос. Будь конкретен и понятен.

Конспект:
$content""",
    userMessage = question
)
