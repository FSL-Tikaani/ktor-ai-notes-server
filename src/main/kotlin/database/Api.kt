package com.tikaani.database

import com.tikaani.UserCredentials
import com.tikaani.UserDataCredentials
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll


suspend fun isUserValid(userCredentials: UserCredentials): Boolean {
    // Возвращает true, если пользователь существует в базе данных
    return DatabaseFactory.dbQuery {
        UsersTable.selectAll().where {
            (UsersTable.username eq userCredentials.login) and (UsersTable.password eq userCredentials.password)
        }.count() > 0
    }
}

suspend fun createUser(user: UserCredentials): Boolean {
    // Создает нового пользователя в базе данных
    val isUserCreated = DatabaseFactory.dbQuery {
        val insertStatement = UsersTable.insert {
            it[username] = user.login
            it[password] = user.password
        }
        // Возвращает true, если количество вставленных строк больше 0
        insertStatement.insertedCount > 0
    }

    return isUserCreated;
}

suspend fun createUserData(userData: UserDataCredentials,  userId: Int): Boolean {
    val isUserDataCreated = DatabaseFactory.dbQuery {
        val insertStatement = UsersDataTable.insert {
            it[id] = userId;
            it[name] = userData.name
            it[surname] = userData.surname
            it[avatar] = userData.avatar
            it[studyYear] = userData.studyYear
            it[numberDiscipline] = userData.numberDiscipline
            it[numberNotes] = userData.numberNotes
        }
        insertStatement.insertedCount > 0
    }
    return isUserDataCreated;
}
