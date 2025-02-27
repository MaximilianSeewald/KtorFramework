package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingListItem(val name: String, val amount: String = "", val id: String, val retrieved: Boolean)
