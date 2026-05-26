package com.tikaani.database

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

// Добавляет публичную заметку в избранное юзера.
// Игнорируем тихо если заметка приватная или уже в избранном -
// клиенту не важно, ему достаточно 200
suspend fun addFavorite(userId: Int, noteId: Int) {
    DatabaseFactory.dbQuery {
        val note = NotesTable.selectAll()
            .where { NotesTable.id eq noteId }
            .firstOrNull() ?: return@dbQuery

        // В избранное можно только публичные - чужие приватные ловить нельзя
        if (!note[NotesTable.isPublic]) return@dbQuery

        // Защита от дубля - таблица бы и так не дала вставить из-за PK,
        // но лучше проверить заранее чем ловить исключение
        val alreadyExists = FavoritesTable.selectAll()
            .where { (FavoritesTable.userId eq userId) and (FavoritesTable.noteId eq noteId) }
            .count() > 0

        if (!alreadyExists) {
            FavoritesTable.insert {
                it[FavoritesTable.userId] = userId
                it[FavoritesTable.noteId] = noteId
            }
        }
    }
}

// Убирает заметку из избранного. Если её там не было - просто ничего не произойдет
suspend fun removeFavorite(userId: Int, noteId: Int) {
    DatabaseFactory.dbQuery {
        FavoritesTable.deleteWhere {
            (FavoritesTable.userId eq userId) and (FavoritesTable.noteId eq noteId)
        }
    }
}
