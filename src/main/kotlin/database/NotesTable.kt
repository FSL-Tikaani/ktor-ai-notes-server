package com.tikaani.database

import org.jetbrains.exposed.sql.Table

object NotesTable : Table("notes") {
    val id           = integer("id").autoIncrement()
    val userId       = integer("user_id")       references UsersTable.id
    val disciplineId = integer("discipline_id") references DisciplinesTable.id
    val topic        = varchar("topic", 200)
    val title        = varchar("title", 300)
    val content      = text("content")
    val fileType     = varchar("file_type", 20)   // "photo" | "pdf" | "text"
    val createdAt    = varchar("created_at", 50)

    override val primaryKey = PrimaryKey(id)
}
