package com.loudless.database

import org.jetbrains.exposed.sql.Table

object UserGroups : Table() {
    val name = varchar("name", 255)
    val adminUserId = integer("adminUserId") references Users.id
    val hashedPassword = varchar("hashedPassword", 255)
    override val primaryKey = PrimaryKey(name)
}
