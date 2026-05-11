package com.loudless.database

import at.favre.lib.crypto.bcrypt.BCrypt
import com.loudless.homeassistant.HomeAssistantMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File

object DatabaseManager {
    private val LOGGER = LoggerFactory.getLogger(DatabaseManager::class.java)

    val shoppingListMap: MutableMap<String, ShoppingList> = mutableMapOf()
    val recipeMap: MutableMap<String, Recipe> = mutableMapOf()

    fun init(){
        LOGGER.info("Initializing database")
        Database.connect(hikari())
        transaction {
            LOGGER.info("Creating base database schemas if missing")
            SchemaUtils.create(Users)
            SchemaUtils.create(UserGroups)
            if (HomeAssistantMode.enabled) {
                LOGGER.info("Home Assistant mode enabled; ensuring default user group")
                ensureHomeAssistantUserGroup()
            }
            var dynamicTableCount = 0
            UserGroups.selectAll().map { it[UserGroups.name] }.forEach {
                val shoppingList = ShoppingList(it)
                SchemaUtils.create(shoppingList)
                shoppingListMap[it] = shoppingList
                val recipe = Recipe(it + "_recipe")
                SchemaUtils.create(recipe)
                recipeMap[it] = recipe
                dynamicTableCount += 2
            }
            LOGGER.info("Initialized {} dynamic group resource tables", dynamicTableCount)
            migrateTablesIfMissing()
        }
        LOGGER.info("Database initialization completed")
    }

    private fun ensureHomeAssistantUserGroup() {
        LOGGER.info("Ensuring Home Assistant user and group records")
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
            LOGGER.info("Home Assistant user group already exists")
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
        LOGGER.info("Created Home Assistant user group")
    }

    private fun Transaction.migrateTablesIfMissing() {
        LOGGER.info("Checking database tables for missing columns")
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
        val dbPath = System.getProperty("ktor.database.path") ?: when {
            File("/data").exists() -> "/data/db"
            System.getProperty("os.name").startsWith("Windows") -> "./data/db"
            else -> "./data/db"
        }
        LOGGER.info("Configuring H2 database at {}", dbPath)
        config.driverClassName = "org.h2.Driver"
        config.jdbcUrl = "jdbc:h2:file:$dbPath"
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }

    fun hashPassword(plainPassword: String): String {
        LOGGER.debug("Hashing password")
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
    }

    fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword)
        LOGGER.debug("Password verification completed: {}", result.verified)
        return result.verified
    }

}
