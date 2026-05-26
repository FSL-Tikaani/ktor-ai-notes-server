package com.tikaani.services

import com.tikaani.Env
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Тут все что связано с обращением к YandexGPT.
// Файл называется GroqService по историческим причинам -
// сначала пробовал Groq, потом перешел на YandexGPT

// Один HttpClient на все вызовы - чтобы не пересоздавать его на каждый запрос
private val yandexClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

// API-ключ и FOLDER_ID берем из env. lazy - чтобы тесты могли поднимать сервис без них
private val YANDEX_API_KEY: String by lazy { Env.require("YANDEX_API_KEY") }
private val FOLDER_ID: String by lazy { Env.require("FOLDER_ID") }

private const val YANDEX_GPT_URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

// Модельки под формат запроса/ответа YandexGPT - спрятаны как private, наружу не нужны

@Serializable
private data class YandexMessage(val role: String, val text: String)

@Serializable
private data class YandexCompletionOptions(
    val stream: Boolean = false,
    // temperature пониже - чтобы ответы были более предсказуемые
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

// Общая обертка над вызовом YandexGPT. systemPrompt задает роль ИИ,
// userMessage - то что прислал юзер. На любой косяк возвращаем пустую строку -
// клиент это поймет как "не получилось"
private suspend fun callYandexGPT(systemPrompt: String, userMessage: String): String {
    return try {
        val response = yandexClient.post(YANDEX_GPT_URL) {
            header("Authorization", "Api-Key $YANDEX_API_KEY")
            contentType(ContentType.Application.Json)
            setBody(YandexRequestBody(
                // yandexgpt-lite - дешевая модель, для конспектов хватает
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
        // Берем первый альтернативный ответ - других у нас обычно и нет
        json.decodeFromString<YandexResponse>(rawBody)
            .result.alternatives.firstOrNull()?.message?.text?.trim() ?: ""
    } catch (e: Exception) {
        println("YandexGPT error: ${e.message}")
        ""
    }
}

// Превращает сырой OCR-текст в красивый конспект - чинит опечатки, добавляет заголовки
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

// Краткая выжимка из конспекта - 3-5 буллетов
suspend fun summarizeNote(content: String): String = callYandexGPT(
    systemPrompt = """Ты ассистент студента. Создай краткую сводку конспекта в виде 3–5 ключевых пунктов.
Каждый пункт начинай с "• ". Будь лаконичен. Сохрани язык конспекта.
Верни только список пунктов без заголовков и пояснений.""",
    userMessage = content
)

// Юзер задает вопрос - ИИ отвечает строго по конспекту
// (сам конспект кладем прямо в system-prompt - так модель не отвлекается)
suspend fun askAboutNote(content: String, question: String): String = callYandexGPT(
    systemPrompt = """Ты ассистент студента. Отвечай на вопросы строго по содержанию конспекта ниже.
Если ответ не содержится в конспекте — честно скажи об этом.
Отвечай на том же языке, на котором задан вопрос. Будь конкретен и понятен.

Конспект:
$content""",
    userMessage = question
)
