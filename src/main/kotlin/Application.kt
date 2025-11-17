package com.tikaani

import io.ktor.server.application.*


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Установка ключей для OCR
    val keyPath = Application::class.java
        .classLoader
        .getResource("google-vision-key.json")!!.path

    System.setProperty(
        "GOOGLE_APPLICATION_CREDENTIALS",
        keyPath
    )
    configureRouting()
}
