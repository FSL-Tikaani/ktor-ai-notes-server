package com.tikaani.database

import com.tikaani.CommunityNoteResponse
import com.tikaani.CreateNoteRequest
import com.tikaani.NoteResponse
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Создать заметку и вернуть её полное представление. */
suspend fun createNote(userId: Int, request: CreateNoteRequest): NoteResponse? {
    return DatabaseFactory.dbQuery {
        // Проверяем, что дисциплина принадлежит этому пользователю
        val discipline = DisciplinesTable.selectAll()
            .where { DisciplinesTable.id eq request.disciplineId }
            .firstOrNull() ?: return@dbQuery null

        if (discipline[DisciplinesTable.userId] != userId) return@dbQuery null

        val now = LocalDateTime.now().format(FORMATTER)

        val inserted = NotesTable.insert {
            it[NotesTable.userId]       = userId
            it[NotesTable.disciplineId] = request.disciplineId
            it[topic]                   = request.topic
            it[title]                   = request.title
            it[content]                 = request.content
            it[fileType]                = request.fileType
            it[filePath]                = request.filePath
            it[createdAt]               = now
            it[isPublic]                = request.isPublic
        }

        NoteResponse(
            id             = inserted[NotesTable.id],
            disciplineId   = request.disciplineId,
            disciplineName = discipline[DisciplinesTable.name],
            disciplineColor = discipline[DisciplinesTable.color],
            topic          = request.topic,
            title          = request.title,
            content        = request.content,
            fileType       = request.fileType,
            filePath       = request.filePath,
            createdAt      = now,
            isPublic       = request.isPublic,
        )
    }
}

/** Список всех заметок пользователя (без content, для списка). */
suspend fun getNotesByUser(userId: Int): List<NoteResponse> {
    return DatabaseFactory.dbQuery {
        NotesTable.selectAll()
            .where { NotesTable.userId eq userId }
            .orderBy(NotesTable.id to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { row ->
                val discRow = DisciplinesTable.selectAll()
                    .where { DisciplinesTable.id eq row[NotesTable.disciplineId] }
                    .firstOrNull()
                val discName  = discRow?.get(DisciplinesTable.name)  ?: ""
                val discColor = discRow?.get(DisciplinesTable.color) ?: "amber"

                NoteResponse(
                    id              = row[NotesTable.id],
                    disciplineId    = row[NotesTable.disciplineId],
                    disciplineName  = discName,
                    disciplineColor = discColor,
                    topic           = row[NotesTable.topic],
                    title           = row[NotesTable.title],
                    content         = row[NotesTable.content],
                    fileType        = row[NotesTable.fileType],
                    filePath        = row[NotesTable.filePath],
                    createdAt       = row[NotesTable.createdAt],
                    isPublic        = row[NotesTable.isPublic],
                )
            }
    }
}

/** Получить одну заметку по id (своя или публичная). */
suspend fun getNoteById(userId: Int, noteId: Int): NoteResponse? {
    return DatabaseFactory.dbQuery {
        val row = NotesTable.selectAll()
            .where { NotesTable.id eq noteId }
            .firstOrNull() ?: return@dbQuery null

        // Разрешаем доступ только к своим или публичным конспектам
        if (row[NotesTable.userId] != userId && !row[NotesTable.isPublic]) return@dbQuery null

        val discRow2  = DisciplinesTable.selectAll()
            .where { DisciplinesTable.id eq row[NotesTable.disciplineId] }
            .firstOrNull()
        val discName  = discRow2?.get(DisciplinesTable.name)  ?: ""
        val discColor = discRow2?.get(DisciplinesTable.color) ?: "amber"

        NoteResponse(
            id              = row[NotesTable.id],
            disciplineId    = row[NotesTable.disciplineId],
            disciplineName  = discName,
            disciplineColor = discColor,
            topic           = row[NotesTable.topic],
            title           = row[NotesTable.title],
            content         = row[NotesTable.content],
            fileType        = row[NotesTable.fileType],
            filePath        = row[NotesTable.filePath],
            createdAt       = row[NotesTable.createdAt],
            isPublic        = row[NotesTable.isPublic],
        )
    }
}

/** Список всех публичных заметок, видимых в разделе «Сообщество». */
suspend fun getPublicNotes(currentUserId: Int, searchQuery: String): List<CommunityNoteResponse> {
    return DatabaseFactory.dbQuery {
        NotesTable.selectAll()
            .where { NotesTable.isPublic eq true }
            .mapNotNull { row ->
                val noteId   = row[NotesTable.id]
                val discRow  = DisciplinesTable.selectAll()
                    .where { DisciplinesTable.id eq row[NotesTable.disciplineId] }
                    .firstOrNull()
                val authorRow = UsersTable.selectAll()
                    .where { UsersTable.id eq row[NotesTable.userId] }
                    .firstOrNull()

                val title    = row[NotesTable.title]
                val topic    = row[NotesTable.topic]
                val discName = discRow?.get(DisciplinesTable.name) ?: ""

                if (searchQuery.isNotEmpty() &&
                    !title.contains(searchQuery, ignoreCase = true) &&
                    !topic.contains(searchQuery, ignoreCase = true) &&
                    !discName.contains(searchQuery, ignoreCase = true)) return@mapNotNull null

                val isFav = FavoritesTable.selectAll()
                    .where { (FavoritesTable.userId eq currentUserId) and (FavoritesTable.noteId eq noteId) }
                    .count() > 0

                CommunityNoteResponse(
                    id               = noteId,
                    disciplineId     = row[NotesTable.disciplineId],
                    disciplineName   = discName,
                    disciplineEmoji  = discRow?.get(DisciplinesTable.emoji) ?: "⊛",
                    disciplineColor  = discRow?.get(DisciplinesTable.color) ?: "amber",
                    topic            = topic,
                    title            = title,
                    content          = row[NotesTable.content],
                    fileType         = row[NotesTable.fileType],
                    filePath         = row[NotesTable.filePath],
                    authorName       = authorRow?.get(UsersTable.username) ?: "unknown",
                    createdAt        = row[NotesTable.createdAt],
                    isFavorite       = isFav,
                )
            }
    }
}
