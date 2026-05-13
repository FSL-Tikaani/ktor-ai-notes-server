package com.tikaani.database

import org.jetbrains.exposed.sql.Table

object UsersDataTable : Table("user_profiles") {
    val id = integer("id")
    val name = varchar("name", 100)
    val surname = varchar("surname", 100)
    val isEmailConfirmed = bool("is_email_confirmed").default(false)
    val avatar = varchar("avatar", 255).nullable()
    val studyYear = integer("study_year")
    val numberDiscipline = integer("number_discipline").default(0)
    val numberNotes = integer("number_notes").default(0)

    override val primaryKey = PrimaryKey(id)
}