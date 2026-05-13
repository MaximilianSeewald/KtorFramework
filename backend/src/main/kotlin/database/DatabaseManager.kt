package com.loudless.database

import at.favre.lib.crypto.bcrypt.BCrypt
import com.loudless.homeassistant.HomeAssistantMode
import com.loudless.userGroups.UserGroupNameValidator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseManager {
    private val LOGGER = LoggerFactory.getLogger(DatabaseManager::class.java)
    private const val legacyHomeAssistantUserGroupName = "ha-instance"

    val shoppingListMap: MutableMap<String, ShoppingList> = mutableMapOf()
    val recipeMap: MutableMap<String, Recipe> = mutableMapOf()
    private var dataSource: HikariDataSource? = null

    @Synchronized
    fun init(){
        LOGGER.info("Initializing database")
        close()
        val resolvedConfig = DatabaseRuntimeConfig.resolve()
        LOGGER.info(
            "Resolved H2 database path {} with parent {}",
            resolvedConfig.databasePath.fileName,
            resolvedConfig.databasePath.parent
        )
        val hikariDataSource = hikari(resolvedConfig)
        dataSource = hikariDataSource
        Database.connect(hikariDataSource)
        transaction {
            LOGGER.info("Creating base database schemas if missing")
            SchemaUtils.create(Users)
            SchemaUtils.create(UserGroups)
            if (HomeAssistantMode.enabled) {
                LOGGER.info("Home Assistant mode enabled; ensuring default user group")
                migrateLegacyHomeAssistantUserGroup()
                ensureHomeAssistantUserGroup()
            }
            var dynamicTableCount = 0
            UserGroups.selectAll().map { it[UserGroups.name] }.forEach { groupName ->
                UserGroupNameValidator.requireValid(groupName)
                val shoppingList = ShoppingList(groupName)
                SchemaUtils.create(shoppingList)
                shoppingListMap[groupName] = shoppingList
                val recipe = Recipe("${groupName}_recipe")
                SchemaUtils.create(recipe)
                recipeMap[groupName] = recipe
                dynamicTableCount += 2
            }
            LOGGER.info("Initialized {} dynamic group resource tables", dynamicTableCount)
            migrateTablesIfMissing()
        }
        LOGGER.info("Database initialization completed")
    }

    @Synchronized
    fun close() {
        dataSource?.let {
            LOGGER.info("Closing database connection pool")
            it.close()
        }
        dataSource = null
        shoppingListMap.clear()
        recipeMap.clear()
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

    private fun Transaction.migrateLegacyHomeAssistantUserGroup() {
        val legacyGroupExists = !UserGroups
            .selectAll()
            .where { UserGroups.name eq legacyHomeAssistantUserGroupName }
            .empty()
        if (!legacyGroupExists) {
            return
        }

        val currentGroupExists = !UserGroups
            .selectAll()
            .where { UserGroups.name eq HomeAssistantMode.userGroupName }
            .empty()

        if (currentGroupExists) {
            LOGGER.info("Removing legacy Home Assistant user group metadata")
            UserGroups.deleteWhere { UserGroups.name eq legacyHomeAssistantUserGroupName }
        } else {
            LOGGER.info(
                "Migrating legacy Home Assistant user group {} to {}",
                legacyHomeAssistantUserGroupName,
                HomeAssistantMode.userGroupName
            )
            renameTableIfPresent(legacyHomeAssistantUserGroupName, HomeAssistantMode.userGroupName)
            renameTableIfPresent(
                "${legacyHomeAssistantUserGroupName}_recipe",
                "${HomeAssistantMode.userGroupName}_recipe"
            )
            UserGroups.update({ UserGroups.name eq legacyHomeAssistantUserGroupName }) {
                it[name] = HomeAssistantMode.userGroupName
            }
        }

        Users.update({ Users.group eq legacyHomeAssistantUserGroupName }) {
            it[group] = HomeAssistantMode.userGroupName
        }
    }

    private fun Transaction.renameTableIfPresent(oldName: String, newName: String) {
        if (!tableExists(oldName)) {
            return
        }
        if (tableExists(newName)) {
            LOGGER.info("Skipping legacy table rename because target table {} already exists", newName)
            return
        }
        exec("ALTER TABLE ${quoteIdentifier(oldName)} RENAME TO ${quoteIdentifier(newName)}")
    }

    private fun Transaction.tableExists(tableName: String): Boolean {
        val escapedTableName = tableName.replace("'", "''")
        return exec(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '$escapedTableName'"
        ) { resultSet ->
            resultSet.next() && resultSet.getInt(1) > 0
        } ?: false
    }

    private fun quoteIdentifier(identifier: String): String {
        return "\"${identifier.replace("\"", "\"\"")}\""
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

    private fun hikari(resolvedConfig: ResolvedDatabaseConfig): HikariDataSource {
        val config = HikariConfig()
        LOGGER.info("Configuring H2 database at {}", resolvedConfig.databasePath)
        config.driverClassName = "org.h2.Driver"
        config.jdbcUrl = resolvedConfig.jdbcUrl
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
