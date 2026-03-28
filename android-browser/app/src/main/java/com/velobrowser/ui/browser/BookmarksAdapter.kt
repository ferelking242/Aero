package com.velobrowser.ui.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.velobrowser.databinding.ItemBookmarkBinding
import com.velobrowser.domain.model.BookmarkEntry

class BookmarksAdapter(
    private val onClick: (BookmarkEntry) -> Unit
) : ListAdapter<BookmarkEntry, BookmarksAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: BookmarkEntry) {
            binding.tvTitle.text = entry.title
            binding.tvUrl.text = entry.url
            binding.root.setOnClickListener { onClick(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BookmarkEntry>() {
            override fun areItemsTheSame(a: BookmarkEntry, b: BookmarkEntry) = a.id == b.id
            override fun areContentsTheSame(a: BookmarkEntry, b: BookmarkEntry) = a == b
        }
    }
}
