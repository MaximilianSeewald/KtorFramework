package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserGroupRequest(val userGroupName: String, val password: String)