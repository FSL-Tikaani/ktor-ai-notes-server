package com.tikaani.database

import com.tikaani.UserCredentials
import com.tikaani.UserDataCredentials
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll


// Проверяет логин/пароль при входе. Тупой матчинг по двум полям -
// пароль пока хранится как есть, потом нужно перевести на хеш
suspend fun isUserValid(userCredentials: UserCredentials): Boolean {
    return DatabaseFactory.dbQuery {
        UsersTable.selectAll().where {
            (UsersTable.username eq userCredentials.login) and (UsersTable.password eq userCredentials.password)
        }.count() > 0
    }
}

// Достает id юзера по логину - нужен почти везде после авторизации,
// потому что в JWT хранится только username
suspend fun getUserIdByLogin(login: String): Int? {
    return DatabaseFactory.dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.username eq login }
            .map { it[UsersTable.id] }
            .firstOrNull()
    }
}

// Создает запись о юзере при регистрации
suspend fun createUser(user: UserCredentials): Boolean {
    val isUserCreated = DatabaseFactory.dbQuery {
        val insertStatement = UsersTable.insert {
            it[username] = user.login
            it[password] = user.password
        }
        // insertedCount > 0 значит реально вставилось
        insertStatement.insertedCount > 0
    }

    return isUserCreated;
}

// Заполняет профиль юзера после регистрации (имя, фамилия, курс и тд).
// userId приходит уже готовый - его извлекаем из JWT в роуте
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
