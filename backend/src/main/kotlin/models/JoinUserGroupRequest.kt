package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class JoinUserGroupRequest(val userGroupName: String, val password: String)
