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

object UserGroupService {

    fun userGroupExists(userGroupName: String): Boolean {
        return transaction {
            UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroupName }
                .map { it[UserGroups.name] }
                .isNotEmpty()
        }
    }

    fun addUserGroup(userGroupName: String, password: String, adminUserId: Int) {
        transaction {
            UserGroups.insert {
                it[name] = userGroupName
                it[hashedPassword] = password
                it[UserGroups.adminUserId] = adminUserId
            }
        }
    }

    fun checkOwnershipAndDeleteUserGroup(userGroupName: String, userId: Int): Boolean {
        return transaction {
            if (UserGroups
                    .selectAll()
                    .where { UserGroups.name eq userGroupName }
                    .map { it[UserGroups.adminUserId] }
                    .firstOrNull() != userId) {
                return@transaction false
            }
            UserGroups.deleteWhere { name eq userGroupName }
            return@transaction true
        }
    }

    fun checkIsAdmin(user: User): Boolean {
        return transaction {
            val userGroup = user.userGroup?: return@transaction false
            UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroup}
                .map { it[UserGroups.adminUserId] }
                .firstOrNull() == user.id
        }
    }

    fun updatePassword(userGroupName: String, newPassword: String) {
        transaction {
            UserGroups.update(where = { UserGroups.name eq userGroupName }) {
                it[hashedPassword] = DatabaseManager.hashPassword(newPassword)
            }
        }
    }

    fun checkPassword(userGroupName: String, password: String): Boolean {
        return transaction {
            val storedPassword = UserGroups
                .selectAll()
                .where { UserGroups.name eq userGroupName }
                .map { it[UserGroups.hashedPassword] }
                .firstOrNull() ?: return@transaction false

            return@transaction DatabaseManager.verifyPassword(password, storedPassword)
        }
    }
}