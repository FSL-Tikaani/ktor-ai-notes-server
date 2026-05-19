package com.tikaani.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.tikaani.JwtConfig
import com.tikaani.UserCredentials
import com.tikaani.UserDataCredentials
import com.tikaani.database.createUser
import com.tikaani.database.createUserData
import com.tikaani.database.getUserIdByLogin
import com.tikaani.database.isUserValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.Date

fun Route.authRoutes() {
    post("auth/register") {
        handleRegistration(call)
    }
    // ИСПРАВЛЕНИЕ: маршрут перенесён в authenticate("auth-jwt") в Routing.kt
    // Здесь оставлен только как внутренний обработчик
    post("auth/login") {
        // ИСПРАВЛЕНИЕ: убран двойной call.receive() — тело запроса читается
        // только один раз внутри handleLogin()
        handleLogin(call)
    }
}

suspend fun handleRegistrationUserData(call: ApplicationCall) {
    try {
        val userData = call.receive<UserDataCredentials>()

        // ИСПРАВЛЕНИЕ: извлекаем логин из JWT-токена, а не из тела запроса
        val principal = call.principal<JWTPrincipal>()
        val login = principal?.payload?.getClaim("username")?.asString()
            ?: return call.respond(HttpStatusCode.Unauthorized, "Invalid token")

        if (userData.name.isBlank() || userData.surname.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Name and Surname cannot be blank!")
            return
        }

        // ИСПРАВЛЕНИЕ: получаем реальный userId из базы по логину из токена
        val userId = getUserIdByLogin(login)
            ?: return call.respond(HttpStatusCode.NotFound, "User not found")

        // ИСПРАВЛЕНИЕ: теперь действительно сохраняем профиль в БД
        val isCreated = createUserData(userData, userId)

        if (isCreated) {
            // ИСПРАВЛЕНИЕ: раньше ответ на успех отсутствовал — соединение зависало
            call.respond(HttpStatusCode.Created, mapOf("message" to "User profile created"))
        } else {
            call.respond(HttpStatusCode.InternalServerError, "Failed to create user profile")
        }
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
    }
}

fun createToken(user: UserCredentials): String {
    return JWT.create()
        .withAudience(JwtConfig.audience)
        .withIssuer(JwtConfig.issuer)
        .withClaim("username", user.login)
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 3_600_000)) // 30 дней
        .sign(Algorithm.HMAC256(JwtConfig.secretEncryptKey))
}

suspend fun handleRegistration(call: ApplicationCall) {
    try {
        val user = call.receive<UserCredentials>()

        if (user.login.isBlank() || user.password.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Username and password cannot be blank!")
            return
        }

        val isCreated = createUser(user)

        if (isCreated) {
            call.respond(mapOf("token" to createToken(user)))
        } else {
            call.respond(HttpStatusCode.Conflict, "User already exists or database error")
        }
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
    }
}

suspend fun handleLogin(call: ApplicationCall) {
    try {
        val user = call.receive<UserCredentials>()

        if (user.login.isBlank() || user.password.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Username and password cannot be blank!")
            return
        }

        val isUserValid = isUserValid(user)

        if (isUserValid) {
            call.respond(mapOf("token" to createToken(user)))
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid username or password")
        }
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
    }
}
