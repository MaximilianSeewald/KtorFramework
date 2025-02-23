package com.loudless.database

import org.jetbrains.exposed.sql.Table

object UserGroups : Table() {
    val name = varchar("name", 255)
    override val primaryKey = PrimaryKey(name)
}
