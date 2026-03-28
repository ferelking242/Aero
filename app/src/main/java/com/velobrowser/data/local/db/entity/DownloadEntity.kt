package com.velobrowser.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.velobrowser.domain.model.DownloadItem
import com.velobrowser.domain.model.DownloadStatus

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val filePath: String = "",
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val statusName: String = DownloadStatus.PENDING.name,
    val startedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): DownloadItem = DownloadItem(
        id = id,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        filePath = filePath,
        fileSize = fileSize,
        downloadedBytes = downloadedBytes,
        status = DownloadStatus.valueOf(statusName),
        startedAt = startedAt
    )

    companion object {
        fun fromDomain(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            url = item.url,
            fileName = item.fileName,
            mimeType = item.mimeType,
            filePath = item.filePath,
            fileSize = item.fileSize,
            downloadedBytes = item.downloadedBytes,
            statusName = item.status.name,
            startedAt = item.startedAt
        )
    }
}
