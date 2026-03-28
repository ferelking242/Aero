package com.velobrowser.ui.downloads

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.velobrowser.databinding.ActivityDownloadsBinding
import com.velobrowser.domain.repository.DownloadRepository
import com.velobrowser.utils.collectFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding

    @Inject lateinit var downloadRepository: DownloadRepository

    private val adapter = DownloadsAdapter(
        onDelete = { item ->
            // Handled via repository injection in a real setup
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerDownloads.layoutManager = LinearLayoutManager(this)
        binding.recyclerDownloads.adapter = adapter

        collectFlow(downloadRepository.getAllDownloads()) { items ->
            adapter.submitList(items)
            binding.emptyView.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
