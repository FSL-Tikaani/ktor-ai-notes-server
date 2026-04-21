package com.tikaani.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.tikaani.JwtConfig
import com.tikaani.UserCredentials
import com.tikaani.database.createUser
import com.tikaani.database.isUserValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.lang.Exception
import java.util.Date

fun Route.authRoutes() {
    post("/register") {
        handleRegistration(call)
    }
    post("/login") {
        handleLogin(call)
    }
}

suspend fun handleRegistration(call: ApplicationCall) {
    try {
        val user = call.receive<UserCredentials>()

        if (user.username.isBlank() || user.password.isBlank()) {
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
        call.respond(HttpStatusCode.BadRequest, "Error:: ${e.message}")
    }
}

fun createToken(user: UserCredentials): String {
    val token = JWT.create()
        .withAudience(JwtConfig.audience)
        .withIssuer(JwtConfig.issuer)
        .withClaim("username", user.username)
        // Выдаем токен на 1 час
        .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
        .sign(Algorithm.HMAC256(JwtConfig.secretEncryptKey))

    return token
}

suspend fun handleLogin(call: ApplicationCall){
    try {
        val user = call.receive<UserCredentials>()

        if (user.username.isBlank() || user.password.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Username and password cannot be blank!")
            return
        }

        val isUserValid = isUserValid(user)

        if (isUserValid) {
            call.respond(mapOf("token" to createToken(user)))
        }else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid username or password")
        }
    } catch (e: kotlin.Exception){
        call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")

    }
}