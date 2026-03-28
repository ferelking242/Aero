package com.velobrowser.data.repository

import com.velobrowser.data.local.db.dao.HistoryDao
import com.velobrowser.data.local.db.entity.HistoryEntity
import com.velobrowser.domain.model.HistoryEntry
import com.velobrowser.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun getHistoryForProfile(profileId: Long): Flow<List<HistoryEntry>> =
        historyDao.getHistoryForProfile(profileId).map { it.map { e -> e.toDomain() } }

    override suspend fun addEntry(entry: HistoryEntry) =
        historyDao.insertEntry(HistoryEntity.fromDomain(entry))

    override suspend fun deleteEntry(id: Long) =
        historyDao.deleteEntry(id)

    override suspend fun clearHistoryForProfile(profileId: Long) =
        historyDao.clearHistoryForProfile(profileId)

    override suspend fun clearAllHistory() =
        historyDao.clearAllHistory()

    override fun searchHistory(query: String, profileId: Long): Flow<List<HistoryEntry>> =
        historyDao.searchHistory(query, profileId).map { it.map { e -> e.toDomain() } }
}
