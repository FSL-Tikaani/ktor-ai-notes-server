package com.tikaani.database

import com.tikaani.Env
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

// Подключение к БД и создание таблиц при старте.
// Используем Exposed - ORM от JetBrains
object DatabaseFactory {

    // null до init() - тесты могут подменить на in-memory через overrideDatabase()
    private var db: Database? = null

    fun init() {
        // Если тесты уже подсунули свою БД - не перетираем
        if (db == null) {
            db = Database.connect(
                url      = Env.getOrDefault("DB_URL", "jdbc:h2:file:./db/testdb;AUTO_SERVER=TRUE"),
                driver   = Env.getOrDefault("DB_DRIVER", "org.h2.Driver"),
                user     = Env.getOrDefault("DB_USER", "root"),
                password = Env.getOrDefault("DB_PASSWORD", "")
            )
        }

        try {
            transaction(db!!) {
                // Порядок важен из-за foreign key:
                // disciplines ссылается на users, notes - на оба
                SchemaUtils.create(UsersTable)
                SchemaUtils.create(UsersDataTable)
                SchemaUtils.create(DisciplinesTable)
                SchemaUtils.create(NotesTable)
                SchemaUtils.create(FavoritesTable)
                // На случай если в коде добавили новые колонки - тихонько добавит их в существующие таблицы
                SchemaUtils.createMissingTablesAndColumns(NotesTable)
                SchemaUtils.createMissingTablesAndColumns(FavoritesTable)
            }
        } catch (e: Exception) {
            println("Error while creating tables:")
            e.printStackTrace()
        }
    }

    // Дырочка для тестов - дернуть до старта приложения и подсунуть свой H2 in-memory
    fun overrideDatabase(database: Database) {
        db = database
    }

    // Все запросы к БД оборачиваем в это - чтобы они шли на IO-диспатчере и не блокировали event loop
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db) { block() }
}
