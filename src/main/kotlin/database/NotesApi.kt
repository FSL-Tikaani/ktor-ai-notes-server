package com.tikaani.database

import com.tikaani.CommunityNoteResponse
import com.tikaani.CreateNoteRequest
import com.tikaani.NoteResponse
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Формат хранения даты в БД - чтобы на клиенте было легко парсить
private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

// Создает заметку. Возвращает null если дисциплина чужая или не нашлась -
// так роут понимает что нужно отдать 400 а не 500
suspend fun createNote(userId: Int, request: CreateNoteRequest): NoteResponse? {
    return DatabaseFactory.dbQuery {
        // Проверяем что дисциплина существует и принадлежит этому юзеру.
        // Без этого можно было бы создавать заметки в чужих дисциплинах подменив id
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

        // Сразу собираем готовый респонс - клиенту так удобнее, не нужен второй GET
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

// Все заметки юзера - используется на главной и в списке "Все конспекты".
// Сортируем по id DESC чтобы свежие были сверху
suspend fun getNotesByUser(userId: Int): List<NoteResponse> {
    return DatabaseFactory.dbQuery {
        NotesTable.selectAll()
            .where { NotesTable.userId eq userId }
            .orderBy(NotesTable.id to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { row ->
                // Подтягиваем имя и цвет дисциплины - чтобы на карточке заметки сразу было видно
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

// Одна заметка по id. Отдаем её если это своя или если публичная -
// иначе null и роут вернет 404
suspend fun getNoteById(userId: Int, noteId: Int): NoteResponse? {
    return DatabaseFactory.dbQuery {
        val row = NotesTable.selectAll()
            .where { NotesTable.id eq noteId }
            .firstOrNull() ?: return@dbQuery null

        // Чужие приватные заметки прячем
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

// Все публичные заметки для раздела "Сообщество".
// searchQuery - подстрока для фильтра по названию/теме/дисциплине.
// currentUserId нужен чтобы проставить флажок "в избранном" для каждой карточки
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

                // Простая фильтрация по подстроке - ищем в названии, теме и имени дисциплины.
                // Регистр игнорируем чтобы поиск был "человеческий"
                if (searchQuery.isNotEmpty() &&
                    !title.contains(searchQuery, ignoreCase = true) &&
                    !topic.contains(searchQuery, ignoreCase = true) &&
                    !discName.contains(searchQuery, ignoreCase = true)) return@mapNotNull null

                // Проверяем у себя ли в избранном - чтобы на клиенте сердечко было заполненое
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
