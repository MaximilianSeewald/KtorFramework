package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class EditUserGroupRequest(val userGroupName: String, val newPassword: String)
