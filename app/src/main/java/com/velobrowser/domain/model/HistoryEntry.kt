package com.velobrowser.domain.model

data class HistoryEntry(
    val id: Long = 0L,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis(),
    val profileId: Long = 1L
)
