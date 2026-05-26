package com.tikaani.database

import com.tikaani.DisciplineRequest
import com.tikaani.DisciplineResponse
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

// То же самое что getUserIdByLogin - дубль, оставлен для удобства роутов
// (там везде берут username из JWT-claim, поэтому функция называется так же как и claim)
suspend fun getUserIdByUsername(username: String): Int? {
    return DatabaseFactory.dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.username eq username }
            .firstOrNull()
            ?.get(UsersTable.id)
    }
}

// Тянет все дисциплины юзера и считает заметки и темы по каждой.
// Делается через N+1 запросов - не очень эффективно, но дисциплин обычно мало
suspend fun getDisciplinesByUser(userId: Int): List<DisciplineResponse> {
    return DatabaseFactory.dbQuery {
        DisciplinesTable.selectAll()
            .where { DisciplinesTable.userId eq userId }
            .map { row ->
                val discId = row[DisciplinesTable.id]

                // Считаем сколько заметок в этой дисциплине - покажем на карточке
                val notesCount = NotesTable.selectAll()
                    .where { NotesTable.disciplineId eq discId }
                    .count()
                    .toInt()

                // Собираем уникальные темы - на клиенте показываем как чипы
                val topics = NotesTable.selectAll()
                    .where { NotesTable.disciplineId eq discId }
                    .map { it[NotesTable.topic] }
                    .distinct()

                DisciplineResponse(
                    id         = discId,
                    name       = row[DisciplinesTable.name],
                    color      = row[DisciplinesTable.color],
                    emoji      = row[DisciplinesTable.emoji],
                    notesCount = notesCount,
                    topics     = topics,
                )
            }
    }
}

// Создает дисциплину и сразу возвращает её клиенту (с notesCount=0 и без тем)
suspend fun createDiscipline(userId: Int, request: DisciplineRequest): DisciplineResponse? {
    return DatabaseFactory.dbQuery {
        val inserted = DisciplinesTable.insert {
            it[DisciplinesTable.userId] = userId
            it[name]  = request.name
            it[color] = request.color
            it[emoji] = request.emoji
        }

        val newId = inserted[DisciplinesTable.id]

        DisciplineResponse(
            id         = newId,
            name       = request.name,
            color      = request.color,
            emoji      = request.emoji,
            notesCount = 0,
            topics     = emptyList(),
        )
    }
}
