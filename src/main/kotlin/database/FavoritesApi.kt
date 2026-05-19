package com.tikaani.database

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/** Добавить заметку в избранное (только публичные). */
suspend fun addFavorite(userId: Int, noteId: Int) {
    DatabaseFactory.dbQuery {
        val note = NotesTable.selectAll()
            .where { NotesTable.id eq noteId }
            .firstOrNull() ?: return@dbQuery

        if (!note[NotesTable.isPublic]) return@dbQuery

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

/** Убрать заметку из избранного. */
suspend fun removeFavorite(userId: Int, noteId: Int) {
    DatabaseFactory.dbQuery {
        FavoritesTable.deleteWhere {
            (FavoritesTable.userId eq userId) and (FavoritesTable.noteId eq noteId)
        }
    }
}
