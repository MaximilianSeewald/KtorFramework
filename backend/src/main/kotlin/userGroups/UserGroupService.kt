package com.loudless.userGroups

import com.loudless.database.DatabaseManager
import com.loudless.database.UserGroups
import com.loudless.models.User
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

object UserGroupService {
    private val LOGGER = LoggerFactory.getLogger(UserGroupService::class.java)

    fun userGroupExists(userGroupName: String): Boolean {
        LOGGER.info("Checking whether user group {} exists", userGroupName)
        return transaction {
            val exists = UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroupName }
                .empty().not()
            LOGGER.info("User group {} exists: {}", userGroupName, exists)
            exists
        }
    }

    fun addUserGroup(userGroupName: String, password: String, adminUserId: Int) {
        LOGGER.info("Adding user group {} with admin user {}", userGroupName, adminUserId)
        transaction {
            UserGroups.insert {
                it[name] = userGroupName
                it[hashedPassword] = password
                it[UserGroups.adminUserId] = adminUserId
            }
        }
        LOGGER.info("Added user group {}", userGroupName)
    }

    fun checkOwnershipAndDeleteUserGroup(userGroupName: String, userId: Int): Boolean {
        LOGGER.info("Checking ownership before deleting user group {}", userGroupName)
        return transaction {
            val adminId = UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroupName }
                .map { it[UserGroups.adminUserId] }
                .firstOrNull()
            
            if (adminId != userId) {
                LOGGER.warn("User {} is not the owner of group {}", userId, userGroupName)
                return@transaction false
            }
            UserGroups.deleteWhere { name eq userGroupName }
            LOGGER.info("Deleted user group {}", userGroupName)
            true
        }
    }

    fun checkIsAdmin(user: User): Boolean {
        LOGGER.info("Checking admin status for user {}", user.id)
        return transaction {
            val userGroup = user.userGroup ?: run {
                LOGGER.info("User {} is not admin because they have no group", user.id)
                return@transaction false
            }
            val adminId = UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroup }
                .map { it[UserGroups.adminUserId] }
                .firstOrNull()
            
            val isAdmin = adminId == user.id
            LOGGER.info("User {} admin status for group {}: {}", user.id, userGroup, isAdmin)
            isAdmin
        }
    }

    fun updatePassword(userGroupName: String, newPassword: String) {
        LOGGER.info("Updating password for user group {}", userGroupName)
        transaction {
            UserGroups.update(where = { UserGroups.name eq userGroupName }) {
                it[hashedPassword] = DatabaseManager.hashPassword(newPassword)
            }
        }
        LOGGER.info("Updated password for user group {}", userGroupName)
    }

    fun checkPassword(userGroupName: String, password: String): Boolean {
        LOGGER.info("Verifying password for user group {}", userGroupName)
        return transaction {
            val storedPassword = UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroupName }
                .map { it[UserGroups.hashedPassword] }
                .firstOrNull() ?: run {
                    LOGGER.warn("Password verification failed because user group {} was not found", userGroupName)
                    return@transaction false
                }

            val verified = DatabaseManager.verifyPassword(password, storedPassword)
            LOGGER.info("Password verification for user group {} completed: {}", userGroupName, verified)
            verified
        }
    }
}
