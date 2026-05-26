package com.tikaani

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.tikaani.database.DatabaseFactory
import io.ktor.server.plugins.doublereceive.DoubleReceive

// Точка входа - дальше Ktor сам читает application.conf и поднимает Netty
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Сначала поднимаем БД, иначе первые запросы упадут
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json()
    }

    // DoubleReceive нужен чтобы тело запроса можно было читать несколько раз
    // (используется в загрузке файлов когда сначала валидируем, потом сохраняем)
    install(DoubleReceive)

    configureSecurity()
    configureRouting()
}

// Все настройки JWT в одном месте. lazy чтобы не падать на старте если env пустой
object JwtConfig {
    val secretEncryptKey: String by lazy { Env.require("JWT_SECRET") }
    val issuer: String by lazy { Env.getOrDefault("JWT_ISSUER", "ktor-server") }
    val audience: String by lazy { Env.getOrDefault("JWT_AUDIENCE", "android-app") }
    val myRealm: String by lazy { Env.getOrDefault("JWT_REALM", "api-authentication") }
    // По умолчанию токен живет 30 дней - чтобы юзер не релогинился каждый день
    val ttlSeconds: Long by lazy { Env.getLong("JWT_TTL_SECONDS", 30L * 24 * 3600) }
}

fun Application.configureSecurity(){

    install(Authentication){
        jwt("auth-jwt"){
            realm = JwtConfig.myRealm
            // Проверяем подпись токена и что issuer/audience совпадают с нашими
            verifier (
                JWT.require(Algorithm.HMAC256(JwtConfig.secretEncryptKey))
                    .withAudience(JwtConfig.audience)
                    .withIssuer(JwtConfig.issuer)
                    .build()
            )
            validate { credential ->
                // Внутри токена должен быть username иначе считаем токен невалидным
                val username = credential.payload.getClaim("username").asString()
                if (!username.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
