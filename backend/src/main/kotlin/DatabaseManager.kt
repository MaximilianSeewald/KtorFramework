package com.loudless

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseManager {

    val shoppingListMap: MutableMap<String,ShoppingList> = mutableMapOf()

    object Users: Table() {
        val id = integer("id").autoIncrement()
        val group = varchar("group", 255) references UserGroups.name
        val name = varchar("name",255)
        val hashedPassword = varchar("hashedPassword", 255)
        override val primaryKey = PrimaryKey(id)
    }

    object UserGroups : Table() {
        val name = varchar("name", 255)
        override val primaryKey = PrimaryKey(name)
    }

    class ShoppingList(name: String): Table(name) {
        val id = uuid("id")
        val name = varchar("name", 255)
        val amount = varchar("amount", 255).default("")
    }

    fun init(){
        Database.connect(hikari())
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(UserGroups)
            UserGroups.selectAll().map { it[UserGroups.name] }.forEach {
                val shoppingList = ShoppingList(it)
                SchemaUtils.create(shoppingList)
                shoppingListMap[it] = shoppingList
            }
        }
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = "org.h2.Driver"
        config.jdbcUrl = "jdbc:h2:file:./data/db"
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }
}