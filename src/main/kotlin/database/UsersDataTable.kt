package com.tikaani.database

import org.jetbrains.exposed.sql.Table

// Профиль юзера - заполняется после регистрации отдельным запросом.
// id здесь равен id из UsersTable - связь один-к-одному
object UsersDataTable : Table("user_profiles") {
    val id = integer("id")
    val name = varchar("name", 100)
    val surname = varchar("surname", 100)
    val isEmailConfirmed = bool("is_email_confirmed").default(false)
    val avatar = varchar("avatar", 255).nullable()
    val studyYear = integer("study_year")
    // Счетчики дисциплин и заметок - заполняются на клиенте при регистрации,
    // потом по факту берем из реальных таблиц
    val numberDiscipline = integer("number_discipline").default(0)
    val numberNotes = integer("number_notes").default(0)

    override val primaryKey = PrimaryKey(id)
}
