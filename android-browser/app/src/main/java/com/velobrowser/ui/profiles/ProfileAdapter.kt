package com.velobrowser.ui.profiles

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.velobrowser.R
import com.velobrowser.databinding.ItemProfileBinding
import com.velobrowser.domain.model.Profile
import com.velobrowser.utils.parseColor

class ProfileAdapter(
    private val activeProfileIdProvider: () -> Long,
    private val onProfileClicked: (Profile) -> Unit,
    private val onProfileDelete: (Profile) -> Unit,
    private val onProfileRename: (Profile) -> Unit
) : ListAdapter<Profile, ProfileAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: Profile) {
            binding.tvProfileName.text = profile.name
            binding.tvProfileInitial.text = profile.initial
            binding.cardProfileAvatar.setCardBackgroundColor(profile.colorHex.parseColor())

            val isActive = profile.id == activeProfileIdProvider()
            binding.ivActiveIndicator.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
            binding.tvActiveLabel.visibility = if (isActive) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onProfileClicked(profile) }
            binding.btnDelete.setOnClickListener { onProfileDelete(profile) }
            binding.btnRename.setOnClickListener { onProfileRename(profile) }

            // Cannot delete default profile
            binding.btnDelete.isEnabled = profile.id != 1L
            binding.btnDelete.alpha = if (profile.id != 1L) 1f else 0.3f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Profile>() {
            override fun areItemsTheSame(a: Profile, b: Profile) = a.id == b.id
            override fun areContentsTheSame(a: Profile, b: Profile) = a == b
        }
    }
}
