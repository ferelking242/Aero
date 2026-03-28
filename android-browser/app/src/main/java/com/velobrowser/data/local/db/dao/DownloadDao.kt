package com.velobrowser.data.local.db.dao

import androidx.room.*
import com.velobrowser.data.local.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY startedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Query("UPDATE downloads SET statusName = :statusName WHERE id = :id")
    suspend fun updateStatus(id: Long, statusName: String)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, fileSize = :fileSize WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedBytes: Long, fileSize: Long)

    @Query("UPDATE downloads SET filePath = :filePath WHERE id = :id")
    suspend fun updateFilePath(id: Long, filePath: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM downloads WHERE statusName = 'COMPLETED'")
    suspend fun clearCompleted()
}
