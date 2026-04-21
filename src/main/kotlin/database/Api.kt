package com.tikaani.database

import com.tikaani.UserCredentials
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll


suspend fun isUserValid(userCredentials: UserCredentials): Boolean {
    // Возвращает true, если пользователь существует в базе данных
    return DatabaseFactory.dbQuery {
        UsersTable.selectAll().where {
            (UsersTable.username eq userCredentials.username) and (UsersTable.password eq userCredentials.password)
        }.count() > 0
    }
}

suspend fun createUser(user: UserCredentials): Boolean {
    // Создает нового пользователя в базе данных
    return DatabaseFactory.dbQuery {
        val insertStatement = UsersTable.insert {
            it[username] = user.username
            it[password] = user.password
        }
        // Возвращает true, если количество вставленных строк больше 0
        insertStatement.insertedCount > 0
    }
}