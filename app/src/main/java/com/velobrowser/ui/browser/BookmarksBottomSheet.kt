package com.velobrowser.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater as LI
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velobrowser.databinding.BottomSheetBookmarksBinding
import com.velobrowser.domain.model.BookmarkEntry
import com.velobrowser.utils.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BookmarksBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "BookmarksBottomSheet"
        fun newInstance() = BookmarksBottomSheet()
    }

    private var _binding: BottomSheetBookmarksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by activityViewModels()
    private val adapter = BookmarksAdapter { entry -> onEntryClicked(entry) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetBookmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerBookmarks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBookmarks.adapter = adapter

        collectFlow(viewModel.getBookmarks()) { entries ->
            adapter.submitList(entries)
        }
    }

    private fun onEntryClicked(entry: BookmarkEntry) {
        viewModel.navigateTo(entry.url)
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
