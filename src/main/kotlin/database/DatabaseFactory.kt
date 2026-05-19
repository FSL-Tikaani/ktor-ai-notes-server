package com.tikaani.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    // Позволяет тестам подставить свою in-memory БД до вызова init()
    private var db: Database? = null

    fun init() {
        if (db == null) {
            db = Database.connect(
                url      = "jdbc:h2:file:./db/testdb;AUTO_SERVER=TRUE",
                driver   = "org.h2.Driver",
                user = "root",
                password = ""
            )
        }

        try {
            transaction(db!!) {
                // Порядок важен: DisciplinesTable ссылается на UsersTable,
                // NotesTable — на оба.
                SchemaUtils.create(UsersTable)
                SchemaUtils.create(UsersDataTable)
                SchemaUtils.create(DisciplinesTable)
                SchemaUtils.create(NotesTable)
                SchemaUtils.create(FavoritesTable)
                // Добавляет новые колонки в уже существующие таблицы
                SchemaUtils.createMissingTablesAndColumns(NotesTable)
                SchemaUtils.createMissingTablesAndColumns(FavoritesTable)
            }
        } catch (e: Exception) {
            println("Error while creating tables:")
            e.printStackTrace()
        }
    }

    /**
     * Только для тестов — заменяет файловую БД на переданную in-memory H2.
     * Вызывай из initTestDatabase() ДО запуска приложения.
     */
    fun overrideDatabase(database: Database) {
        db = database
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db) { block() }
}
