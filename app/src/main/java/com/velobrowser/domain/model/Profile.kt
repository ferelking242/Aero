package com.velobrowser.domain.model

data class Profile(
    val id: Long = 0L,
    val name: String,
    val colorHex: String = "#2196F3",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val initial: String
        get() = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}
