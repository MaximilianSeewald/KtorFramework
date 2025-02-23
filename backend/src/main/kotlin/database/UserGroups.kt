package com.loudless.database

import org.jetbrains.exposed.sql.Table

object UserGroups : Table() {
    val name = varchar("name", 255)
    val adminName = varchar("admin", 255).default("")
    override val primaryKey = PrimaryKey(name)
}
