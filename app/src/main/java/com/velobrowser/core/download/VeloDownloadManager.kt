package com.velobrowser.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.velobrowser.domain.model.DownloadItem
import com.velobrowser.domain.model.DownloadStatus
import com.velobrowser.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VeloDownloadManager @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val notificationHelper: DownloadNotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownload(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String
    ) {
        val fileName = extractFileName(url, contentDisposition, mimeType)
        val item = DownloadItem(url = url, fileName = fileName, mimeType = mimeType)

        scope.launch {
            val id = downloadRepository.insertDownload(item)
            try {
                val systemDm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription("Downloading via Velo Browser")
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        allowScanningByMediaScanner()
                    } else {
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                }

                systemDm.enqueue(request)
                downloadRepository.updateDownloadStatus(id, DownloadStatus.RUNNING)
                notificationHelper.showDownloadStarted(context, fileName)
            } catch (e: Exception) {
                downloadRepository.updateDownloadStatus(id, DownloadStatus.FAILED)
            }
        }
    }

    private fun extractFileName(url: String, contentDisposition: String?, mimeType: String): String {
        if (!contentDisposition.isNullOrBlank()) {
            val regex = Regex("""filename[^;=\n]*=(['"]?)([^'"\n]+)\1""", RegexOption.IGNORE_CASE)
            val match = regex.find(contentDisposition)
            if (match != null) {
                val name = match.groupValues[2].trim()
                if (name.isNotEmpty()) return name
            }
        }
        val fromUrl = url.substringAfterLast("/").substringBefore("?").substringBefore("#")
        if (fromUrl.isNotEmpty() && fromUrl.contains(".")) return fromUrl

        val extension = when {
            mimeType.contains("pdf") -> ".pdf"
            mimeType.contains("zip") -> ".zip"
            mimeType.contains("mp4") -> ".mp4"
            mimeType.contains("mp3") -> ".mp3"
            mimeType.contains("apk") -> ".apk"
            mimeType.contains("image/png") -> ".png"
            mimeType.contains("image/jpeg") -> ".jpg"
            else -> ".bin"
        }
        return "velo_download_${System.currentTimeMillis()}$extension"
    }
}
