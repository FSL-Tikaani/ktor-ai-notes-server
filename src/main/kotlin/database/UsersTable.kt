package com.tikaani.database

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = integer(name = "id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 50)

    override val primaryKey = PrimaryKey(id)
}
