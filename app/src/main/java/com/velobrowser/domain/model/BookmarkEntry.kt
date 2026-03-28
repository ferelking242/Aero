package com.velobrowser.domain.model

data class BookmarkEntry(
    val id: Long = 0L,
    val url: String,
    val title: String,
    val faviconUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val profileId: Long = 1L
)
