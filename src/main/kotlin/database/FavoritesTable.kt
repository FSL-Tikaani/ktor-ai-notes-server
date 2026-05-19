package com.tikaani.database

import org.jetbrains.exposed.sql.Table

object FavoritesTable : Table("favorites") {
    val userId = integer("user_id") references UsersTable.id
    val noteId = integer("note_id") references NotesTable.id

    override val primaryKey = PrimaryKey(userId, noteId)
}
