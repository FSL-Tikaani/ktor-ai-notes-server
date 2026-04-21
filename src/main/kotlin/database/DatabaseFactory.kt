package com.tikaani.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(){
        val database = Database.connect(
            url = "jdbc:h2:file:./db/testdb",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )

        try {
            transaction(database) {
                SchemaUtils.create(UsersTable)
            }
        }catch (e: Exception){
            println("Error while creating tables:")
            e.printStackTrace()
        }
    }

    // Функция для обёртки запросов
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}