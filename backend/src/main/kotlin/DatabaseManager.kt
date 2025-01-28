package com.loudless

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseManager {

    object Users: Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name",255)
        val hashedPassword = varchar("hashedPassword", 255)
        override val primaryKey = PrimaryKey(id)
    }

    fun init(){
        Database.connect(hikari())
        transaction {
            SchemaUtils.create(Users)
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

    fun addUser(name: String, password: String) {
        transaction {
            Users.insert {
                it[Users.name] = name
                it[Users.hashedPassword] = (password)
            }
        }
    }
}