package com.velobrowser.domain.repository

import com.velobrowser.domain.model.DownloadItem
import com.velobrowser.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadItem>>
    suspend fun insertDownload(item: DownloadItem): Long
    suspend fun updateDownloadStatus(id: Long, status: DownloadStatus)
    suspend fun updateDownloadProgress(id: Long, downloadedBytes: Long, fileSize: Long)
    suspend fun updateFilePath(id: Long, filePath: String)
    suspend fun deleteDownload(id: Long)
    suspend fun clearCompleted()
}
