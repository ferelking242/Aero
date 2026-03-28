package com.velobrowser.ui.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.velobrowser.R
import com.velobrowser.databinding.ItemDownloadBinding
import com.velobrowser.domain.model.DownloadItem
import com.velobrowser.domain.model.DownloadStatus
import java.text.SimpleDateFormat
import java.util.*

class DownloadsAdapter(
    private val onDelete: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DownloadItem) {
            binding.tvFileName.text = item.fileName
            binding.tvUrl.text = item.url
            binding.tvDate.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(item.startedAt))

            val statusText = when (item.status) {
                DownloadStatus.PENDING -> binding.root.context.getString(R.string.download_pending)
                DownloadStatus.RUNNING -> binding.root.context.getString(R.string.download_running)
                DownloadStatus.COMPLETED -> binding.root.context.getString(R.string.download_complete)
                DownloadStatus.FAILED -> binding.root.context.getString(R.string.download_failed)
                DownloadStatus.PAUSED -> binding.root.context.getString(R.string.download_paused)
                DownloadStatus.CANCELLED -> binding.root.context.getString(R.string.download_cancelled)
            }
            binding.tvStatus.text = statusText

            if (item.status == DownloadStatus.RUNNING && item.fileSize > 0) {
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.progressBar.progress = ((item.downloadedBytes.toFloat() / item.fileSize) * 100).toInt()
            } else {
                binding.progressBar.visibility = android.view.View.GONE
            }

            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(a: DownloadItem, b: DownloadItem) = a.id == b.id
            override fun areContentsTheSame(a: DownloadItem, b: DownloadItem) = a == b
        }
    }
}
