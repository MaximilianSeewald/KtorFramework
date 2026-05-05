package com.loudless.database

import at.favre.lib.crypto.bcrypt.BCrypt
import com.loudless.HomeAssistantMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseManager {

    val shoppingListMap: MutableMap<String, ShoppingList> = mutableMapOf()
    val recipeMap: MutableMap<String, Recipe> = mutableMapOf()

    fun init(){
        Database.connect(hikari())
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(UserGroups)
            if (HomeAssistantMode.enabled) {
                ensureHomeAssistantUserGroup()
            }
            UserGroups.selectAll().map { it[UserGroups.name] }.forEach {
                val shoppingList = ShoppingList(it)
                SchemaUtils.create(shoppingList)
                shoppingListMap[it] = shoppingList
                val recipe = Recipe(it + "_recipe")
                SchemaUtils.create(recipe)
                recipeMap[it] = recipe
            }
            migrateTablesIfMissing()
        }
    }

    private fun ensureHomeAssistantUserGroup() {
        val userId = Users
            .selectAll()
            .where { Users.name eq HomeAssistantMode.userName }
            .map { it[Users.id] }
            .firstOrNull() ?: Users.insert {
                it[name] = HomeAssistantMode.userName
                it[hashedPassword] = hashPassword(HomeAssistantMode.password)
                it[group] = HomeAssistantMode.userGroupName
            }[Users.id]

        if (!UserGroups.selectAll().where { UserGroups.name eq HomeAssistantMode.userGroupName }.empty()) {
            Users.update({ Users.id eq userId }) {
                it[group] = HomeAssistantMode.userGroupName
            }
            return
        }

        UserGroups.insert {
            it[name] = HomeAssistantMode.userGroupName
            it[hashedPassword] = hashPassword(HomeAssistantMode.password)
            it[adminUserId] = userId
        }

        Users.update({ Users.id eq userId }) {
            it[group] = HomeAssistantMode.userGroupName
        }
    }

    private fun Transaction.migrateTablesIfMissing() {
        shoppingListMap.forEach { (_, shoppingList) ->
            SchemaUtils.addMissingColumnsStatements(shoppingList).forEach { exec(it) }
        }
        recipeMap.forEach { (_, recipe) ->
            SchemaUtils.addMissingColumnsStatements(recipe).forEach { exec(it) }
        }
        SchemaUtils.addMissingColumnsStatements(Users).forEach { exec(it) }
        SchemaUtils.addMissingColumnsStatements(UserGroups).forEach { exec(it) }
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        val dbPath = when {
            File("/data").exists() -> "/data/db"
            System.getProperty("os.name").startsWith("Windows") -> "./data/db"
            else -> "./data/db"
        }
        config.driverClassName = "org.h2.Driver"
        config.jdbcUrl = "jdbc:h2:file:$dbPath"
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }

    fun hashPassword(plainPassword: String): String {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
    }

    fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword)
        return result.verified
    }

}
