package com.tikaani.database

import org.jetbrains.exposed.sql.Table

// Связка "юзер - заметка в избранном". Используется в разделе "Сообщество".
// Составной первичный ключ - один юзер не может добавить одну и ту же заметку дважды
object FavoritesTable : Table("favorites") {
    val userId = integer("user_id") references UsersTable.id
    val noteId = integer("note_id") references NotesTable.id

    override val primaryKey = PrimaryKey(userId, noteId)
}
