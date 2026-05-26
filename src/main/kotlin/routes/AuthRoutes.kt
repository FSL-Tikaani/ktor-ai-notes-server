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

// Открытые ручки авторизации - регистрация и логин.
// Заполнение профиля (handleRegistrationUserData) лежит ниже, но вешается уже под JWT в Routing.kt
fun Route.authRoutes() {
    post("auth/register") {
        handleRegistration(call)
    }
    post("auth/login") {
        handleLogin(call)
    }
}

// Сохраняет профиль (имя, фамилия, курс) - вызывается после регистрации.
// userId берем из токена а не из тела - чтобы юзер не мог записать профиль кому-то другому
suspend fun handleRegistrationUserData(call: ApplicationCall) {
    try {
        val userData = call.receive<UserDataCredentials>()

        val principal = call.principal<JWTPrincipal>()
        val login = principal?.payload?.getClaim("username")?.asString()
            ?: return call.respond(HttpStatusCode.Unauthorized, "Invalid token")

        if (userData.name.isBlank() || userData.surname.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Name and Surname cannot be blank!")
            return
        }

        // По username из токена находим реальный id - и под него уже пишем профиль
        val userId = getUserIdByLogin(login)
            ?: return call.respond(HttpStatusCode.NotFound, "User not found")

        val isCreated = createUserData(userData, userId)

        if (isCreated) {
            call.respond(HttpStatusCode.Created, mapOf("message" to "User profile created"))
        } else {
            call.respond(HttpStatusCode.InternalServerError, "Failed to create user profile")
        }
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
    }
}

// Собирает JWT-токен с username внутри. Срок жизни берем из JwtConfig
fun createToken(user: UserCredentials): String {
    return JWT.create()
        .withAudience(JwtConfig.audience)
        .withIssuer(JwtConfig.issuer)
        .withClaim("username", user.login)
        .withExpiresAt(Date(System.currentTimeMillis() + JwtConfig.ttlSeconds * 1000))
        .sign(Algorithm.HMAC256(JwtConfig.secretEncryptKey))
}

// Регистрация - создаем юзера и сразу возвращаем токен,
// чтобы клиенту не нужно было еще раз стучаться на /login
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
            // Чаще всего это сработает на unique-индексе username
            call.respond(HttpStatusCode.Conflict, "User already exists or database error")
        }
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
    }
}

// Логин - проверяем пару логин/пароль и выдаем токен
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
