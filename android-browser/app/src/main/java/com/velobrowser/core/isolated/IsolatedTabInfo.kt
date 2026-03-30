package com.velobrowser.core.isolated

data class IsolatedTabInfo(
    val slot: Int,
    val url: String = "",
    val title: String = "Isolated Tab",
    val isActive: Boolean = false
)
