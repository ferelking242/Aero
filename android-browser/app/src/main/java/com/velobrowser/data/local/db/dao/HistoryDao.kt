package com.velobrowser.data.local.db.dao

import androidx.room.*
import com.velobrowser.data.local.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC")
    fun getHistoryForProfile(profileId: Long): Flow<List<HistoryEntity>>

    @Query("""
        SELECT * FROM history 
        WHERE profileId = :profileId 
        AND (url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%')
        ORDER BY visitedAt DESC
        LIMIT 50
    """)
    fun searchHistory(query: String, profileId: Long): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    suspend fun clearHistoryForProfile(profileId: Long)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
