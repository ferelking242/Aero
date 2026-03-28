package com.velobrowser.data.repository

import com.velobrowser.data.local.db.dao.DownloadDao
import com.velobrowser.data.local.db.entity.DownloadEntity
import com.velobrowser.domain.model.DownloadItem
import com.velobrowser.domain.model.DownloadStatus
import com.velobrowser.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { it.map { e -> e.toDomain() } }

    override suspend fun insertDownload(item: DownloadItem): Long =
        downloadDao.insertDownload(DownloadEntity.fromDomain(item))

    override suspend fun updateDownloadStatus(id: Long, status: DownloadStatus) =
        downloadDao.updateStatus(id, status.name)

    override suspend fun updateDownloadProgress(id: Long, downloadedBytes: Long, fileSize: Long) =
        downloadDao.updateProgress(id, downloadedBytes, fileSize)

    override suspend fun updateFilePath(id: Long, filePath: String) =
        downloadDao.updateFilePath(id, filePath)

    override suspend fun deleteDownload(id: Long) =
        downloadDao.deleteDownload(id)

    override suspend fun clearCompleted() =
        downloadDao.clearCompleted()
}
