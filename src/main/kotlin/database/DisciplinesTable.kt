package com.tikaani.database

import org.jetbrains.exposed.sql.Table

// Дисциплины юзера (математика, физика и тд)
object DisciplinesTable : Table("disciplines") {
    val id        = integer("id").autoIncrement()
    val userId    = integer("user_id") references UsersTable.id
    val name      = varchar("name", 100)
    // Цвет и эмодзи - для отображения карточки. По умолчанию ставим нейтральные
    val color     = varchar("color", 30).default("amber")
    val emoji     = varchar("emoji", 10).default("⊛")

    override val primaryKey = PrimaryKey(id)
}
