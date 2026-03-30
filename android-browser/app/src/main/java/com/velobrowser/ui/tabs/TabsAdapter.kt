package com.velobrowser.ui.tabs

import android.view.LayoutInflater
import android.view.View
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
            val ctx = binding.root.context
            val title = tab.title.ifBlank { ctx.getString(R.string.new_tab) }
            binding.tvTabTitle.text = title
            binding.tvTabUrl.text = tab.url

            val isActive = tab.id == activeTabId()

            when {
                tab.isIsolated -> {
                    binding.ivIncognito.visibility = View.VISIBLE
                    binding.ivIncognito.setImageResource(R.drawable.ic_isolated_tab)
                    binding.ivIncognito.setColorFilter(
                        ContextCompat.getColor(ctx, R.color.color_isolated_accent)
                    )
                    binding.ivIncognitoCenter.visibility = View.VISIBLE
                    binding.ivIncognitoCenter.setImageResource(R.drawable.ic_isolated_tab)
                    binding.ivIncognitoCenter.setColorFilter(
                        ContextCompat.getColor(ctx, R.color.color_isolated_accent)
                    )
                    binding.tabPreviewArea.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.color_isolated_preview_bg)
                    )
                    val strokeColor = if (isActive) {
                        ContextCompat.getColor(ctx, R.color.color_isolated_accent)
                    } else {
                        ContextCompat.getColor(ctx, android.R.color.transparent)
                    }
                    binding.root.strokeColor = strokeColor
                    binding.root.strokeWidth = if (isActive) 5 else 0
                }
                tab.isIncognito -> {
                    binding.ivIncognito.visibility = View.VISIBLE
                    binding.ivIncognito.setImageResource(R.drawable.ic_incognito)
                    binding.ivIncognito.colorFilter = null
                    binding.ivIncognitoCenter.visibility = View.VISIBLE
                    binding.ivIncognitoCenter.setImageResource(R.drawable.ic_incognito)
                    binding.ivIncognitoCenter.colorFilter = null
                    binding.tabPreviewArea.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.color_incognito_preview_bg)
                    )
                    val strokeColor = if (isActive) {
                        ContextCompat.getColor(ctx, R.color.color_incognito)
                    } else {
                        ContextCompat.getColor(ctx, android.R.color.transparent)
                    }
                    binding.root.strokeColor = strokeColor
                    binding.root.strokeWidth = if (isActive) 5 else 0
                }
                else -> {
                    binding.ivIncognito.visibility = View.GONE
                    binding.ivIncognitoCenter.visibility = View.GONE
                    binding.tabPreviewArea.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.color_tab_preview_bg)
                    )
                    val strokeColor = if (isActive) {
                        ContextCompat.getColor(ctx, com.google.android.material.R.color.design_default_color_primary)
                    } else {
                        ContextCompat.getColor(ctx, android.R.color.transparent)
                    }
                    binding.root.strokeColor = strokeColor
                    binding.root.strokeWidth = if (isActive) 5 else 0
                }
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
