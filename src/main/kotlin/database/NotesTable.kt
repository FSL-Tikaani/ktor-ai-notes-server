package com.tikaani.database

import org.jetbrains.exposed.sql.Table

// Конспект - привязан к юзеру и к одной дисциплине
object NotesTable : Table("notes") {
    val id           = integer("id").autoIncrement()
    val userId       = integer("user_id")       references UsersTable.id
    val disciplineId = integer("discipline_id") references DisciplinesTable.id
    val topic        = varchar("topic", 200)
    val title        = varchar("title", 300)
    // content большой - может быть длинный текст, поэтому text а не varchar
    val content      = text("content")
    val fileType     = varchar("file_type", 20)   // "photo" / "pdf" / "text" - что было исходником
    // Если конспект сделан из фото или pdf - тут имя файла в UploadsData
    val filePath     = text("file_path").nullable()
    val createdAt    = varchar("created_at", 50)
    // Если true - конспект виден в разделе "Сообщество" всем остальным
    val isPublic     = bool("is_public").default(false)

    override val primaryKey = PrimaryKey(id)
}
