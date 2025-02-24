package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserGroupRequest(var userGroupName: String, var password: String)