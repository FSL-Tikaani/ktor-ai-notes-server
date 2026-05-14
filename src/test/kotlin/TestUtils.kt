package com.tikaani

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.tikaani.database.DatabaseFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.tikaani.database.DisciplinesTable
import com.tikaani.database.NotesTable
import com.tikaani.database.UsersDataTable
import com.tikaani.database.UsersTable
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
// In-memory H2 database для тестов
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Инициализирует отдельную in-memory БД для каждого теста.
 * Имя "testdb_<random>" гарантирует изоляцию между тестами,
 * запущенными параллельно (в том числе в одной JVM).
 */
fun initTestDatabase() {
    val dbName = "testdb_${System.nanoTime()}"
    val database = Database.connect(
        url    = "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=MySQL",
        driver = "org.h2.Driver",
        user   = "root",
        password = ""
    )
    // Перезаписываем DatabaseFactory.dbQuery, подключая тестовую БД
    DatabaseFactory.overrideDatabase(database)

    transaction(database) {
        SchemaUtils.create(UsersTable)
        SchemaUtils.create(UsersDataTable)
        SchemaUtils.create(DisciplinesTable)
        SchemaUtils.create(NotesTable)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// JWT helper
// ─────────────────────────────────────────────────────────────────────────────

/** Генерирует валидный JWT-токен для указанного пользователя (для тестов). */
fun makeTestToken(username: String, expiresInMs: Long = 3_600_000L): String =
    JWT.create()
        .withAudience(JwtConfig.audience)
        .withIssuer(JwtConfig.issuer)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
        .sign(Algorithm.HMAC256(JwtConfig.secretEncryptKey))

/** Генерирует просроченный токен — для теста 401. */
fun makeExpiredToken(username: String): String =
    JWT.create()
        .withAudience(JwtConfig.audience)
        .withIssuer(JwtConfig.issuer)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() - 1000))
        .sign(Algorithm.HMAC256(JwtConfig.secretEncryptKey))

// ─────────────────────────────────────────────────────────────────────────────
// HTTP-клиент с JSON-поддержкой
// ─────────────────────────────────────────────────────────────────────────────

/** Создаёт клиента с Content-Negotiation (JSON) — переиспользуется во всех тестах. */
fun ApplicationTestBuilder.jsonClient(): HttpClient =
    createClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Константы
// ─────────────────────────────────────────────────────────────────────────────

const val TEST_LOGIN    = "testuser"
const val TEST_PASSWORD = "testpass123"

const val TEST_LOGIN_2  = "anotheruser"
