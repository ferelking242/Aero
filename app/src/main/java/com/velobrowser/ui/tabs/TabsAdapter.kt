package com.velobrowser.ui.tabs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.velobrowser.R
import com.velobrowser.databinding.ItemTabBinding
import com.velobrowser.domain.model.BrowserTab

class TabsAdapter(
    private val activeTabId: () -> String?,
    private val onTabClicked: (BrowserTab) -> Unit,
    private val onTabClosed: (BrowserTab) -> Unit
) : ListAdapter<BrowserTab, TabsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(tab: BrowserTab) {
            binding.tvTabTitle.text = tab.title.ifBlank { binding.root.context.getString(R.string.new_tab) }
            binding.tvTabUrl.text = tab.url.ifBlank { binding.root.context.getString(R.string.about_blank) }

            val isActive = tab.id == activeTabId()
            binding.root.strokeColor = if (isActive) {
                ContextCompat.getColor(binding.root.context, com.google.android.material.R.color.design_default_color_primary)
            } else {
                ContextCompat.getColor(binding.root.context, android.R.color.transparent)
            }
            binding.root.strokeWidth = if (isActive) 4 else 0

            if (tab.isIncognito) {
                binding.ivIncognito.visibility = android.view.View.VISIBLE
            } else {
                binding.ivIncognito.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onTabClicked(tab) }
            binding.btnCloseTab.setOnClickListener { onTabClosed(tab) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BrowserTab>() {
            override fun areItemsTheSame(a: BrowserTab, b: BrowserTab) = a.id == b.id
            override fun areContentsTheSame(a: BrowserTab, b: BrowserTab) = a == b
        }
    }
}
