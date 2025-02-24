package com.loudless.userGroups

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserGroupRequest(var userGroupName: String, var password: String)