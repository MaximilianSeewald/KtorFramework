package com.loudless.database

import org.jetbrains.exposed.sql.Table

class Recipe(name: String): Table(name) {
    val id = uuid("id")
    val name = varchar("name", 255)
    val items = text("items").default("[]")
}

