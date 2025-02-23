package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Int, val name: String, val userGroup: String)
