package com.tikaani

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    configureSecurity()
    configureRouting()
}

fun Application.configureSecurity(){
    val secretEncryptKey = "HARDCODE SECRET PHRASE"
    val issuer = "ktor-server"
    val audience = "android-app"
    val myRealm = "api-authentication"

    install(Authentication){
        jwt("auth-jwt"){
            realm = myRealm
            verifier (
                JWT.require(Algorithm.HMAC256(secretEncryptKey))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != ""){
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}