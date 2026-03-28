package com.velobrowser.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velobrowser.databinding.BottomSheetHistoryBinding
import com.velobrowser.domain.model.HistoryEntry
import com.velobrowser.utils.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoryBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "HistoryBottomSheet"
        fun newInstance() = HistoryBottomSheet()
    }

    private var _binding: BottomSheetHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by activityViewModels()
    private val adapter = HistoryAdapter { entry -> onEntryClicked(entry) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        collectFlow(viewModel.getHistory()) { entries ->
            adapter.submitList(entries)
        }
    }

    private fun onEntryClicked(entry: HistoryEntry) {
        viewModel.navigateTo(entry.url)
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
