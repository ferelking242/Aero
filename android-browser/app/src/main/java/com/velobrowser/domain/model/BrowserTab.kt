package com.velobrowser.domain.model

import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val favicon: android.graphics.Bitmap? = null,
    val isIncognito: Boolean = false,
    val isIsolated: Boolean = false,
    val isolatedSlot: Int = 0,
    val profileId: Long = 1L,
    val groupId: String? = null,
    val groupName: String? = null
)
