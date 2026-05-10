package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class VerifySessionResponse(val valid: Boolean, val user: User)
