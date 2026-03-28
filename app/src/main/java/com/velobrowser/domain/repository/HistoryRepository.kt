package com.velobrowser.domain.repository

import com.velobrowser.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getHistoryForProfile(profileId: Long): Flow<List<HistoryEntry>>
    suspend fun addEntry(entry: HistoryEntry)
    suspend fun deleteEntry(id: Long)
    suspend fun clearHistoryForProfile(profileId: Long)
    suspend fun clearAllHistory()
    fun searchHistory(query: String, profileId: Long): Flow<List<HistoryEntry>>
}
