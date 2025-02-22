package com.loudless.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseManager {

    val shoppingListMap: MutableMap<String, ShoppingList> = mutableMapOf()

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