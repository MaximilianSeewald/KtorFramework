package com.loudless.database

import org.jetbrains.exposed.sql.Table

object Users: Table() {
    val id = integer("id").autoIncrement()
    val group = varchar("group", 255).nullable()
    val name = varchar("name",255)
    val hashedPassword = varchar("hashedPassword", 255)
    override val primaryKey = PrimaryKey(id)
}