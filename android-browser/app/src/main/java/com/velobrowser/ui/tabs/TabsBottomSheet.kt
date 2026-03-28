package com.velobrowser.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velobrowser.databinding.BottomSheetTabsBinding
import com.velobrowser.ui.browser.BrowserViewModel
import com.velobrowser.utils.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TabsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "TabsBottomSheet"
        fun newInstance() = TabsBottomSheet()
    }

    private var _binding: BottomSheetTabsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by activityViewModels()
    private lateinit var adapter: TabsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TabsAdapter(
            activeTabId = { viewModel.activeTabId.value },
            onTabClicked = { tab ->
                viewModel.switchToTab(tab.id)
                dismissAllowingStateLoss()
            },
            onTabClosed = { tab ->
                viewModel.closeTab(tab.id)
                if (viewModel.tabCount() == 0) dismissAllowingStateLoss()
            }
        )

        binding.recyclerTabs.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerTabs.adapter = adapter

        binding.btnNewTab.setOnClickListener {
            viewModel.openNewTab()
            dismissAllowingStateLoss()
        }

        binding.btnNewIncognito.setOnClickListener {
            viewModel.openNewTab(incognito = true)
            dismissAllowingStateLoss()
        }

        collectFlow(viewModel.tabs) { tabs ->
            adapter.submitList(tabs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
