package com.tikaani.database

import org.jetbrains.exposed.sql.Table

// Базовая таблица юзеров - только то что нужно для логина.
// Имя, фамилия и прочее лежит отдельно в UsersDataTable
object UsersTable : Table("users") {
    val id = integer(name = "id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 50)

    override val primaryKey = PrimaryKey(id)
}
