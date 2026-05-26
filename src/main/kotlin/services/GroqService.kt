package com.tikaani.services

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val yandexClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private val env by lazy { dotenv { ignoreIfMissing = true } }
private val YANDEX_API_KEY: String by lazy { env["YANDEX_API_KEY"] }
private val FOLDER_ID: String by lazy { env["FOLDER_ID"] }

private const val YANDEX_GPT_URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

// ─── Модели YandexGPT API ─────────────────────────────────────────────────────

@Serializable
private data class YandexMessage(val role: String, val text: String)

@Serializable
private data class YandexCompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
)

@Serializable
private data class YandexRequestBody(
    val modelUri: String,
    val completionOptions: YandexCompletionOptions,
    val messages: List<YandexMessage>
)

@Serializable
private data class YandexAlternative(val message: YandexMessage)

@Serializable
private data class YandexResult(val alternatives: List<YandexAlternative>)

@Serializable
private data class YandexResponse(val result: YandexResult)

// ─── Базовая функция вызова ───────────────────────────────────────────────────

private suspend fun callYandexGPT(systemPrompt: String, userMessage: String): String {
    return try {
        val response = yandexClient.post(YANDEX_GPT_URL) {
            header("Authorization", "Api-Key $YANDEX_API_KEY")
            contentType(ContentType.Application.Json)
            setBody(YandexRequestBody(
                modelUri = "gpt://$FOLDER_ID/yandexgpt-lite",
                completionOptions = YandexCompletionOptions(),
                messages = listOf(
                    YandexMessage("system", systemPrompt),
                    YandexMessage("user", userMessage)
                )
            ))
        }
        val rawBody = response.bodyAsText()
        println("YandexGPT status: ${response.status}, body: $rawBody")
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<YandexResponse>(rawBody)
            .result.alternatives.firstOrNull()?.message?.text?.trim() ?: ""
    } catch (e: Exception) {
        println("YandexGPT error: ${e.message}")
        ""
    }
}

// ─── Публичные функции ────────────────────────────────────────────────────────

/**
 * Принимает сырой OCR-текст и возвращает красивый структурированный конспект.
 */
suspend fun formatNoteWithAi(rawText: String): String = callYandexGPT(
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
suspend fun summarizeNote(content: String): String = callYandexGPT(
    systemPrompt = """Ты ассистент студента. Создай краткую сводку конспекта в виде 3–5 ключевых пунктов.
Каждый пункт начинай с "• ". Будь лаконичен. Сохрани язык конспекта.
Верни только список пунктов без заголовков и пояснений.""",
    userMessage = content
)

/**
 * Отвечает на вопрос студента строго по содержанию конспекта.
 */
suspend fun askAboutNote(content: String, question: String): String = callYandexGPT(
    systemPrompt = """Ты ассистент студента. Отвечай на вопросы строго по содержанию конспекта ниже.
Если ответ не содержится в конспекте — честно скажи об этом.
Отвечай на том же языке, на котором задан вопрос. Будь конкретен и понятен.

Конспект:
$content""",
    userMessage = question
)
