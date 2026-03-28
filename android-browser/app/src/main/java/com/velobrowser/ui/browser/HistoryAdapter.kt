package com.velobrowser.ui.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.velobrowser.databinding.ItemHistoryBinding
import com.velobrowser.domain.model.HistoryEntry
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onClick: (HistoryEntry) -> Unit
) : ListAdapter<HistoryEntry, HistoryAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: HistoryEntry) {
            binding.tvTitle.text = entry.title
            binding.tvUrl.text = entry.url
            binding.tvDate.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(entry.visitedAt))
            binding.root.setOnClickListener { onClick(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryEntry>() {
            override fun areItemsTheSame(a: HistoryEntry, b: HistoryEntry) = a.id == b.id
            override fun areContentsTheSame(a: HistoryEntry, b: HistoryEntry) = a == b
        }
    }
}
