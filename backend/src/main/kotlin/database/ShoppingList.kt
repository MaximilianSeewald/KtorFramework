package com.loudless.database

import org.jetbrains.exposed.sql.Table

class ShoppingList(name: String): Table(name) {
    val id = uuid("id")
    val name = varchar("name", 255)
    val amount = varchar("amount", 255).default("")
    val retrieved = bool("retrieved").default(false)
}