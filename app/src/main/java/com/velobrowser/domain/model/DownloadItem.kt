package com.velobrowser.domain.model

data class DownloadItem(
    val id: Long = 0L,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val filePath: String = "",
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val startedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
