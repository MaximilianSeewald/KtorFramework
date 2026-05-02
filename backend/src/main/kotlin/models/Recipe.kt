package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val items: List<RecipeItem>
)

