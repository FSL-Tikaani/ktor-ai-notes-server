package com.tikaani

import io.github.cdimascio.dotenv.dotenv

// Тут читаем переменные окружения - сначала смотрим в .env (если он есть),
// потом в системные ENV. Так удобно: локально храним .env, в Docker - через ENV
object Env {
    private val dotenv = dotenv {
        // Если .env не найден - не падаем (в проде его и не должно быть)
        ignoreIfMissing = true
        systemProperties = false
    }

    fun get(key: String): String? = dotenv[key] ?: System.getenv(key)

    // Использовать когда без переменной приложение работать не сможет (JWT_SECRET и тд)
    fun require(key: String): String =
        get(key) ?: error("Missing required env variable: $key")

    fun getOrDefault(key: String, default: String): String = get(key) ?: default

    fun getBool(key: String, default: Boolean): Boolean =
        get(key)?.lowercase()?.let { it == "true" || it == "1" || it == "yes" } ?: default

    fun getLong(key: String, default: Long): Long =
        get(key)?.toLongOrNull() ?: default
}
