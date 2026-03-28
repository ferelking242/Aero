package com.velobrowser.ui.profiles

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.velobrowser.R
import com.velobrowser.databinding.ActivityProfileManagerBinding
import com.velobrowser.utils.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileManagerBinding
    private val viewModel: ProfileManagerViewModel by viewModels()

    private val profileColors = listOf(
        "#2196F3", "#E91E63", "#4CAF50", "#FF9800", "#9C27B0",
        "#00BCD4", "#F44336", "#795548", "#607D8B", "#009688"
    )

    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ProfileAdapter(
            activeProfileIdProvider = { viewModel.activeProfileId.value },
            onProfileClicked = { profile ->
                viewModel.switchToProfile(profile.id)
                finish()
            },
            onProfileDelete = { profile -> showDeleteConfirmation(profile.id) },
            onProfileRename = { profile -> showRenameDialog(profile.id, profile.name) }
        )

        binding.recyclerProfiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerProfiles.adapter = adapter

        binding.fabAddProfile.setOnClickListener { showCreateProfileDialog() }

        collectFlow(viewModel.profiles) { profiles ->
            adapter.submitList(profiles)
        }
    }

    private fun showCreateProfileDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.profile_name_hint)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_profile))
            .setView(input)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val color = profileColors.random()
                    viewModel.createProfile(name, color)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteConfirmation(profileId: Long) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_profile))
            .setMessage(getString(R.string.delete_profile_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteProfile(profileId)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRenameDialog(profileId: Long, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_profile))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameProfile(profileId, newName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
