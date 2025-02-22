package com.loudless.models

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingListItem(var name: String, var amount: String = "", var id: String)
