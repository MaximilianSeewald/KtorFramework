package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class RecipeItem(val name: String, val value: String)

