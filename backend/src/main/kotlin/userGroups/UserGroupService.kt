package com.loudless.userGroups

import com.loudless.database.UserGroups
import io.ktor.http.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

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
            UserGroups.deleteWhere { UserGroups.name eq userGroupName }
            return@transaction true
        }
    }
}